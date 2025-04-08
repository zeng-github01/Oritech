package rearth.oritech.fabric;

import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.Oritech;
import rearth.oritech.util.item.BlockItemApi;
import rearth.oritech.util.item.ItemApi;
import rearth.oritech.util.item.containers.SimpleInventoryStorage;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public class FabricItemApi implements BlockItemApi {
    
    @Override
    public void registerBlockEntity(Supplier<BlockEntityType<?>> typeSupplier) {
        ItemStorage.SIDED.registerForBlockEntity((entity, direction) ->
                                                   ContainerStorageWrapper.of(((ItemApi.BlockProvider) entity).getInventoryStorage(direction)), typeSupplier.get());
    }
    
    @Override
    public ItemApi.InventoryStorage find(World world, BlockPos pos, @Nullable BlockState state, @Nullable BlockEntity entity, @Nullable Direction direction) {
        var candidate = ItemStorage.SIDED.find(world, pos, state, entity, direction);
        if (candidate == null) return null;
        if (candidate instanceof ContainerStorageWrapper wrapper) return wrapper.container;
        return new FabricStorageWrapper(candidate);
    }
    
    @Override
    public ItemApi.InventoryStorage find(World world, BlockPos pos, @Nullable Direction direction) {
        return find(world, pos, null, null, direction);
    }
    
    // used to interact with storages from other mods
    public static class FabricStorageWrapper implements ItemApi.InventoryStorage {
        
        public final Storage<ItemVariant> storage;
        
        public FabricStorageWrapper(Storage<ItemVariant> storage) {
            this.storage = storage;
        }
        
        @Override
        public boolean supportsInsertion() {
            return storage.supportsInsertion();
        }
        
        @Override
        public int insert(ItemStack inserted, boolean simulate) {
            try (var transaction = Transaction.openOuter()) {
                var insertCount = storage.insert(ItemVariant.of(inserted), inserted.getCount(), transaction);
                if (!simulate)
                    transaction.commit();
                return (int) insertCount;
            }
        }
        
        @Override
        public ItemStack insertToSlot(ItemStack inserted, int slot, boolean simulate) {
            
            // this usually won't be used
            if (storage instanceof SlottedStorage<ItemVariant> slottedStorage) {
                try (var transaction = Transaction.openOuter()) {
                    var insertCount = slottedStorage.getSlot(slot).insert(ItemVariant.of(inserted), inserted.getCount(), transaction);
                    if (!simulate)
                        transaction.commit();
                    return inserted.copyWithCount((int) insertCount);
                }
                
            }
            
            return ItemStack.EMPTY;
        }
        
        @Override
        public boolean supportsExtraction() {
            return storage.supportsExtraction();
        }
        
        @Override
        public int extract(ItemStack extracted, boolean simulate) {
            try (var transaction = Transaction.openOuter()) {
                var extractedCount = storage.extract(ItemVariant.of(extracted), extracted.getCount(), transaction);
                if (!simulate)
                    transaction.commit();
                return (int) extractedCount;
            }
        }
        
        @Override
        public ItemStack extractFromSlot(ItemStack extracted, int slot, boolean simulate) {
            
            if (storage instanceof SlottedStorage<ItemVariant> slottedStorage) {
                try (var transaction = Transaction.openOuter()) {
                    var extractedCount = slottedStorage.getSlot(slot).extract(ItemVariant.of(extracted), extracted.getCount(), transaction);
                    if (!simulate)
                        transaction.commit();
                    return extracted.copyWithCount((int) extractedCount);
                }
                
            }
            
            return ItemStack.EMPTY;
        }
        
        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            Oritech.LOGGER.error("Unable to set stack in slot: {}, stack is: {}", slot, stack);
            Oritech.LOGGER.error("This should never happen");
        }
        
        @Override
        public ItemStack getStackInSlot(int slot) {
            // this usually won't be used
            
            if (storage instanceof SlottedStorage<ItemVariant> slottedStorage) {
                return slottedStorage.getSlot(slot).getResource().toStack((int) slottedStorage.getSlot(slot).getAmount());
            }
            
            return ItemStack.EMPTY;
        }
        
        @Override
        public int getSlotCount() {
            
            if (storage instanceof SlottedStorage<ItemVariant> slottedStorage) {
                return slottedStorage.getSlotCount();
            }
            
            return 1;
        }
        
        @Override
        public int getSlotLimit(int slot) {
            
            if (storage instanceof SlottedStorage<ItemVariant> slottedStorage) {
                return (int) slottedStorage.getSlot(slot).getCapacity();
            }
            
            return 0;
        }
        
        @Override
        public void update() {
        
        }
    }
    
    public static class ContainerStorageWrapper extends SnapshotParticipant<List<ItemStack>> implements Storage<ItemVariant> {
        
        private final SimpleInventoryStorage container;
        
        public static ContainerStorageWrapper of(SimpleInventoryStorage container) {
            if (container == null) return null;
            return new ContainerStorageWrapper(container);
        }
        
        public ContainerStorageWrapper(SimpleInventoryStorage container) {
            this.container = container;
        }
        
        @Override
        public boolean supportsInsertion() {
            return container.supportsInsertion();
        }
        
        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            updateSnapshots(transaction);
            transaction.addCloseCallback((transactionContext, result) -> {
                if (result.wasCommitted()) {
                    container.update();
                }
            });
            
            return container.insert(resource.toStack((int) maxAmount), false);
        }
        
        @Override
        public boolean supportsExtraction() {
            return container.supportsExtraction();
        }
        
        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            updateSnapshots(transaction);
            transaction.addCloseCallback((transactionContext, result) -> {
                if (result.wasCommitted()) {
                    container.update();
                }
            });
            
            return container.extract(resource.toStack((int) maxAmount), false);
        }
        
        @Override
        public @NotNull Iterator<StorageView<ItemVariant>> iterator() {
            return IntStream.range(0, container.getSlotCount()).mapToObj(slot -> new StorageView<ItemVariant>() {
                @Override
                public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
                    updateSnapshots(transaction);
                    transaction.addCloseCallback((transactionContext, result) -> {
                        if (result.wasCommitted()) {
                            container.update();
                        }
                    });
                    
                    var extracted = container.extractFromSlot(resource.toStack((int) maxAmount), slot, false);
                    return extracted.getCount();
                }
                
                @Override
                public boolean isResourceBlank() {
                    return getStack().isEmpty();
                }
                
                @Override
                public ItemVariant getResource() {
                    return ItemVariant.of(getStack());
                }
                
                @Override
                public long getAmount() {
                    return getStack().getCount();
                }
                
                @Override
                public long getCapacity() {
                    return container.getMaxCount(getStack());
                }
                
                private ItemStack getStack() {
                    return container.getStack(slot);
                }
                
            }.getUnderlyingView()).iterator();
        }
        
        @Override
        protected List<ItemStack> createSnapshot() {
            return new ArrayList<>(container.heldStacks);
        }
        
        @Override
        protected void readSnapshot(List<ItemStack> snapshot) {
            container.clear();
            container.heldStacks.addAll(snapshot);
        }
    }
    
    
    
}
