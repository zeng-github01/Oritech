package rearth.oritech.block.blocks.addons;

import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

public class CombiAddonBlock extends MachineAddonBlock {
    
    public CombiAddonBlock(Properties settings, AddonSettings addonSettings) {
        super(settings, addonSettings);
    }
    
    @Override
    public AddonSettings getAddonSettings() {
        return super.getAddonSettings();    // todo
    }
    
    @Override
    public @NotNull Class<? extends BlockEntity> getBlockEntityType() {
        return super.getBlockEntityType(); // todo
    }
    
}
