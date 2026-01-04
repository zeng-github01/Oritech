package rearth.oritech.fabric;

import dev.architectury.fluid.FluidStack;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.EntityElytraEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.block.ComposterBlock;
import rearth.oritech.Oritech;
import rearth.oritech.api.networking.NetworkManager;
import rearth.oritech.item.tools.armor.JetpackElytraItem;
import rearth.oritech.item.tools.armor.JetpackExoElytraItem;
import rearth.oritech.item.tools.util.ArmorEventHandler;

public final class OritechFabricMod implements ModInitializer {
    @Override
    public void onInitialize() {
        
        NetworkManager.FLUID_STACK_CODEC = FluidStack.CODEC;
        NetworkManager.FLUID_STACK_STREAM_CODEC = FluidStack.STREAM_CODEC;
        
        // Run our common setup.
        Oritech.runAllRegistries();
        Oritech.initialize();
        
        registerFabricEvents();
        
        for (var pair : Oritech.COMPOSTABLES_DATA) {
            ComposterBlock.add(pair.getB(), pair.getA());
        }
        
    }
    
    public static void registerFabricEvents() {
        ServerEntityEvents.EQUIPMENT_CHANGE.register(ArmorEventHandler::processEvent);
        EntityElytraEvents.CUSTOM.register(((entity, tickElytra) -> {
            var chestStack = entity.getItemBySlot(EquipmentSlot.CHEST);
            if (chestStack.getItem() instanceof JetpackElytraItem jetpackElytraItem) {
                return jetpackElytraItem.useCustomElytra(entity, chestStack, tickElytra);
            } else if (chestStack.getItem() instanceof JetpackExoElytraItem jetpackElytraItem) {
                return jetpackElytraItem.useCustomElytra(entity, chestStack, tickElytra);
            }
            
            return false;
        }));
    }
}
