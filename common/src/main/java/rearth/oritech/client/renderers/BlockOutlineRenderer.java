package rearth.oritech.client.renderers;

import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShapes;
import rearth.oritech.Oritech;
import rearth.oritech.block.base.block.MultiblockMachine;
import rearth.oritech.block.blocks.augmenter.AugmentResearchStationBlock;
import rearth.oritech.block.blocks.processing.RefineryModuleBlock;
import rearth.oritech.block.blocks.storage.LargeStorageBlock;
import rearth.oritech.block.blocks.storage.SmallStorageBlock;
import rearth.oritech.init.BlockContent;
import rearth.oritech.init.ItemContent;
import rearth.oritech.init.TagContent;
import rearth.oritech.init.ToolsContent;
import rearth.oritech.item.tools.harvesting.PromethiumPickaxeItem;
import rearth.oritech.util.Geometry;
import rearth.oritech.util.MultiblockMachineController;

import java.util.ArrayList;

public class BlockOutlineRenderer {
    public static void render(ClientWorld world, Camera camera, MatrixStack matrixStack, VertexConsumerProvider consumer) {
        if (world == null) return;
        
        var client = MinecraftClient.getInstance();
        var player = client.player;
        if (player == null || player.isSneaking()) return;
        if (client.crosshairTarget == null || client.crosshairTarget.getType() != HitResult.Type.BLOCK) return;
        
        var itemStack = player.getMainHandStack();
        var blockPos = ((BlockHitResult) client.crosshairTarget).getBlockPos();
        
        if (Oritech.CONFIG.showMachinePreview()) {
            renderBlockPlacementPreviewOutline(world, camera, matrixStack, consumer, itemStack, player, blockPos);
            renderParticlePlacementHelper(world, camera, matrixStack, consumer, itemStack, player, blockPos);
        }
        
        renderPromethiumPickaxeOutline(world, camera, matrixStack, consumer, itemStack, player, blockPos);
    }
    
    private static void renderBlockPlacementPreviewOutline(ClientWorld world, Camera camera, MatrixStack matrixStack, VertexConsumerProvider consumer, ItemStack itemStack, ClientPlayerEntity player, BlockPos blockPos) {
        
        var hasBlockItem = itemStack.getItem() instanceof BlockItem || itemStack.getItem().equals(ItemContent.UNSTABLE_CONTAINER);
        
        if (!hasBlockItem) return;
        
        var block = itemStack.getItem() instanceof BlockItem ? ((BlockItem) itemStack.getItem()).getBlock() : BlockContent.UNSTABLE_CONTAINER;
        
        if (!(block instanceof BlockEntityProvider entityProvider) || !block.getDefaultState().contains(MultiblockMachine.ASSEMBLED))
            return;
        
        var machinePos = blockPos.add(((BlockHitResult) player.client.crosshairTarget).getSide().getVector());
        if (itemStack.getItem().equals(ItemContent.UNSTABLE_CONTAINER))
            machinePos = blockPos;
        var placementState = block.getPlacementState(new ItemPlacementContext(player, player.preferredHand, itemStack, (BlockHitResult) player.client.crosshairTarget));
        var entity = entityProvider.createBlockEntity(machinePos, placementState);
        if (!(entity instanceof MultiblockMachineController multiblockController)) return;
        
        if (itemStack.getItem().equals(ItemContent.UNSTABLE_CONTAINER)) {
            var blockState = world.getBlockState(machinePos);
            var isValid = blockState.isIn(TagContent.UNSTABLE_CONTAINER_SOURCES_LOW) || blockState.isIn(TagContent.UNSTABLE_CONTAINER_SOURCES_MEDIUM) || blockState.isIn(TagContent.UNSTABLE_CONTAINER_SOURCES_HIGH);
            if (!isValid) return;
        }
        
        var coreOffsets = multiblockController.getCorePositions();
        var machineFacing = getFacingFromState(placementState);
        
        if (block instanceof LargeStorageBlock) {    // the large block is weird
            machineFacing = player.getHorizontalFacing().getOpposite();
        } else if (block instanceof AugmentResearchStationBlock) {
            machineFacing = player.getFacing();
        } else if (!(block instanceof MultiblockMachine || block instanceof RefineryModuleBlock)) {
            machineFacing = machineFacing.getOpposite();
        }
        
        var fullList = new ArrayList<>(coreOffsets);
        fullList.add(Vec3i.ZERO);
        
        matrixStack.push();
        var cameraPos = camera.getPos();
        matrixStack.translate(-cameraPos.getX(), -cameraPos.getY(), -cameraPos.getZ());
        matrixStack.translate(0.005f, 0.005f, 0.005f); // slight offset to avoid z fighting
        
        var shape = VoxelShapes.fullCube();
        for (var coreOffset : fullList) {
            var fixedOffset = new Vec3i(coreOffset.getX(), coreOffset.getY(), coreOffset.getZ());
            var worldOffset = Geometry.offsetToWorldPosition(machineFacing, fixedOffset, machinePos);
            shape = VoxelShapes.union(shape, VoxelShapes.cuboid(worldOffset.getX(), worldOffset.getY(), worldOffset.getZ(), worldOffset.getX() + 1, worldOffset.getY() + 1, worldOffset.getZ() + 1));
        }
        
        WorldRenderer.drawCuboidShapeOutline(matrixStack, consumer.getBuffer(RenderLayer.getLines()), shape, 0, 0, 0, 1f, 1f, 1f, 0.7F);
        matrixStack.pop();
        
    }
    
