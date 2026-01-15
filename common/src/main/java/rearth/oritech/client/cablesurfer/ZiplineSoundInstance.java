package rearth.oritech.client.cablesurfer;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public class ZiplineSoundInstance extends AbstractTickableSoundInstance {
    
    private final Player player;
    
    public ZiplineSoundInstance(Player player) {
        super(SoundEvents.MINECART_RIDING, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.player = player;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.1F; // Start quiet
        this.pitch = 0.5F;  // Start low
    }
    
    @Override
    public void tick() {
        // Stop if zipline stopped or player dead
        if (!ClientZiplineHandler.isActive() || player.isRemoved()) {
            this.stop();
            return;
        }
        
        this.x = player.getX();
        this.y = player.getY() + 2;
        this.z = player.getZ();
        
        float speed = Math.abs(ClientZiplineHandler.getCurrentSpeed());
        
        // Dynamic Volume: Silence when not moving, loud when fast
        this.volume = Mth.clamp(speed * 0.8F, 0.0F, 0.8F);
        
        // Dynamic Pitch: 0.5 (low rumble) to 1.8 (high whine)
        this.pitch = Mth.lerp(speed / 1.5F, 0.5F, 1.8F);
    }
}