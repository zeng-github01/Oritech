package rearth.oritech.block.entity.interaction;

import dev.architectury.registry.menu.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.Oritech;
import rearth.oritech.api.energy.EnergyApi;
import rearth.oritech.api.energy.containers.DelegatingEnergyStorage;
import rearth.oritech.api.energy.containers.DynamicStatisticEnergyStorage;
import rearth.oritech.api.item.ItemApi;
import rearth.oritech.api.networking.NetworkedBlockEntity;
import rearth.oritech.api.networking.SyncField;
import rearth.oritech.api.networking.SyncType;
import rearth.oritech.block.base.entity.ExpandableEnergyStorageBlockEntity;
import rearth.oritech.init.BlockEntitiesContent;
import rearth.oritech.util.InventoryInputMode;
import rearth.oritech.util.MultiblockMachineController;
import rearth.oritech.util.ScreenProvider;

import java.util.*;

public class PowerPoleEntity extends NetworkedBlockEntity implements MultiblockMachineController, ExtendedMenuProvider,
                                                                       ScreenProvider, EnergyApi.BlockProvider {
    
    // stores data per dimension
    public static final HashMap<ResourceLocation, PoleNetworkData> POLE_NETWORK_DATA = new HashMap<>();
    
    // multiblock
    private final ArrayList<BlockPos> coreBlocksConnected = new ArrayList<>();
    @SyncField(SyncType.GUI_OPEN)
    private float coreQuality = 1f;
    @SyncField({SyncType.INITIAL, SyncType.CUSTOM})
    private final Set<ConnectionTarget> connections = new HashSet<>();
    
    // storage
    @SyncField({SyncType.GUI_OPEN, SyncType.GUI_TICK})
    protected final PowerPoleEnergyStorage energyStorage = new PowerPoleEnergyStorage();
    
    private final EnergyApi.EnergyStorage outputStorage = new DelegatingEnergyStorage(energyStorage, null) {
        @Override
        public boolean supportsInsertion() {
            return false;
        }
        
        @Override
        public long insert(long amount, boolean simulate) {
            return 0L;
        }
    };
    
    public PowerPoleEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesContent.POWER_POLE_ENTITY, pos, state);
    }
    
    @Override
    public void serverTick(Level world, BlockPos pos, BlockState state, NetworkedBlockEntity blockEntity) {
        
        // todo occasional electric particles while working
        
        outputEnergy();
        
        energyStorage.tick(world.getGameTime());
        
    }
    
    private void outputEnergy() {
        if (!isConnected() || energyStorage.getAmount() <= 0) return;
        
        // todo caching for targets? Used to be BlockApiCache.create()
        var target = ExpandableEnergyStorageBlockEntity.getOutputPosition(worldPosition, getFacingForMultiblock().getCounterClockWise());
        var candidate = EnergyApi.BLOCK.find(level, target.getB(), target.getA().getOpposite());
        if (candidate != null && candidate.supportsInsertion()) {
            EnergyApi.transfer(energyStorage, candidate, Long.MAX_VALUE, false);
        }
    }
    
    public void assignNewTarget(BlockPos target) {
        Oritech.LOGGER.info("Assigning new power pole target");
        
        var targetEntityCandidate = level.getBlockEntity(target, BlockEntitiesContent.POWER_POLE_ENTITY);
        if (targetEntityCandidate.isEmpty()) return;
        var targetEntity = targetEntityCandidate.get();
        
        connections.add(targetEntity.getConnectionData());
        targetEntity.assignIncomingConnection(this);
        
        var allNetworks = getNetworkData();
        
        var ownNet = getNetwork();
        var isConnected = isConnected();
        var targetNet = targetEntity.getNetwork();
        var targetConnected = targetEntity.isConnected();
        
        if (!isConnected && targetConnected) {
            // join network of target
            joinNetwork(targetNet, allNetworks);
        } else if (isConnected && !targetConnected) {
            // join target into own network
            targetEntity.joinNetwork(ownNet, allNetworks);
        } else if (!isConnected && !targetConnected) {
            // neither connected, create new network, then let both join
            var newNet = createNetwork(allNetworks);
            this.joinNetwork(newNet, allNetworks);
            targetEntity.joinNetwork(newNet, allNetworks);
        } else if (isConnected && targetConnected) {
            if (targetNet == ownNet) {
                // in same network, nothing to do
                System.out.println("Same net, no work");
            } else {
                // merge networks
                allNetworks.mergeNetworks(ownNet, targetNet);
            }
        } else {
            throw new IllegalStateException("This should never happen");
        }
        
        allNetworks.setDirty();
        
        updateConnectionsInState(Objects.requireNonNull(getNetwork()));
        targetEntity.updateConnectionsInState(getNetwork());
        
        this.markDirty(false);
        this.sendUpdate(SyncType.CUSTOM);
    }
    
    private void joinNetwork(PoleNetwork target, PoleNetworkData data) {
        data.activeNetworks.put(worldPosition, target);
        System.out.println("Joining network");
    }
    
    private void updateConnectionsInState(PoleNetwork network) {
        network.setPole(worldPosition, connections);
    }
    
    private PoleNetwork createNetwork(PoleNetworkData data) {
        System.out.println("Creating Network");
        return new PoleNetwork();
    }
    
    public void assignIncomingConnection(PowerPoleEntity from) {
        this.connections.add(from.getConnectionData());
        this.markDirty(false);
        this.sendUpdate(SyncType.CUSTOM);
    }
    
    public void removeIncomingConnection(BlockPos source) {
        this.connections.remove(source);
        this.markDirty(false);
        this.sendUpdate(SyncType.CUSTOM);
    }
    
    public ConnectionTarget getConnectionData() {
        return new ConnectionTarget(worldPosition, getFacingForMultiblock());
    }
    
    public Set<ConnectionTarget> getConnections() {
        return connections;
    }
    
    public void onRemoved() {
        
        // remove connection from targets (if loaded)
        for (var target: connections) {
            if (level.isLoaded(target.pos) && level.getBlockEntity(target.pos) instanceof PowerPoleEntity powerPole) {
                powerPole.removeIncomingConnection(worldPosition);
            }
        }
        
        var allNetworks = getNetworkData();
        allNetworks.removePole(worldPosition);
        allNetworks.setDirty();
        
        this.markDirty(false);
        
    }
    
    @Override
    public void markDirty(boolean updateComparator) {
        super.markDirty(updateComparator);
        getNetworkData().setDirty();
    }
    
    private boolean isConnected() {
        return getNetwork() != null;
    }
    
    private PoleNetworkData getNetworkData() {
        return POLE_NETWORK_DATA.computeIfAbsent(level.dimension().location(), data -> new PoleNetworkData());
    }
    
    private @Nullable PoleNetwork getNetwork() {
        return getNetworkData().getNetwork(worldPosition);
    }
    
    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        addMultiblockToNbt(tag);
        
        var connectionList = new ListTag();
        for (var connection : connections) {
            var compound = new CompoundTag();
            compound.putLong("p", connection.pos().asLong());
            compound.putInt("d", connection.facing.ordinal());
        }
        tag.put("connectionData", connectionList);
    }
    
    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        loadMultiblockNbtData(tag);
        
        if (tag.contains("connectionData")) {
            
            connections.clear();
            
            var nbtList = tag.getList("connectionData", Tag.TAG_COMPOUND);
            for (var nbtElem : nbtList) {
                var elem = (CompoundTag) nbtElem;
                var pos = BlockPos.of(elem.getLong("p"));
                var dir = Direction.values()[elem.getInt("d")];
                connections.add(new ConnectionTarget(pos, dir));
            }
            
        }
    }
    
    @Override
    public void saveExtraData(FriendlyByteBuf buf) {
        this.sendUpdate(SyncType.GUI_OPEN);
    }
    
    @Override
    public @NotNull Component getDisplayName() {
        return Component.empty();
    }
    
    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, @NotNull Inventory playerInventory, @NotNull Player player) {
        return null;    // todo
    }
    
    @Override
    public EnergyApi.EnergyStorage getEnergyStorage(Direction direction) {
        
        if (direction != null && direction.equals(getFacingForMultiblock().getCounterClockWise()))
            return outputStorage;
        
        return energyStorage;
    }
    
    @Override
    public List<Vec3i> getCorePositions() {
        return List.of(new Vec3i(1, 0, 0), new Vec3i(-1, 0, 0));
    }
    
    @Override
    public Direction getFacingForMultiblock() {
        return Objects.requireNonNull(level).getBlockState(getBlockPos()).getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
    }
    
    @Override
    public BlockPos getPosForMultiblock() {
        return worldPosition;
    }
    
    @Override
    public Level getWorldForMultiblock() {
        return level;
    }
    
    @Override
    public ArrayList<BlockPos> getConnectedCores() {
        return coreBlocksConnected;
    }
    
    @Override
    public void setCoreQuality(float quality) {
        this.coreQuality = quality;
    }
    
    @Override
    public float getCoreQuality() {
        return coreQuality;
    }
    
    @Override
    public ItemApi.InventoryStorage getInventoryForMultiblock() {
        return null;
    }
    
    @Override
    public EnergyApi.EnergyStorage getEnergyStorageForMultiblock(Direction direction) {
        return energyStorage;
    }
    
    @Override
    public void triggerSetupAnimation() {
    
    }
    
    @Override
    public List<GuiSlot> getGuiSlots() {
        return List.of();
    }
    
    @Override
    public float getDisplayedEnergyUsage() {
        return 0;
    }
    
    @Override
    public float getProgress() {
        return 0;
    }
    
    @Override
    public boolean showProgress() {
        return false;
    }
    
    @Override
    public boolean showExpansionPanel() {
        return false;
    }
    
    @Override
    public InventoryInputMode getInventoryInputMode() {
        return InventoryInputMode.FILL_LEFT_TO_RIGHT;
    }
    
    @Override
    public Container getDisplayedInventory() {
        return null;
    }
    
    @Override
    public MenuType<?> getScreenHandlerType() {
        return null;    // todo
    }
    
    protected class PowerPoleEnergyStorage extends EnergyApi.EnergyStorage {
        
        private final List<Long> inserted = new ArrayList<>();  // just for this tick
        private final List<Long> extracted = new ArrayList<>();
        private final Long[] historicInsert = new Long[20];
        private final Long[] historicExtract = new Long[20];
        private int currentInsertSources = 0;
        private long lastTickedAt = 0;
        
        private PowerPoleEnergyStorage() {
            Arrays.fill(historicInsert, 0L);
            Arrays.fill(historicExtract, 0L);
        }
        
        protected static final Long MAX_CAPACITY = 1_000_000L;
        
        private boolean isValid() {
            return PowerPoleEntity.this.isConnected();
        }
        
        @Override
        public long insert(long maxAmount, boolean simulate) {
            if (!isValid()) return 0;
            
            var insertAmount = Math.min(maxAmount, MAX_CAPACITY - getAmount());
            
            if (insertAmount > 0 && !simulate) {
                var newAmount = getAmount() + insertAmount;
                setAmount(newAmount);
                this.inserted.add(insertAmount);
            }
            
            return insertAmount;
        }
        
        @Override
        public long extract(long maxAmount, boolean simulate) {
            if (!isValid()) return 0;
            
            var extractAmount = Math.min(maxAmount, this.getAmount());
            
            if (extractAmount > 0 && !simulate) {
                var newAmount = getAmount() - extractAmount;
                setAmount(newAmount);
                this.extracted.add(extractAmount);
            }
            
            return extractAmount;
        }
        
        @Override
        public void setAmount(long amount) {
            if (!isValid()) return;
            
            if (amount > MAX_CAPACITY || amount < 0) {
                Oritech.LOGGER.error("tried setting invalid amount for pole network: " + amount);
                return;
            }
            
            var network = PowerPoleEntity.this.getNetwork();
            if (network == null) {
                Oritech.LOGGER.error("Invalid set network state for power pole entity at: {}", worldPosition);
                return;
            }
            
            network.storedEnergy = amount;
            
        }
        
        @Override
        public long getAmount() {
            if (!isValid()) return 0;
            
            var network = PowerPoleEntity.this.getNetwork();
            if (network == null) {
                Oritech.LOGGER.error("Invalid get network state for power pole entity at: {}", worldPosition);
                return 0;
            }
            
            return network.storedEnergy;
        }
        
        @Override
        public long getCapacity() {
            if (!isValid()) return 0;
            return MAX_CAPACITY;
        }
        
        @Override
        public void update() {
            if (!isValid()) return;
            PowerPoleEntity.this.markDirty(false);
        }
        
        public void tick(long worldTicks) {
            if (worldTicks <= lastTickedAt) return;
            var index = (int) (worldTicks % 20);
            historicInsert[index] = inserted.stream().mapToLong(Long::longValue).sum();
            historicExtract[index] = extracted.stream().mapToLong(Long::longValue).sum();
            currentInsertSources = inserted.size();
            
            inserted.clear();
            extracted.clear();
            lastTickedAt = worldTicks;
        }
        
        public DynamicStatisticEnergyStorage.EnergyStatistics getCurrentStatistics(long worldTicks) {
            var index = (int) (worldTicks % 20);
            
            return new DynamicStatisticEnergyStorage.EnergyStatistics(
              (float) Arrays.stream(historicInsert).mapToLong(Long::longValue).average().orElse(0),
              (float) Arrays.stream(historicExtract).mapToLong(Long::longValue).average().orElse(0),
              historicInsert[index],
              historicExtract[index],
              currentInsertSources,
              Arrays.stream(historicInsert).mapToLong(Long::longValue).max().orElse(0),
              Arrays.stream(historicExtract).mapToLong(Long::longValue).max().orElse(0)
            );
            
        }
    }
    
    public record ConnectionTarget(BlockPos pos, Direction facing) {}
    
    // this is kept separate from the block entities (and fully decoupled) so it works well across unloaded areas,
    // even if some poles are in the middle of it
    public static class PoleNetworkData extends SavedData {
        
        // runtime lookup map. Pole positions are also stored in the network, so for saving the keys can be reconstructed later here
        private final Map<BlockPos, PoleNetwork> activeNetworks = new HashMap<>();
        
        public @Nullable PoleNetwork getNetwork(BlockPos pos) {
            return activeNetworks.get(pos);
        }
        
        public static Factory<PoleNetworkData> TYPE = new Factory<>(PoleNetworkData::new, PoleNetworkData::fromNbt, null);
        
        @Override
        public @NotNull CompoundTag save(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
            
            System.out.println("Saving power pole data!");
            
            var networksList = new ListTag();
            
            var uniqueNetworks = new HashSet<>(activeNetworks.values());
            
            // Iterate the unique networks and save them
            for (var network : uniqueNetworks) {
                
                var networkCompound = new CompoundTag();
                networkCompound.putLong("energy", network.storedEnergy);
                
                var poleList = new ListTag();
                for (var polePair : network.poles.entrySet()) {
                    var data = new CompoundTag();
                    data.putLong("pos", polePair.getKey().asLong());
                    data.putLongArray("cons", polePair.getValue().stream().mapToLong(BlockPos::asLong).toArray());
                    poleList.add(data);
                }
                
                networkCompound.put("poles", poleList);
                networksList.add(networkCompound);
            }
            
            tag.put("networks", networksList);
            return tag;
        }
        
        public static PoleNetworkData fromNbt(CompoundTag nbt, HolderLookup.Provider registryLookup) {
            
            System.out.println("reading power pole data!");
            
            var data = new PoleNetworkData();
            
            if (!nbt.contains("networks")) return data;
            
            var networksList = nbt.getList("networks", Tag.TAG_COMPOUND);
            
            for (var networkTag : networksList) {
                
                var tag = (CompoundTag) networkTag;
                
                var energy = tag.getLong("energy");
                var poles = new HashMap<BlockPos, Set<BlockPos>>();
                var poleDataList = tag.getList("poles", Tag.TAG_COMPOUND);
                for (var poleDataTag : poleDataList) {
                    var poleData = (CompoundTag) poleDataTag;
                    var polePos = BlockPos.of(poleData.getLong("pos"));
                    var poleConnections = new HashSet<>(Arrays.stream(poleData.getLongArray("cons")).mapToObj(BlockPos::of).toList());
                    poles.put(polePos, poleConnections);
                }
                
                var network = new PoleNetwork(poles, energy);
                
                for (var polePos : network.getPoles())
                    data.activeNetworks.put(polePos, network);
            }
            
            return data;
        }
        
        protected void mergeNetworks(PoleNetwork netA, PoleNetwork netB) {
            
            System.out.println("merging networks");
            
            // move all from netB to netA
            netA.storedEnergy = Math.min(PowerPoleEnergyStorage.MAX_CAPACITY, netA.storedEnergy + netB.storedEnergy);
            
            netA.poles.putAll(netB.poles);
            
            for (var polePos : netB.getPoles()) {
                activeNetworks.put(polePos, netA);
            }
            
        }
        
        public void removePole(BlockPos removeAt) {
            
            System.out.println("removing pole");
            
            // first, updating connections in network data
            var existingNet = activeNetworks.get(removeAt);
            if (existingNet == null) return;
            
            activeNetworks.remove(removeAt);
            
            // for all connections, remove any to the deleted pole
            for (var connections : existingNet.poles.values()) {
                connections.remove(removeAt);
            }
            
            var removedPoleConnections = existingNet.poles.remove(removeAt);
            
            if (removedPoleConnections.size() <= 1) return; // no split needed
            
            // potential split needed
            var newNets = new HashSet<Map<BlockPos, Set<BlockPos>>>();
            
            for (var deletedConnection : removedPoleConnections) {
                
                var newConnectionNet = FloodFillNetwork(existingNet, deletedConnection);
                newNets.add(newConnectionNet);
                
            }
            
            System.out.println("split into: " + newNets.size());
            
            if (newNets.size() == 1) return;    // no split needed, there's other connections doing the same
            
            var newNetCount = newNets.size();
            var newNetPower = existingNet.storedEnergy / newNetCount;
            
            for (var newNetData : newNets) {
                var newNet = new PoleNetwork(newNetData, newNetPower);
                for (var polePos : newNet.getPoles())
                    activeNetworks.put(polePos, newNet);
            }
            
        }
        
        // the network is potentially split at this stage. Returns all poles connected to the marked start
        private static Map<BlockPos, Set<BlockPos>> FloodFillNetwork(PoleNetwork existing, BlockPos startAt) {
            
            var maxIterations = 200;
            var result = new HashMap<BlockPos, Set<BlockPos>>();
            
            var openChecks = Set.of(startAt);
            
            // basically a while loop, but with an extra safety check
            for (int i = 0; i < maxIterations; i++) {
                
                var next = new HashSet<BlockPos>();
                for (var openPole : openChecks) {
                    var connections = existing.getConnections(openPole);
                    result.put(openPole, connections);
                    
                    // add all connections that we dont have already
                    next.addAll(connections.stream().filter(elem -> !result.containsKey(elem)).toList());
                }
                
                if (next.isEmpty()) break;
                
                openChecks = next;
            }
            
            
            return result;
        }
        
    }
    
    // stores the energy in a network. Also includes a list of poles and their connection (only used for floodfill when splitting networks)
    public static class PoleNetwork {
        
        // contains all poles as key, and 0-N positions as value
        private final Map<BlockPos, Set<BlockPos>> poles;
        
        public long storedEnergy = 0L;
        
        // constructor for codec
        private PoleNetwork(Map<BlockPos, Set<BlockPos>> loadedPoles, long storedEnergy) {
            this.poles = new HashMap<>(loadedPoles);
            this.storedEnergy = storedEnergy;
        }
        
        // default constructor
        public PoleNetwork() {
            this.poles = new HashMap<>();
        }
        
        public Set<BlockPos> getPoles() {
            return poles.keySet();
        }
        
        public Set<BlockPos> getConnections(BlockPos polePos) {
            return poles.get(polePos);
        }
        
        // adds or updates a pole in a network
        public void setPole(BlockPos pole, Set<ConnectionTarget> connections) {
            poles.put(pole, new HashSet<>(connections.stream().map(elem -> elem.pos()).toList()));
        }
    }
}
