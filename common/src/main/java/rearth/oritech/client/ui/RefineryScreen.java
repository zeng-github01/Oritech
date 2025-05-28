package rearth.oritech.client.ui;

import io.wispforest.owo.ui.container.FlowLayout;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import rearth.oritech.util.ScreenProvider;

public class RefineryScreen extends UpgradableMachineScreen<RefineryScreenHandler> {
    
    private final FluidDisplay outADisplay;
    private final FluidDisplay outBDisplay;
    private final FluidDisplay outCDisplay;
    
    private static final ScreenProvider.BarConfiguration outAConfig = new ScreenProvider.BarConfiguration(92, 6, 21, 74);
    private static final ScreenProvider.BarConfiguration outBConfig = new ScreenProvider.BarConfiguration(92 + 27, 6, 21, 74);
    private static final ScreenProvider.BarConfiguration outCConfig = new ScreenProvider.BarConfiguration(92 + 27 * 2, 6, 21, 74);
    
    public RefineryScreen(RefineryScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        
        outADisplay = initFluidDisplay(handler.outputAContainer, outAConfig);
        outBDisplay = initFluidDisplay(handler.outputBContainer, outBConfig);
        outCDisplay = initFluidDisplay(handler.outputCContainer, outCConfig);
    }
    
    @Override
    public void fillOverlay(FlowLayout overlay) {
        super.fillOverlay(overlay);
        
        addFluidDisplay(overlay, outADisplay);
        updateFluidDisplay(outADisplay);
        
        addFluidDisplay(overlay, outBDisplay);
        updateFluidDisplay(outBDisplay);
        
        addFluidDisplay(overlay, outCDisplay);
        updateFluidDisplay(outCDisplay);
        
    }
    
    @Override
    protected void handledScreenTick() {
        
        updateFluidDisplay(outADisplay);
        updateFluidDisplay(outBDisplay);
        updateFluidDisplay(outCDisplay);
        
        super.handledScreenTick();
    }
}
