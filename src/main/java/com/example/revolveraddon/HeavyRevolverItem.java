package com.example.revolveraddon;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Крупнокалиберный револьвер.
 * ПКМ — выстрел, Shift+ПКМ — перезарядка (патроны берутся из инвентаря).
 */
public class HeavyRevolverItem extends Item {
    public static final int CAPACITY = 6;
    public static final double RANGE = 64.0;
    public static final float DAMAGE = 14.0f;      // 7 сердец
    public static final int FIRE_COOLDOWN = 25;    // тиков
    public static final int RELOAD_COOLDOWN = 40;

    public HeavyRevolverItem(Properties props) {
        super(props);
    }

    private static int rounds(ItemStack s) {
        return s.getOrDefault(RevolverAddon.ROUNDS.get(), 0);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) {
            return reload(level, player, stack);
        }
        if (rounds(stack) <= 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.8f, 1.5f);
            player.getCooldowns().addCooldown(this, 8);
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide) {
            fire((ServerLevel) level, player, stack, hand);
        }
        player.getCooldowns().addCooldown(this, FIRE_COOLDOWN);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private void fire(ServerLevel level, Player player, ItemStack stack, InteractionHand hand) {
        stack.set(RevolverAddon.ROUNDS.get(), rounds(stack) - 1);

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(RANGE));

        BlockHitResult blockHit = level.clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double maxSqr = blockHit.getType() == HitResult.Type.MISS
                ? RANGE * RANGE : eye.distanceToSqr(blockHit.getLocation());

        AABB box = player.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
                e -> !e.isSpectator() && e.isPickable(), maxSqr);

        Vec3 impact = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
        if (entityHit != null) {
            impact = entityHit.getLocation();
            entityHit.getEntity().hurt(level.damageSources().playerAttack(player), DAMAGE);
        }

        // трассер и дым
        double dist = eye.distanceTo(impact);
        for (double d = 1.5; d < dist; d += 1.0) {
            Vec3 p = eye.add(look.scale(d));
            level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        Vec3 muzzle = eye.add(look.scale(0.8));
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y - 0.1, muzzle.z, 8, 0.05, 0.05, 0.05, 0.02);
        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y - 0.1, muzzle.z, 2, 0.02, 0.02, 0.02, 0.01);

        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS, 1.2f, 0.6f);

        stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
    }

    private InteractionResultHolder<ItemStack> reload(Level level, Player player, ItemStack stack) {
        int loaded = rounds(stack);
        int need = CAPACITY - loaded;
        if (need <= 0) return InteractionResultHolder.pass(stack);

        int taken = 0;
        if (player.getAbilities().instabuild) {
            taken = need;
        } else {
            for (ItemStack s : player.getInventory().items) {
                if (taken >= need) break;
                if (s.is(RevolverAddon.HEAVY_ROUND.get())) {
                    int t = Math.min(need - taken, s.getCount());
                    s.shrink(t);
                    taken += t;
                }
            }
        }
        if (taken == 0) return InteractionResultHolder.fail(stack);

        if (!level.isClientSide) {
            stack.set(RevolverAddon.ROUNDS.get(), loaded + taken);
            level.playSound(null, player.blockPosition(), SoundEvents.IRON_DOOR_CLOSE,
                    SoundSource.PLAYERS, 1.0f, 1.4f);
        }
        player.getCooldowns().addCooldown(this, RELOAD_COOLDOWN);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip,
                                net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.revolver_addon.rounds", rounds(stack), CAPACITY));
        tooltip.add(Component.translatable("tooltip.revolver_addon.hint"));
    }
}
