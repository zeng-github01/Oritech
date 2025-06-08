package rearth.oritech.api.networking.neoforge;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.apache.logging.log4j.util.TriConsumer;
import rearth.oritech.api.networking.NetworkManager;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Consumer;

public class NetworkManagerImpl {
    
    public static final Queue<Consumer<PayloadRegistrar>> PENDING_INITS = new ArrayDeque<>();
    
    public static void sendUpdateForBlock(BlockEntity blockEntity, NetworkManager.MessagePayload payload) {
        PacketDistributor.sendToPlayersTrackingChunk((ServerWorld) blockEntity.getWorld(), new ChunkPos(blockEntity.getPos()), payload);
    }
    
    public static void sendUpdateForBlock(NetworkManager.MessagePayload payload, ServerPlayerEntity player) {
        PacketDistributor.sendToPlayer(player, payload);
    }
    
    public static <T extends CustomPayload> void registerToClient(CustomPayload.Id<T> id, PacketCodec<RegistryByteBuf, T> packetCodec, TriConsumer<T, World, DynamicRegistryManager> consumer) {
        PENDING_INITS.add(payloadRegistrar -> {
            payloadRegistrar.playToClient(id,packetCodec, (payload, context) -> consumer.accept(payload, context.player().getWorld(), context.player().getRegistryManager()));
        });
    }
    
}
