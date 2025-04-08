package rearth.oritech.util.item;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import rearth.oritech.util.item.containers.SimpleInventoryStorage;

public class ItemApi {
    
    public static BlockItemApi BLOCK;

    public interface BlockProvider {
        SimpleInventoryStorage getInventoryStorage(Direction direction);
    }
    
    public interface InventoryStorage {
        
        default boolean supportsInsertion() {
            return true;
        }
        
        // returns how much was inserted
        int insert(ItemStack inserted, boolean simulate);
        
        // returns the remainder
        ItemStack insertToSlot(ItemStack inserted, int slot, boolean simulate);
        
        default boolean supportsExtraction() {
            return true;
        }
        
        // returns how much was extracted
        int extract(ItemStack inserted, boolean simulate);
        
        // returns the extracted stack
        ItemStack extractFromSlot(ItemStack extracted, int slot, boolean simulate);
        
        void setStackInSlot(int slot, ItemStack stack);
        
        ItemStack getStackInSlot(int slot);
        
        int getSlotCount();
        
        int getSlotLimit(int slot);
        
        void update();
        
    }
    

}
