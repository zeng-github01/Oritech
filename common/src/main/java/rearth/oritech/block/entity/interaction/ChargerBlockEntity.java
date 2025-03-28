package rearth.oritech.block.entity.interaction;

import dev.architectury.fluid.FluidStack;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.fluid.Fluids;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.BucketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.Oritech;
import rearth.oritech.client.init.ModScreens;
import rearth.oritech.client.init.ParticleContent;
import rearth.oritech.client.ui.BasicMachineScreenHandler;
import rearth.oritech.init.BlockEntitiesContent;
import rearth.oritech.init.ComponentContent;
import rearth.oritech.init.ToolsContent;
import rearth.oritech.item.tools.armor.BaseJetpackItem;
import rearth.oritech.network.NetworkContent;
import rearth.oritech.util.*;
import rearth.oritech.util.energy.EnergyApi;
import rearth.oritech.util.energy.containers.DynamicEnergyStorage;

import java.util.List;

public class ChargerBlockEntity extends BlockEntity implements BlockEntityTicker<ChargerBlockEntity>, FluidProvider, EnergyApi.BlockProvider, InventoryProvider, ScreenProvider, ExtendedScreenHandlerFactory {
    
    protected final DynamicEnergyStorage energyStorage = new DynamicEnergyStorage(Oritech.CONFIG.charger.energyCapacity(), Oritech.CONFIG.charger.maxEnergyInsertion(), Oritech.CONFIG.charger.maxEnergyExtraction(), this::markDirty);
    
    // 0 = bucket/item to be charged/filled, 1 = empty bucket/charged/fill item
    public final SimpleInventory inventory = new SimpleSidedInventory(2, new InventorySlotAssignment(0, 1, 1, 1)) {
        @Override
        public void markDirty() {
            ChargerBlockEntity.this.markDirty();
        }
    };
    public final InventoryStorage inventoryStorage = InventoryStorage.of(inventory, null);
    
    private final SingleVariantStorage<FluidVariant> fluidStorage = new SingleVariantStorage<>() {
        @Override
        protected FluidVariant getBlankVariant() {
            return FluidVariant.blank();
        }
        
        @Override
        protected long getCapacity(FluidVariant variant) {
            return (16 * FluidConstants.BUCKET);
        }
        
        @Override
        protected boolean canInsert(FluidVariant variant) {
            var jetpackItem = (BaseJetpackItem) ToolsContent.JETPACK.asItem();
            
            return jetpackItem.isValidFuel(variant.getFluid()) && super.canInsert(variant);
        }
        
        @Override
        protected void onFinalCommit() {
            super.onFinalCommit();
            ChargerBlockEntity.this.markDirty();
        }
    };
    
    private boolean networkDirty = false;
    