    private static Direction getFacingFromState(BlockState state) {
        if (state.contains(Properties.HORIZONTAL_FACING)) {
            return state.get(Properties.HORIZONTAL_FACING);
        } else if (state.contains(Properties.FACING)) {
            return state.get(Properties.FACING);
        } else if (state.contains(SmallStorageBlock.TARGET_DIR)) {
            return state.get(SmallStorageBlock.TARGET_DIR);
        }
        
        return Direction.NORTH;
    }
    
    private static void renderPromethiumPickaxeOutline(ClientWorld world, Camera camera, MatrixStack matrixStack, VertexConsumerProvider consumer, ItemStack itemStack, ClientPlayerEntity player, BlockPos blockPos) {
        if (!itemStack.isOf(ToolsContent.PROMETHIUM_PICKAXE)) return;
        
        var offsetBlocks = PromethiumPickaxeItem.getOffsetBlocks(world, player, blockPos);
        
        matrixStack.push();
        var cameraPos = camera.getPos();
        matrixStack.translate(-cameraPos.getX(), -cameraPos.getY(), -cameraPos.getZ());
        
        for (var offsetPos : offsetBlocks) {
            var offsetState = world.getBlockState(offsetPos);
            var renderShape = offsetState.getOutlineShape(world, offsetPos);
            
            matrixStack.push();
            matrixStack.translate(offsetPos.getX(), offsetPos.getY(), offsetPos.getZ());
            WorldRenderer.drawCuboidShapeOutline(matrixStack, consumer.getBuffer(RenderLayer.getLines()), renderShape, 0, 0, 0, 0.0F, 0.0F, 0.0F, 0.35F);
            matrixStack.pop();
        }
        
        matrixStack.pop();
    }
    
    private static void renderParticlePlacementHelper(ClientWorld world, Camera camera, MatrixStack matrixStack, VertexConsumerProvider consumer, ItemStack itemStack, ClientPlayerEntity player, BlockPos blockPos) {
        
        var isRing = itemStack.isOf(BlockContent.ACCELERATOR_RING.asItem());
        var isMotor = itemStack.isOf(BlockContent.ACCELERATOR_MOTOR.asItem());
        
        if (!isRing && !isMotor) return;
        
        assert player.client.crosshairTarget != null;
        
        var facing = player.getHorizontalFacing();
        var cameraPos = camera.getPos();
        var blockHit = (BlockHitResult) player.client.crosshairTarget;
        var targetPos = Vec3d.of(blockHit.getBlockPos().add(blockHit.getSide().getVector()));
        
        if (isMotor)
            facing = facing.rotateYClockwise();
        
        var shape = VoxelShapes.cuboid(7/16f, 7/16f, 0, 9/16f, 9/16f, 1f);
        var halfShape = VoxelShapes.cuboid(4/16f, 7/16f, 0.8, 6/16f, 9/16f, 1.3f);
        var halfShapeLeft = VoxelShapes.cuboid(8/16f, 7/16f, 0.3, 10/16f, 9/16f, 0.8f);
        
        matrixStack.push();
        
        matrixStack.translate(-cameraPos.getX(), -cameraPos.getY(), -cameraPos.getZ());
        matrixStack.translate(targetPos.x, targetPos.y, targetPos.z);
        matrixStack.translate(0.005f, 0.005f, 0.005f); // slight offset to avoid z fighting
        
        var rotationY = 0;
        var extraOffset = Vec3d.ZERO;
        if (facing.equals(Direction.WEST)) {
            rotationY = 90;
            extraOffset = new Vec3d(0, 0, 1);
        }
        if (facing.equals(Direction.SOUTH)) {
            rotationY = 180;
            extraOffset = new Vec3d(1, 0, 1);
        }
        if (facing.equals(Direction.EAST)) {
            rotationY = 270;
            extraOffset = new Vec3d(1, 0, 0);
        }
        
        
        matrixStack.translate(extraOffset.x, extraOffset.y, extraOffset.z);
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotationY));
        
        WorldRenderer.drawCuboidShapeOutline(matrixStack, consumer.getBuffer(RenderLayer.getLines()), shape, 0, 0, 0, 1F, 1.0F, 1.0F, 1F);
        
        if (isRing) {
            matrixStack.push();
            matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(30));
            WorldRenderer.drawCuboidShapeOutline(matrixStack, consumer.getBuffer(RenderLayer.getLines()), halfShape, 0, 0, 0, 1F, 1.0F, 1.0F, 1F);
            matrixStack.pop();
            
            matrixStack.push();
            matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-30));
            WorldRenderer.drawCuboidShapeOutline(matrixStack, consumer.getBuffer(RenderLayer.getLines()), halfShapeLeft, 0, 0, 0, 1F, 1.0F, 1.0F, 1F);
            matrixStack.pop();
        }
        
        matrixStack.pop();
    }
}
