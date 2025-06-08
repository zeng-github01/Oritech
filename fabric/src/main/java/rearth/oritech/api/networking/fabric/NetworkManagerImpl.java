package rearth.oritech.api.networking.fabric;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.apache.logging.log4j.util.TriConsumer;
import rearth.oritech.api.networking.NetworkManager;

public class NetworkManagerImpl {
    
    public static void sendUpdateForBlock(BlockEntity blockEntity, NetworkManager.MessagePayload payload) {
        for (var player : PlayerLookup.tracking(blockEntity)) {
            ServerPlayNetworking.send(player, payload);
        }
    }
    
    public static void sendUpdateForBlock(NetworkManager.MessagePayload payload, ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, payload);
    }
    
    public static <T extends CustomPayload> void registerToClient(CustomPayload.Id<T> id, PacketCodec<RegistryByteBuf, T> packetCodec, TriConsumer<T, World, DynamicRegistryManager> consumer) {
        PayloadTypeRegistry.playS2C().register(id, packetCodec);
        ClientPlayNetworking.registerGlobalReceiver(id, (message, context) -> {
            consumer.accept(message, context.player().clientWorld, context.client().world.getRegistryManager());
        });
    }
    
}
