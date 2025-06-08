package rearth.oritech.api.networking;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;

// full update is send when GUI is opened, and when the block is loaded for the first time.
// T is expected to be the field type, and R the smaller delta type.
public interface UpdatableField<T, R> {
    
    R getDeltaData();
    PacketCodec<? extends ByteBuf, R> getDeltaCodec();
    
    default boolean useDeltaOnly(SyncType type) {
        return type.equals(SyncType.TICK) || type.equals(SyncType.GUI_TICK) || type.equals(SyncType.SPARSE_TICK);
    }
    
    void handleFullUpdate(T updatedData);
    void handleDeltaUpdate(R updatedData);
    
}
