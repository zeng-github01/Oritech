package rearth.oritech.item.tools;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageSources;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Pair;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraft.world.explosion.ExplosionBehavior;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.Oritech;
import rearth.oritech.api.energy.EnergyApi;
import rearth.oritech.api.energy.containers.DynamicEnergyStorage;
import rearth.oritech.block.blocks.processing.MachineCoreBlock;
import rearth.oritech.block.entity.MachineCoreEntity;
import rearth.oritech.block.entity.interaction.LaserArmBlockEntity;
import rearth.oritech.client.init.ParticleContent;
import rearth.oritech.client.renderers.PromethiumToolRenderer;
import rearth.oritech.init.TagContent;
import rearth.oritech.item.tools.util.OritechEnergyItem;
import rearth.oritech.util.TooltipHelper;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static rearth.oritech.block.entity.interaction.LaserArmBlockEntity.BLOCK_BREAK_ENERGY;
import static rearth.oritech.item.tools.harvesting.DrillItem.BAR_STEP_COUNT;

// todo tooltip about enchantability (power, unbreaking, anything of a pickaxe)
public class PortableLaserItem extends Item implements OritechEnergyItem, GeoItem {
    
    public static int ACTION_COOLDOWN = 16;
    public static float MINING_SPEED_MULTIPLIER = 0.25f;
    
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation SHOOTING = RawAnimation.begin().thenPlay("shooting");
    private static final RawAnimation SINGLE_SHOT = RawAnimation.begin().thenPlay("singleshot");
    
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    
    private static final Map<PlayerEntity, Pair<BlockPos, Integer>> blockBreakStats = new HashMap<>();
    
    public PortableLaserItem(Settings settings) {
        super(settings);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }
    
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        
        var stack = player.getStackInHand(hand);
        
        if (world.isClient || !(stack.getItem() instanceof PortableLaserItem laserItem)) return TypedActionResult.consume(stack);
        
        var energyUsed = 50_000;
        if (!laserItem.tryUseEnergy(stack, energyUsed, player)) {
            return TypedActionResult.pass(stack);
        }
        
        if (player.getItemCooldownManager().isCoolingDown(this)) return TypedActionResult.fail(stack);
        player.getItemCooldownManager().set(this, ACTION_COOLDOWN);
        
        Vec3d endPos;
        
        var hit = getPlayerTargetRay(player);
        if (hit != null) {
            world.createExplosion(null, new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(DamageTypes.LIGHTNING_BOLT), player),
              null, hit.getPos(), 6, false, World.ExplosionSourceType.MOB);
            
