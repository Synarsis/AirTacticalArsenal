package com.synarsis.airtacticalarsenal.item;

import com.synarsis.airtacticalarsenal.client.renderer.item.SprayCanItemRenderer;
import com.synarsis.airtacticalarsenal.entity.PaintStandEntity;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

public class SprayCanItem extends Item implements GeoItem {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public static final int MAX_CHARGE = 300; 
    private static final float FREE_SPRAY_COST = 0.5f; 

    public SprayCanItem(Properties properties) {
        super(properties);
    }

    public static int getCharge(ItemStack stack) {
        if (stack.hasTag()) {
            return (int) stack.getTag().getFloat("SprayCharge");
        }
        return 0;
    }

    public static float getChargeFloat(ItemStack stack) {
        if (stack.hasTag()) {
            return stack.getTag().getFloat("SprayCharge");
        }
        return 0;
    }

    public static void setCharge(ItemStack stack, int charge) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putFloat("SprayCharge", Math.max(0, Math.min(MAX_CHARGE, charge)));
    }

    public static void setChargeFloat(ItemStack stack, float charge) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putFloat("SprayCharge", Math.max(0, Math.min(MAX_CHARGE, charge)));
    }

    public static void consumeCharge(ItemStack stack, float amount) {
        float current = getChargeFloat(stack);
        setChargeFloat(stack, Math.max(0, current - amount));
    }

    public static void addCharge(ItemStack stack, int amount) {
        float current = getChargeFloat(stack);
        setChargeFloat(stack, Math.min(MAX_CHARGE, current + amount));
    }

    public static boolean hasCharge(ItemStack stack) {
        return getCharge(stack) > 0;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getCharge(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round((float) getCharge(stack) / MAX_CHARGE * 13.0f);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float ratio = (float) getCharge(stack) / MAX_CHARGE;

        int r = (int) ((1.0f - ratio) * 255);
        int g = (int) (ratio * 255);
        return (r << 16) | (g << 8);
    }

    public static boolean hasWater(ItemStack stack) {
        if (stack.hasTag()) {
            return stack.getTag().getBoolean("HasWater");
        }
        return false;
    }

    public static void setHasWater(ItemStack stack, boolean hasWater) {
        stack.getOrCreateTag().putBoolean("HasWater", hasWater);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        if (player == null) return InteractionResult.PASS;

        if (level.getFluidState(pos).isSource() && level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)) {
            if (hasWater(stack)) {

                if (level.isClientSide) {
                    player.displayClientMessage(Component.literal("§7Вода уже залита"), true);
                }
                return InteractionResult.FAIL;
            }

            if (!level.isClientSide) {
                setHasWater(stack, true);
                level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
            }

            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return InteractionResult.PASS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE; 
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged; 
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000; 
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        HitResult hitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockPos waterPos = ((net.minecraft.world.phys.BlockHitResult)hitResult).getBlockPos();
            if (level.getFluidState(waterPos).is(net.minecraft.tags.FluidTags.WATER) && level.getFluidState(waterPos).isSource()) {
                if (!hasWater(stack)) {
                    if (!level.isClientSide) {
                        setHasWater(stack, true);
                        level.playSound(null, waterPos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
                    }
                    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
                } else {
                    if (level.isClientSide) {
                        player.displayClientMessage(Component.literal("§7Вода уже залита"), true);
                    }
                    return InteractionResultHolder.pass(stack);
                }
            }
        }

        if (!hasCharge(stack)) {

            level.playSound(player, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.5f, 1.5f);
            return InteractionResultHolder.fail(stack);
        }

        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (!(entity instanceof Player player)) return;

        if (!hasCharge(stack)) {
            player.stopUsingItem();
            return;
        }

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.scale(5.0)); 

        PaintStandEntity targetStand = findPaintStand(level, player, eyePos, endPos);

        if (targetStand != null && targetStand.hasShahed()) {

            if (!level.isClientSide) {
                boolean continued = targetStand.applyPaint(player, stack);
                if (!continued) {
                    player.stopUsingItem();
                    return;
                }
            }

            spawnPaintParticles(level, targetStand.position().add(0, 1.0, 0), true);

            if (remainingUseDuration % 5 == 0) {
                level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.3f, 1.2f);
            }
        } else {

            if (!level.isClientSide) {
                consumeCharge(stack, FREE_SPRAY_COST);
            }

            Vec3 particlePos = eyePos.add(lookVec.scale(1.5));
            spawnPaintParticles(level, particlePos, false);

            if (remainingUseDuration % 8 == 0) {
                level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.15f, 1.5f);
            }
        }
    }

    private PaintStandEntity findPaintStand(Level level, Player player, Vec3 start, Vec3 end) {
        AABB searchBox = new AABB(start, end).inflate(1.0);
        List<PaintStandEntity> stands = level.getEntitiesOfClass(PaintStandEntity.class, searchBox, 
            stand -> stand.isAlive());

        PaintStandEntity closest = null;
        double closestDist = Double.MAX_VALUE;

        for (PaintStandEntity stand : stands) {

            AABB standBox = stand.getBoundingBox().inflate(0.5);
            if (standBox.clip(start, end).isPresent()) {
                double dist = start.distanceToSqr(stand.position());
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = stand;
                }
            }
        }

        return closest;
    }

    private void spawnPaintParticles(Level level, Vec3 pos, boolean isPainting) {
        if (!level.isClientSide) return;

        DustParticleOptions blackDust = new DustParticleOptions(new Vector3f(0.1f, 0.1f, 0.1f), 1.0f);

        int count = isPainting ? 5 : 2;
        for (int i = 0; i < count; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * 0.5;
            double offsetY = (level.random.nextDouble() - 0.5) * 0.5;
            double offsetZ = (level.random.nextDouble() - 0.5) * 0.5;

            level.addParticle(blackDust, 
                pos.x + offsetX, 
                pos.y + offsetY, 
                pos.z + offsetZ, 
                0, 0, 0);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int charge = getCharge(stack);

        if (charge > 0) {
            tooltip.add(Component.literal("§7Заряд: §f" + charge + "§7/" + MAX_CHARGE));
        } else {
            tooltip.add(Component.literal("§cПусто"));
        }

        if (hasWater(stack)) {
            tooltip.add(Component.literal("§9💧 Вода залита"));
        } else {
            tooltip.add(Component.literal("§8💧 Нужна вода"));
        }

        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§7Зажать ПКМ - распылять"));
        tooltip.add(Component.literal("§7Направить на стойку - покраска"));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§eЗаправка:"));
        tooltip.add(Component.literal("§71. ПКМ по воде - залить воду"));
        tooltip.add(Component.literal("§72. Крафт + §fчёрный краситель§7 (1-10)"));
        tooltip.add(Component.literal("§7   1 краситель = 10% (30 ед.)"));

        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private SprayCanItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) {
                    this.renderer = new SprayCanItemRenderer();
                }
                return this.renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "spray_controller", 0, state -> {

            return state.setAndContinue(RawAnimation.begin().thenLoop("idle"));
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
