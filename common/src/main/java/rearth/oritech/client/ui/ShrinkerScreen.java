package rearth.oritech.client.ui;

import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Positioning;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import rearth.oritech.api.networking.NetworkManager;
import rearth.oritech.block.entity.interaction.ShrinkerBlockEntity;

public class ShrinkerScreen extends UpgradableMachineScreen<UpgradableMachineScreenHandler> {
    
    public ShrinkerScreen(UpgradableMachineScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
    }
    
    @Override
    public void fillOverlay(FlowLayout overlay) {
        super.fillOverlay(overlay);
        
        var shrinkButton = Components.button(Component.translatable("text.oritech.shrink").withStyle(ChatFormatting.DARK_GRAY), event -> {
            onShrinkPressed();
        });
        shrinkButton.renderer(ORITECH_BUTTON);
        shrinkButton.textShadow(false);
        
        overlay.child(shrinkButton.positioning(Positioning.absolute(76, 38)));
        
    }
    
    private void onShrinkPressed() {
        NetworkManager.sendToServer(new ShrinkerBlockEntity.ShrinkerPlayerUsePacket(this.menu.getBlockPos()));
    }
}
