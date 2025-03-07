package rearth.oritech.client.ui;

import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import rearth.oritech.block.base.entity.ExpandableEnergyStorageBlockEntity;
import rearth.oritech.util.TooltipHelper;

public class EnergyStorageScreen extends UpgradableMachineScreen<UpgradableMachineScreenHandler> {
    
    private LabelComponent inAvgSecond;
    private LabelComponent inLastTick;
    private LabelComponent inSources;
    private LabelComponent inPeak;
    private LabelComponent outAvgSecond;
    private LabelComponent outLastTick;
    private LabelComponent outPeak;
    private boolean showingOutput;
    
    public EnergyStorageScreen(UpgradableMachineScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }
    
    @Override
    public void fillOverlay(FlowLayout overlay) {
        super.fillOverlay(overlay);
        
        var insertionContainer = Containers.verticalFlow(Sizing.content(), Sizing.content());
        insertionContainer.surface(Surface.PANEL_INSET);
        insertionContainer.padding(Insets.of(2, 2, 4, 8));
        insertionContainer.positioning(Positioning.absolute(60, 23));
        
        inAvgSecond = Components.label(Text.literal("X RF/t"));
        inAvgSecond.tooltip(Text.translatable("title.oritech.energy.inAvgSecond.tooltip"));
        inLastTick = Components.label(Text.literal("X RF/t"));
        inLastTick.tooltip(Text.translatable("title.oritech.energy.inLastTick.tooltip"));
        inSources = Components.label(Text.literal("X"));
        inSources.tooltip(Text.translatable("title.oritech.energy.inSources.tooltip"));
        inPeak = Components.label(Text.literal("X RF/t"));
        inPeak.tooltip(Text.translatable("title.oritech.energy.inPeak.tooltip"));
        
        insertionContainer.child(inLastTick.margins(Insets.of(2)));
        insertionContainer.child(inAvgSecond.margins(Insets.of(2)));
        insertionContainer.child(inPeak.margins(Insets.of(2)));
        insertionContainer.child(inSources.margins(Insets.of(2)));
        
        var extractionContainer = Containers.verticalFlow(Sizing.content(), Sizing.content());
        extractionContainer.surface(Surface.PANEL_INSET);
        extractionContainer.padding(Insets.of(2, 2, 4, 8));
        extractionContainer.positioning(Positioning.absolute(60, 23));
        
        outAvgSecond = Components.label(Text.literal("X RF/t"));
        outAvgSecond.tooltip(Text.translatable("title.oritech.energy.outAvgSecond.tooltip"));
        outLastTick = Components.label(Text.literal("X RF/t"));
        outLastTick.tooltip(Text.translatable("title.oritech.energy.outLastTick.tooltip"));
        outPeak = Components.label(Text.literal("X RF/t"));
        outPeak.tooltip(Text.translatable("title.oritech.energy.outPeak.tooltip"));
        
        extractionContainer.child(outLastTick.margins(Insets.of(2)));
        extractionContainer.child(outAvgSecond.margins(Insets.of(2)));
        extractionContainer.child(outPeak.margins(Insets.of(2)));
        
        overlay.child(insertionContainer);
        
        var toggleButton = Components.button(Text.literal("                  ").append(Text.translatable("title.oritech.item_filter.toggle_energy_statistics").withColor(BasicMachineScreen.GRAY_TEXT_COLOR)), buttonComponent -> {
            showingOutput = !showingOutput;
            if (showingOutput) {
                overlay.removeChild(insertionContainer);
                overlay.child(extractionContainer);
            } else {
                overlay.removeChild(extractionContainer);
                overlay.child(insertionContainer);
            }
        });
        toggleButton.horizontalSizing(Sizing.fixed(60));
        toggleButton.renderer(ItemFilterScreen.createToggleRenderer(ignored -> showingOutput));
        toggleButton.textShadow(false);
        overlay.child(toggleButton.positioning(Positioning.absolute(60, 5)));
        
    }
    
    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();
        
        var statistics = ((ExpandableEnergyStorageBlockEntity) this.handler.blockEntity).currentStats;
        if (statistics == null) return;
        
        var updateAll = this.handler.worldAccess.getTime() % 4 == 0;
        
        if (updateAll) {
            inAvgSecond.text(Text.translatable("title.oritech.energy.inAvgSecond", TooltipHelper.getEnergyText((long) statistics.avgInsertSecond())));
            inSources.text(Text.translatable("title.oritech.energy.inSources", statistics.insertionCountLastTick()));
            inPeak.text(Text.translatable("title.oritech.energy.inPeak", TooltipHelper.getEnergyText(statistics.maxInsertSecond())));
            outAvgSecond.text(Text.translatable("title.oritech.energy.outAvgSecond", TooltipHelper.getEnergyText((long) statistics.avgExtractSecond())));
            outPeak.text(Text.translatable("title.oritech.energy.outPeak", TooltipHelper.getEnergyText(statistics.maxExtractSecond())));
        }
        
        inLastTick.text(Text.translatable("title.oritech.energy.inLastTick", TooltipHelper.getEnergyText(statistics.insertedLastTickTotal())));
        outLastTick.text(Text.translatable("title.oritech.energy.outLastTick", TooltipHelper.getEnergyText(statistics.extractedLastTickTotal())));
        
        
    }
}
