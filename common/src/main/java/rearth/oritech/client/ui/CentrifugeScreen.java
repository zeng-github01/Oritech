package rearth.oritech.client.ui;

import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import rearth.oritech.Oritech;
import rearth.oritech.block.entity.processing.CentrifugeBlockEntity;
import rearth.oritech.util.ScreenProvider;

public class CentrifugeScreen extends UpgradableMachineScreen<CentrifugeScreenHandler> {
    private final FluidDisplay inFluidDisplay;
    
    private static final ScreenProvider.BarConfiguration inputConfig = new ScreenProvider.BarConfiguration(28, 6, 21, 74);
    
    public CentrifugeScreen(CentrifugeScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        
        if (((CentrifugeBlockEntity) handler.blockEntity).hasFluidAddon) {
            inFluidDisplay = initFluidDisplay(handler.inputTank, inputConfig);
            
        } else {
            inFluidDisplay = null;
        }
    }
    
    @Override
    public void fillOverlay(FlowLayout overlay) {
        super.fillOverlay(overlay);
        
        if (inFluidDisplay != null) {
            addFluidDisplay(overlay, inFluidDisplay);
            updateFluidDisplay(inFluidDisplay);
        }
        
    }
    
    @Override
    protected void handledScreenTick() {
        
        if (inFluidDisplay != null)
            updateFluidDisplay(inFluidDisplay);
        
        super.handledScreenTick();
    }
}