            endPos = hit.getPos();
        } else {
            var startPos = player.getEyePos();
            var lookVec = player.getRotationVec(0F);
            endPos = startPos.add(lookVec.multiply(128));
        }
        
        if (hit instanceof EntityHitResult entityHitResult && entityHitResult.getEntity() instanceof LivingEntity livingEntity) {
            processEntityTarget(player, livingEntity,  20, stack, world);
        }
        
        triggerAnim(player, GeoItem.getId(stack), "laser", "singleshot");
        
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.8f, 1f);
        
        // Calculate the "right" direction based on the player's yaw
        float yawRadians = (player.getYaw() + 90) * (float) Math.PI / 180;
        double rightX = -MathHelper.sin(yawRadians);
        double rightZ = MathHelper.cos(yawRadians);
        Vec3d rightDir = new Vec3d(rightX, 0, rightZ).normalize();
        
        var startPos = player.getEyePos().add(endPos.subtract(player.getEyePos()).multiply(0.4f)).add(0, -0.5f, 0).add(rightDir.multiply(0.3f));
        ParticleContent.LASER_BOOM.spawn(world, startPos, endPos);
        ParticleContent.MELTDOWN_IMMINENT.spawn(world, endPos, 6);
        
        return TypedActionResult.consume(stack);
    }
    
    public static void onUseTick(PlayerEntity player) {
        var world = player.getWorld();
        var stack = player.getStackInHand(Hand.MAIN_HAND);
        
        if (!(stack.getItem() instanceof PortableLaserItem laserItem) || world == null) return;
        
        var energyUsed = 2048;
        if (!laserItem.tryUseEnergy(stack, energyUsed, player)) {
            return;
        }
        
        // todo config value for range
        var finalHit = getPlayerTargetRay(player);
        
        laserItem.triggerAnim(player, GeoItem.getId(stack), "laser", "shooting");
        
        if (finalHit instanceof BlockHitResult blockHitResult) {
            var blockPos = blockHitResult.getBlockPos();
            var blockState = world.getBlockState(blockPos);
            if (blockState.isAir() || blockState.isIn(TagContent.LASER_PASSTHROUGH)) return;
            processBlockBreaking(blockPos, blockState, world, player, stack, energyUsed);
        } else if (finalHit instanceof EntityHitResult entityHitResult) {
            var target = entityHitResult.getEntity();
            if (!(target instanceof LivingEntity livingEntity)) return;
            processEntityTarget(player, livingEntity, 6, stack, world);
        }
        
        if (finalHit != null && finalHit.getType() != HitResult.Type.MISS) {
            ParticleContent.LASER_BEAM_EFFECT.spawn(world, finalHit.getPos());
        }
        
    }
    
    private static @Nullable HitResult getPlayerTargetRay(PlayerEntity player) {
        
        // block raycast
        var blockHit = player.raycast(128, 0, true);
        
        // entity raycast
        // possible idea for future optimization: do a custom raycast here with slightly inflated bounding boxes to make aiming easier
        var startPos = player.getEyePos();
        var lookVec = player.getRotationVec(0F);
        var endPos = startPos.add(lookVec.multiply(128));
        var entityHit = ProjectileUtil.raycast(
          player,
          startPos,
          endPos,
          new Box(startPos, endPos),
          entity -> !entity.isSpectator() && entity.isAttackable() && entity.isAlive() && entity != player,
          128 * 128
        );
        
        // Determine the closest hit
        HitResult finalHit = null;
        var blockDistance = blockHit.getType() == HitResult.Type.BLOCK ? startPos.squaredDistanceTo(blockHit.getPos()) : Double.MAX_VALUE;
        var entityDistance = entityHit != null ? startPos.squaredDistanceTo(entityHit.getPos()) : Double.MAX_VALUE;
        
        if (blockDistance < entityDistance) {
            finalHit = blockHit;
        } else if (entityHit != null) {
            finalHit = entityHit;
        }
        return finalHit;
    }
    
    private static void processBlockBreaking(BlockPos blockPos, BlockState blockState, World world, PlayerEntity player, ItemStack tool, int energyUsed) {
        
        var stats = blockBreakStats.getOrDefault(player, new Pair<>(BlockPos.ORIGIN, 0));
        if (!blockPos.equals(stats.getLeft())) {
            stats = new Pair<>(blockPos, energyUsed);
        } else {
            stats = new Pair<>(blockPos, stats.getRight() + energyUsed);
        }
        
        if (blockState.isIn(TagContent.LASER_ACCELERATED)) {
            blockState.randomTick((ServerWorld) world, blockPos, world.random);
            ParticleContent.ACCELERATING.spawn(world, Vec3d.of(blockPos));
            stats = new Pair<>(blockPos, -1);
        }
        
        var blockEntity = world.getBlockEntity(blockPos);
        if (blockEntity instanceof MachineCoreEntity coreBlock && coreBlock.isEnabled()) {
            blockEntity = (BlockEntity) coreBlock.getCachedController();
        }
        if (blockEntity != null) {
            var storageCandidate = EnergyApi.BLOCK.find(world, blockPos, blockState, null, null);
            if (storageCandidate == null && blockEntity instanceof EnergyApi.BlockProvider provider) {
                storageCandidate = provider.getEnergyStorage(null);
            }
            
            if (storageCandidate instanceof DynamicEnergyStorage dynamicStorage) {
                var inserted = dynamicStorage.insertIgnoringLimit(energyUsed, false);
                if (inserted > 0)
                    dynamicStorage.update();
                
                return;
            } else if (storageCandidate != null) {
                var inserted = storageCandidate.insert(energyUsed, false);
                if (inserted > 0)
                    storageCandidate.update();
                
                return;
            }
        }
        
        var currentInvestedEnergy = stats.getRight();
        var requiredBreakingEnergy = (int) (Math.sqrt(blockState.getHardness(world, blockPos)) * BLOCK_BREAK_ENERGY / MINING_SPEED_MULTIPLIER);
        var efficiencyLevel = getEnchantmentLevel(tool, Enchantments.EFFICIENCY);
        if (efficiencyLevel > 0) requiredBreakingEnergy = requiredBreakingEnergy / (efficiencyLevel + 1);
        if (currentInvestedEnergy > requiredBreakingEnergy) {
            stats = new Pair<>(blockPos, 0);
            finishBlockBreaking(blockPos, blockState, world, player, tool);
        }
        
        blockBreakStats.put(player, stats);
    }
    
    private static void finishBlockBreaking(BlockPos targetPos, BlockState targetBlockState, World world, PlayerEntity player, ItemStack tool) {
        
        var targetEntity = world.getBlockEntity(targetPos);
        List<ItemStack> dropped;
        dropped = Block.getDroppedStacks(targetBlockState, (ServerWorld) world, targetPos, targetEntity, player, tool);
        
        var blockRecipe = LaserArmBlockEntity.tryGetLaserRecipe(targetBlockState, world);
        if (blockRecipe != null) {
            var recipe = blockRecipe.value();
            var farmedCount = 1;
            dropped = List.of(new ItemStack(recipe.getResults().get(0).getItem(), farmedCount));
            ParticleContent.CHARGING.spawn(world, Vec3d.of(targetPos), 1);
        }
        
        // add stack to player inv, or spawn at block pos
        for (var stack : dropped) {
            if (!player.getInventory().insertStack(stack))
                world.spawnEntity(new ItemEntity(world, targetPos.toCenterPos().x, targetPos.toCenterPos().y, targetPos.toCenterPos().z, stack));
        }
        
        try {
            targetBlockState.getBlock().onBreak(world, targetPos, targetBlockState, player);
        } catch (Exception exception) {
            Oritech.LOGGER.warn("Laser arm block break event failure when breaking " + targetBlockState + " at " + targetPos + ": " + exception.getLocalizedMessage());
        }
        world.addBlockBreakParticles(targetPos, world.getBlockState(targetPos));
        world.playSound(null, targetPos, targetBlockState.getSoundGroup().getBreakSound(), SoundCategory.BLOCKS, 1f, 1f);
        world.breakBlock(targetPos, false);
    }
    
    private static void processEntityTarget(PlayerEntity player, LivingEntity target, int damage, ItemStack tool, World world) {
        
        // make creepers charged
        if (target.getType().equals(EntityType.CREEPER) && !target.getDataTracker().get(CreeperEntity.CHARGED)) {
            target.getDataTracker().set(CreeperEntity.CHARGED, true);
            return;
        }
        
        var sharpnessLevel = getEnchantmentLevel(tool, Enchantments.SHARPNESS);
        damage = (int) (damage * Math.sqrt(sharpnessLevel + 1));
        
        target.damage(
          new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(DamageTypes.LIGHTNING_BOLT), player),
          damage);
        
    }
    
    // A hack to do this without context of the DRM
    public static int getEnchantmentLevel(ItemStack stack, RegistryKey<Enchantment> enchantment) {
        var enchantments = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        for (var entry : enchantments.getEnchantments()) {
            if (entry.getKey().isPresent() && entry.getKey().get().equals(enchantment)) {
                return enchantments.getLevel(entry);
            }
        }
        return 0;
    }
    
    // this overrides the fabric specific extensions
    public boolean allowComponentsUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack, ItemStack newStack) {
        return false;
    }
    
    public boolean allowContinuingBlockBreaking(PlayerEntity player, ItemStack oldStack, ItemStack newStack) {
        return true;
    }
    
    // this overrides the neoforge specific extensions
    public boolean shouldCauseReequipAnimation(@NotNull ItemStack oldStack, @NotNull ItemStack newStack, boolean slotChanged) {
        return false;
    }
    
    public boolean shouldCauseBlockBreakReset(@NotNull ItemStack oldStack, @NotNull ItemStack newStack) {
        return false;
    }
    
    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        var storedEnergy = TooltipHelper.getEnergyText(this.getStoredEnergy(stack));
        var capacity = TooltipHelper.getEnergyText(this.getEnergyCapacity(stack));
        var text = Text.translatable("tooltip.oritech.energy_indicator", storedEnergy, capacity);
        tooltip.add(text.formatted(Formatting.GOLD));
    }
    
    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }
    
    @Override
    public int getEnchantability() {
        return 10;
    }
    
    @Override
    public int getItemBarStep(ItemStack stack) {
        return Math.round((getStoredEnergy(stack) * 100f / this.getEnergyCapacity(stack)) * BAR_STEP_COUNT) / 100;
    }
    
    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        return true;
    }
    
    @Override
    public int getItemBarColor(ItemStack stack) {
        return 0xff7007;
    }
    
    @Override
    public long getEnergyCapacity(ItemStack stack) {
        return 5_000_000L;  // todo config settings
    }
    
    @Override
    public long getEnergyMaxInput(ItemStack stack) {
        return 500_000L;
    }
    
    @Override
    public long getEnergyMaxOutput(ItemStack stack) {
        return 500_000L;
    }
    
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(
          this,
          "laser",
          0,
          state -> {
              if (state.getController().getAnimationState().equals(AnimationController.State.STOPPED))
                  return state.setAndContinue(IDLE);
              return PlayState.CONTINUE;
          })
                          .triggerableAnim("idle", IDLE)
                          .triggerableAnim("singleshot", SINGLE_SHOT)
                          .triggerableAnim("shooting", SHOOTING));
    }
    
    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private PromethiumToolRenderer renderer;
            
            @Override
            public @NotNull BuiltinModelItemRenderer getGeoItemRenderer() {
                if (this.renderer == null)
                    this.renderer = new PromethiumToolRenderer("portable_laser", true);
                return renderer;
            }
        });
    }
    
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
