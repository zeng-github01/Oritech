package rearth.oritech.block.blocks.decorative;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.init.BlockContent;

// todo translation, recipe
public class MetalGirderBlock extends HorizontalFacingBlock {
    
    public static final BooleanProperty HEADING = BooleanProperty.of("heading");
    
    private static final VoxelShape NORTH_SHAPE = Block.createCuboidShape(0, 4, 4, 16, 12, 12);
    private static final VoxelShape EAST_SHAPE = Block.createCuboidShape(4, 4, 0, 12, 12, 16);
    
    public MetalGirderBlock(Settings settings) {
        super(settings);
        this.setDefaultState(getDefaultState().with(HEADING, false));
    }
    
    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(HEADING);
        builder.add(Properties.HORIZONTAL_FACING);
    }
    
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        var facing = state.get(Properties.HORIZONTAL_FACING);
        if (facing.getAxis().equals(Direction.Axis.X)) return NORTH_SHAPE;
        return EAST_SHAPE;
    }
    
    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return getCollisionShape(state, world, pos, context);
    }
    
    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        
        var facing = state.get(Properties.HORIZONTAL_FACING);
        var blockInBack = world.getBlockState(pos.add(facing.getVector())).isOf(BlockContent.METAL_GIRDER_BLOCK);
        var blockInFront = world.getBlockState(pos.add(facing.getOpposite().getVector())).isOf(BlockContent.METAL_GIRDER_BLOCK);
        var straight = false;
        if (blockInFront && !blockInBack) {
            facing = facing.getOpposite();
        } else if (blockInFront && blockInBack) {
            straight = true;
        }
        
        return state.with(Properties.HORIZONTAL_FACING, facing).with(HEADING, !straight);
        
    }
    
    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        var world = ctx.getWorld();
        var pos = ctx.getBlockPos();
        
        // if placed on ceiling/wall, rotate with player facing. Otherwise get surface facing.
        // if block behind is other girder, use straight model. Otherwise it's an endstop.
        
        var facing = ctx.getSide();
        
        if (ctx.getSide().getAxis().equals(Direction.Axis.Y)) {
            facing = ctx.getHorizontalPlayerFacing().getOpposite();
        }
        
        var blockInBack = world.getBlockState(pos.add(facing.getVector())).isOf(BlockContent.METAL_GIRDER_BLOCK);
        var blockInFront = world.getBlockState(pos.add(facing.getOpposite().getVector())).isOf(BlockContent.METAL_GIRDER_BLOCK);
        var straight = false;
        if (blockInFront && !blockInBack) {
            facing = facing.getOpposite();
        } else if (blockInFront && blockInBack) {
            straight = true;
        }
        
        return getDefaultState().with(Properties.HORIZONTAL_FACING, facing).with(HEADING, !straight);
    }
    
    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return null;
    }
}