    public ChargerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesContent.CHARGER_BLOCK_ENTITY, pos, state);
    }
    
    @Override
    public void tick(World world, BlockPos pos, BlockState state, ChargerBlockEntity blockEntity) {
        if (world.isClient) return;
        
        if (networkDirty) {
            updateNetwork();
        }
        
        // stop if no input is given, or it's a stackable item
        if (inventory.getStack(0).isEmpty() || inventory.getStack(0).getCount() > 1) return;
        processBuckets();
        
        var isFull = true;
        var startEnergy = energyStorage.amount;
        var startFluid = fluidStorage.amount;
        
        // try charge item
        if (!chargeItems()) isFull = false;
        
        // try filling item
        if (!fillItems()) isFull = false;
        
        // move charged and/or filled item to right
        if (isFull) {
            var outSlot = inventory.getStack(1);
            if (outSlot.isEmpty()) {
                inventory.setStack(1, inventory.getStack(0));
                inventory.setStack(0, ItemStack.EMPTY);
            }
        }
        
        if (fluidStorage.amount != startFluid || energyStorage.amount != startEnergy) {
            ParticleContent.ASSEMBLER_WORKING.spawn(world, pos.toCenterPos().add(0.1, 0.1, 0), 1);
        }
        
    }
    
    private void processBuckets() {
        var inStack = inventory.getStack(0);
        
        if (inStack != ItemStack.EMPTY && inStack.getItem() instanceof BucketItem bucketItem) {
            // empty input bucket
            var emptyBucket = ItemVariant.of(Items.BUCKET, inStack.getComponentChanges()).toStack();
            if (!outputCanAcceptBucket(emptyBucket)) return;
            var bucketFluid = bucketItem.arch$getFluid();
            if (bucketFluid == Fluids.EMPTY) return;
            
            try (var tx = Transaction.openOuter()) {
                long inserted = fluidStorage.insert(FluidVariant.of(bucketFluid), FluidConstants.BUCKET, tx);
                if (inserted != FluidConstants.BUCKET) return;
                
                inStack.decrement(1);
                if (inventory.getStack(1).isEmpty()) {
                    inventory.heldStacks.set(1, emptyBucket);
                } else {
                    inventory.getStack(1).increment(1);
                }
                inventory.heldStacks.set(0, inStack);
                tx.commit();
            }
            
            // shouldn't be necessary, since tx.commit should already be marking this dirty
            this.markDirty();
        }
    }
    
    private boolean outputCanAcceptBucket(ItemStack bucket) {
        var slot = inventory.getStack(1);
        return (slot.isEmpty() || (slot.isStackable() && ItemStack.areItemsAndComponentsEqual(slot, bucket) && slot.getCount() < slot.getMaxCount()));
    }
    
    @Override
    public void markDirty() {
        super.markDirty();
        networkDirty = true;
    }
    
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        SingleVariantStorage.writeNbt(fluidStorage, FluidVariant.CODEC, nbt, registryLookup);
        Inventories.writeNbt(nbt, inventory.heldStacks, false, registryLookup);
        nbt.putLong("energy_stored", energyStorage.amount);
    }
    
    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        SingleVariantStorage.readNbt(fluidStorage, FluidVariant.CODEC, FluidVariant::blank, nbt, registryLookup);
        Inventories.readNbt(nbt, inventory.heldStacks, registryLookup);
        energyStorage.amount = nbt.getLong("energy_stored");
    }
    
    private void updateNetwork() {
        networkDirty = false;
        
        NetworkContent.MACHINE_CHANNEL.serverHandle(this).send(new NetworkContent.GenericEnergySyncPacket(pos, energyStorage.amount, energyStorage.capacity));
        NetworkContent.MACHINE_CHANNEL.serverHandle(this).send(new NetworkContent.SingleVariantFluidSyncPacket(pos, Registries.FLUID.getId(fluidStorage.variant.getFluid()).toString(), fluidStorage.amount));
    }
    
    // return true if nothing is left to charge
    private boolean chargeItems() {
        var heldStack = inventory.heldStacks.get(0);
        
        var stackRef = new StackContext(heldStack, updated -> inventory.heldStacks.set(0, updated));
        var slotEnergyContainer = EnergyApi.ITEM.find(stackRef);
        if (slotEnergyContainer != null) {
            EnergyApi.transfer(energyStorage, slotEnergyContainer, Long.MAX_VALUE, false);
            return slotEnergyContainer.getAmount() >= slotEnergyContainer.getCapacity();
        } else {
            return true;
        }
    }
    
    // return true if nothing is left to fill
    private boolean fillItems() {
        
        var inputItem = inventory.getStack(0);
        var rate = (long) (FluidConstants.BUCKET * 0.05f);
        
        // ensure we are trying to charge a jetpack
        if (!inputItem.isEmpty() && inputItem.getItem() instanceof BaseJetpackItem jetpackItem) {
            
            var container = jetpackItem.getStoredFluid(inputItem);
            var usedRate = Math.min(rate, jetpackItem.getFuelCapacity() - container.getAmount());
            
            if (container.getAmount() >= jetpackItem.getFuelCapacity()) return true;
            
            // ensure jetpack can be filled from storage
            if (fluidStorage.amount > usedRate
                  && jetpackItem.isValidFuel(fluidStorage.variant.getFluid())
                  && (container.isEmpty() || container.getFluid().equals(fluidStorage.variant.getFluid()))) {
                
                // actually fill jetpack
                var newAmount = container.getAmount() + usedRate;
                inputItem.set(ComponentContent.STORED_FLUID.get(), FluidStack.create(fluidStorage.variant.getFluid(), newAmount));
                fluidStorage.amount -= usedRate;
                
                networkDirty = true;
                
            }
            return false;
            
        } else {
            return true;
        }
        
    }
    
    @Override
    public Text getDisplayName() {
        return Text.literal("");
    }
    
    @Override
    public Object getScreenOpeningData(ServerPlayerEntity player) {
        return new ModScreens.BasicData(pos);
    }
    
    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        updateNetwork();
        return new BasicMachineScreenHandler(syncId, playerInventory, this);
    }
    
    @Override
    public ScreenHandlerType<?> getScreenHandlerType() {
        return ModScreens.CHARGER_SCREEN;
    }
    
    @Override
    public EnergyApi.EnergyContainer getStorage(Direction direction) {
        return energyStorage;
    }
    
    @Override
    public Storage<ItemVariant> getInventory(Direction direction) {
        return inventoryStorage;
    }
    
    @Override
    public List<GuiSlot> getGuiSlots() {
        return List.of(new GuiSlot(0, 56, 38), new GuiSlot(1, 117, 38));
    }
    
    @Override
    public float getDisplayedEnergyUsage() {
        return 1024;
    }
    
    @Override
    public float getProgress() {
        return 0;
    }
    
    @Override
    public InventoryInputMode getInventoryInputMode() {
        return InventoryInputMode.FILL_LEFT_TO_RIGHT;
    }
    
    @Override
    public Inventory getDisplayedInventory() {
        return inventory;
    }
    
    @Override
    public boolean inputOptionsEnabled() {
        return false;
    }
    
    @Override
    public boolean showArmor() {
        return true;
    }
    
    @Override
    public boolean showExpansionPanel() {
        return false;
    }
    
    @Override
    public Storage<FluidVariant> getFluidStorage(Direction direction) {
        return fluidStorage;
    }
    
    @Override
    public @Nullable SingleVariantStorage<FluidVariant> getForDirectFluidAccess() {
        return fluidStorage;
    }
}
