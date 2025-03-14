package rearth.oritech.block.blocks.pipes;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.Oritech;
import rearth.oritech.block.entity.pipes.GenericPipeInterfaceEntity;

public abstract class AbstractPipeBlock extends Block {
    
    private static final Boolean USE_ACCURATE_OUTLINES = Oritech.CONFIG.tightCableHitboxes();
    protected VoxelShape[] boundingShapes;
    
    public AbstractPipeBlock(Settings settings) {
        super(settings);
        this.boundingShapes = createShapes();
    }
    
    protected abstract VoxelShape getShape(BlockState state);
    
    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        if (!USE_ACCURATE_OUTLINES)
            return super.getOutlineShape(state, world, pos, context);
        return getShape(state);
    }
    
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return getShape(state);
    }
    
    protected abstract VoxelShape[] createShapes();
    
    @Override
    public abstract void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify);
    
    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        var baseState = super.getPlacementState(ctx);
        return addConnectionStates(baseState, ctx.getWorld(), ctx.getBlockPos(), true);
    }
    
    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess worldAccess, BlockPos pos, BlockPos neighborPos) {
        var world = (World) worldAccess;
        if (world.isClient) return state;
        
        if (neighborState.isOf(Blocks.AIR))
            // remove potential stale machine -> neighboring pipes mapping
            getNetworkData(world).machinePipeNeighbors.remove(neighborPos);
        
        return state;
    }
    
    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        super.onStateReplaced(state, world, pos, newState, moved);
        
        if (!state.isOf(newState.getBlock()) && !(newState.getBlock() instanceof AbstractPipeBlock)) {
            // block was removed/replaced instead of updated
            onBlockRemoved(pos, state, world);
        }
        
    }
    
    /**
     * Updates all the neighboring pipes of the target position.
     *
     * @param world           The target world
     * @param pos             The target position
     * @param neighborToggled Whether the neighbor was toggled
     */
    public abstract void updateNeighbors(World world, BlockPos pos, boolean neighborToggled);
    
    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!player.isCreative() && !world.isClient) {
            onBlockRemoved(pos, state, world);
        }
        return super.onBreak(world, pos, state, player);
    }
    
    /**
     * Adds the connection states to the pipe block-state.
     *
     * @param state            The current pipe block-state
     * @param world            The target world
     * @param pos              The target pipe position
     * @param createConnection Whether to create a connection
     * @return The updated block-state
     */
    public abstract BlockState addConnectionStates(BlockState state, World world, BlockPos pos, boolean createConnection);
    
    /**
     * Adds the connection states to the pipe block-state.
     * Attempts to create a connection ONLY in the specified direction.
     * Useful for when only one connection needs to be created.
     *
     * @param state           The current pipe block-state
     * @param world           The target world
     * @param pos             The target pipe position
     * @param createDirection The direction to create a connection in
     * @return The updated block-state
     */
    public abstract BlockState addConnectionStates(BlockState state, World world, BlockPos pos, Direction createDirection);
    
    /**
     * Adds the straight property to the pipe block-state.
     *
     * @param state The current pipe block-state
     * @return The updated block-state
     */
    public abstract BlockState addStraightState(BlockState state);
    
    /**
     * Check if the pipe should connect in a specific direction.
     *
     * @param current          The current pipe block-state
     * @param direction        The direction to check
     * @param currentPos       The current pipe position
     * @param world            The target world
     * @param createConnection Whether to create a connection
     * @return Boolean whether the pipe should connect
     */
    public abstract boolean shouldConnect(BlockState current, Direction direction, BlockPos currentPos, World world, boolean createConnection);
    
    /**
     * Check if the pipe is connecting in a specific direction.
     *
     * @param current          The target pipe block-state
     * @param direction        The direction to check
     * @param createConnection Whether to create a connection
     * @return Boolean whether the pipe is connecting
     */
    public abstract boolean isConnectingInDirection(BlockState current, Direction direction, BlockPos currentPos, World world, boolean createConnection);
    
    /**
     * Check if the pipe node has a neighboring machine.
     *
     * @param state The target pipe block-state
     * @param world The target world
     * @param pos   The target pipe position
     * @return Boolean whether a machine is connected
     */
    public boolean hasNeighboringMachine(BlockState state, World world, BlockPos pos, boolean createConnection) {
        var lookup = apiValidationFunction();
        return (isConnectingInDirection(state, Direction.NORTH, pos, world, createConnection) && hasMachineInDirection(Direction.NORTH, world, pos, lookup))
                 || (isConnectingInDirection(state, Direction.EAST, pos, world, createConnection) && hasMachineInDirection(Direction.EAST, world, pos, lookup))
                 || (isConnectingInDirection(state, Direction.SOUTH, pos, world, createConnection) && hasMachineInDirection(Direction.SOUTH, world, pos, lookup))
                 || (isConnectingInDirection(state, Direction.WEST, pos, world, createConnection) && hasMachineInDirection(Direction.WEST, world, pos, lookup))
                 || (isConnectingInDirection(state, Direction.UP, pos, world, createConnection) && hasMachineInDirection(Direction.UP, world, pos, lookup))
                 || (isConnectingInDirection(state, Direction.DOWN, pos, world, createConnection) && hasMachineInDirection(Direction.DOWN, world, pos, lookup));
    }
    
    /**
     * Check if a machine is connected in a specific direction.
     *
     * @param direction The direction to check
     * @param world     The target world
     * @param ownPos    The target pipe position
     * @param lookup    The lookup function {@link AbstractPipeBlock#apiValidationFunction()}
     * @return Boolean whether a machine is connected
     */
    public boolean hasMachineInDirection(Direction direction, World world, BlockPos ownPos, TriFunction<World, BlockPos, Direction, Boolean> lookup) {
        var neighborPos = ownPos.add(direction.getVector());
        var neighborState = world.getBlockState(neighborPos);
        return !(neighborState.getBlock() instanceof GenericPipeBlock) && lookup.apply(world, neighborPos, direction.getOpposite());
    }
    
    /**
     * Check if the target block is a valid connection target.
     *
     * @param target    The target block
     * @param world     The target world
     * @param direction The direction to check (IMPORTANT: This is the direction from the target to the current pipe)
     * @param pos       The target pipe position
     * @return Boolean whether the target is a valid connection target
     */
    public boolean isValidConnectionTarget(Block target, World world, Direction direction, BlockPos pos) {
        var lookupFunction = apiValidationFunction();
        return connectToOwnBlockType(target) || (lookupFunction.apply(world, pos, direction) && isCompatibleTarget(target));
    }
    
    /**
     * Check if the target block is a valid interface target.
     *
     * @param target    The target block
     * @param world     The target world
     * @param direction The direction to check (IMPORTANT: This is the direction from the target to the current pipe)
     * @param pos       The target pipe position
     * @return Boolean whether the target is a valid interface target
     */
    public boolean isValidInterfaceTarget(Block target, World world, Direction direction, BlockPos pos) {
        var lookupFunction = apiValidationFunction();
        return (lookupFunction.apply(world, pos, direction) && isCompatibleTarget(target));
    }
    
    /**
     * Check if the target block is compatible with the pipe block.
     *
     * @param block The target block
     * @return Boolean whether the block is compatible
     */
    public boolean isCompatibleTarget(Block block) {
        return true;
    }
    
    /**
     * Validation function which utilizes lookup API's to check if a block is a valid connection target.
     *
     * @return The validation function for the pipe block
     */
    public abstract TriFunction<World, BlockPos, Direction, Boolean> apiValidationFunction();
    
    public abstract BlockState getConnectionBlock();
    
    public abstract BlockState getNormalBlock();
    
    public abstract String getPipeTypeName();
    
    public abstract boolean connectToOwnBlockType(Block block);
    
    public abstract GenericPipeInterfaceEntity.PipeNetworkData getNetworkData(World world);
    
    protected abstract void onBlockRemoved(BlockPos pos, BlockState oldState, World world);
}
