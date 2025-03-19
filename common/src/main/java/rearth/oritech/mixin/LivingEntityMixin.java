package rearth.oritech.mixin;


import net.minecraft.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rearth.oritech.block.entity.interaction.LaserArmBlockEntity;


@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity implements Attackable {
    
    public LivingEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }
    
    @Override
    public ItemEntity dropStack(ItemStack stack) {
        return dropStack(stack, 0.0F);
    }
    
    @SuppressWarnings("resource")
    @Override
    public ItemEntity dropStack(ItemStack stack, float yOffset) {
        LivingEntity thisEntity = (LivingEntity) (Object) this;
        LivingEntity attacker = thisEntity.getLastAttacker();
        
        if (stack.isEmpty() || thisEntity.getWorld().isClient) return null;
        
        if (!thisEntity.isAlive() && oritech$isLaser(attacker)) {
            ((PlayerEntity)attacker).giveItemStack(stack);
            return null;
        }
        return super.dropStack(stack, yOffset);
    }
    
    @Inject(method = "dropXp(Lnet/minecraft/entity/Entity;)V", at = @At(value = "HEAD"), cancellable = true)
    private void disableXpForLaser(Entity attacker, CallbackInfo ci) {
        if (oritech$isLaser(attacker))
            ci.cancel();
    }
    
    @Unique
    private boolean oritech$isLaser(Entity attacker) {
        return attacker instanceof PlayerEntity player && player.getGameProfile().getName().equals(LaserArmBlockEntity.LASER_PLAYER_NAME);
    }
}