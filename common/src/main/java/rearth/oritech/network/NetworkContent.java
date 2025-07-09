package rearth.oritech.network;

import com.mojang.serialization.Codec;
import dev.architectury.fluid.FluidStack;
import dev.architectury.registry.menu.MenuRegistry;
import io.wispforest.owo.network.OwoNetChannel;
import io.wispforest.owo.serialization.CodecUtils;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import rearth.oritech.Oritech;
import rearth.oritech.block.entity.augmenter.AugmentApplicationEntity;
import rearth.oritech.block.entity.augmenter.PlayerAugments;
import rearth.oritech.block.entity.augmenter.PlayerAugmentsClient;
import rearth.oritech.block.entity.augmenter.api.Augment;
import rearth.oritech.init.recipes.OritechRecipe;
import rearth.oritech.init.recipes.OritechRecipeType;

import java.util.Map;

public class NetworkContent {
    
    public static final OwoNetChannel MACHINE_CHANNEL = OwoNetChannel.create(Oritech.id("machine_data"));
    public static final OwoNetChannel UI_CHANNEL = OwoNetChannel.create(Oritech.id("ui_interactions"));
    
    public record AugmentInstallTriggerPacket(BlockPos position, Identifier id, int operationId) {
    }
    
    public record LoadPlayerAugmentsToMachinePacket(BlockPos position) {
    }
    
    public record OpenAugmentScreenPacket(BlockPos position) {
    }
    
    public record AugmentPlayerTogglePacket(Identifier id) {
    }
    
    public record AugmentPlayerStatePacket(Map<Identifier, Augment.AugmentState> data) {
    }
    
    // these two are basically copies of the architectury built-in fluid stack codecs, but using the OPTIONAL_STREAM_CODEC to allow for empty fluid stacks
    public static Codec<FluidStack> FLUID_STACK_CODEC;
    public static PacketCodec<RegistryByteBuf, FluidStack> FLUID_STACK_STREAM_CODEC;
    
    public static void registerChannels() {
        
        Oritech.LOGGER.debug("Registering oritech channels");
        
        MACHINE_CHANNEL.builder().register(OritechRecipeType.ORI_RECIPE_ENDEC, OritechRecipe.class);
        MACHINE_CHANNEL.builder().register(CodecUtils.toEndecWithRegistries(FLUID_STACK_CODEC, FLUID_STACK_STREAM_CODEC), FluidStack.class);
        
        MACHINE_CHANNEL.registerClientbound(AugmentPlayerStatePacket.class, (message, access) -> {
            PlayerAugmentsClient.setPlayerAugment(access, message.data);
        });
        
        UI_CHANNEL.registerServerbound(AugmentInstallTriggerPacket.class, (message, access) -> {
            var player = access.player();
            var entity = access.player().getWorld().getBlockEntity(message.position);
            
            if (entity instanceof AugmentApplicationEntity modifierEntity) {
                var operation = PlayerAugments.AugmentApplicatorOperation.values()[message.operationId];
                switch (operation) {
                    case RESEARCH -> {
                        modifierEntity.researchAugment(message.id, player.isCreative(), player);
                    }
                    case ADD -> {
                        modifierEntity.installAugmentToPlayer(message.id, player);
                    }
                    case REMOVE -> {
                        modifierEntity.removeAugmentFromPlayer(message.id, player);
                    }
                }
            }
        });
        
        UI_CHANNEL.registerServerbound(LoadPlayerAugmentsToMachinePacket.class, (message, access) -> {
            var player = access.player();
            var entity = access.player().getWorld().getBlockEntity(message.position);
            
            if (entity instanceof AugmentApplicationEntity modifierEntity) {
                modifierEntity.loadResearchesFromPlayer(player);
            }
        });
        
        UI_CHANNEL.registerServerbound(OpenAugmentScreenPacket.class, (message, access) -> {
            var player = access.player();
            var entity = access.player().getWorld().getBlockEntity(message.position);
            
            if (entity instanceof AugmentApplicationEntity modifierEntity) {
                modifierEntity.screenInvOverride = true;
                MenuRegistry.openExtendedMenu(player, modifierEntity);
            }
        });
        
        UI_CHANNEL.registerServerbound(AugmentPlayerTogglePacket.class, (message, access) -> {
            var player = access.player();
            AugmentApplicationEntity.toggleAugmentForPlayer(message.id, player);
        });
        
    }
    
}
