package de.njlent.wurstmeteor.modules.combat;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public class SpearAssistModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<BoostMode> boostMode = sgGeneral.add(new EnumSetting.Builder<BoostMode>()
        .name("boost-mode")
        .description("How attack while charging boosts movement.")
        .defaultValue(BoostMode.HoldAndDash)
        .build()
    );

    private final Setting<Double> boostSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("boost-speed")
        .description("Boost speed in blocks per tick.")
        .defaultValue(0.35)
        .min(0.0)
        .sliderRange(0.0, 2.0)
        .build()
    );

    private final Setting<Double> dashDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("dash-distance")
        .description("Extra distance applied when attack starts.")
        .defaultValue(3.0)
        .min(0.0)
        .sliderRange(0.0, 10.0)
        .build()
    );

    private final Setting<Boolean> allowReverse = sgGeneral.add(new BoolSetting.Builder()
        .name("allow-reverse")
        .description("Boosts backwards while the back key is held.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> stayGrounded = sgGeneral.add(new BoolSetting.Builder()
        .name("stay-grounded")
        .description("Drops vertical boost motion.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoResumeCharge = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-resume-charge")
        .description("Keeps using the spear while the use key is held.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoAttackWhenReady = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-attack-when-ready")
        .description("Attacks the looked-at entity when the attack cooldown is full.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> nearRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("near-highlight-range")
        .description("Range for near target highlights.")
        .defaultValue(7.0)
        .min(1.0)
        .sliderRange(1.0, 16.0)
        .build()
    );

    private final Setting<Double> farRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("far-highlight-range")
        .description("Range for far target highlights.")
        .defaultValue(12.0)
        .min(1.0)
        .sliderRange(4.0, 32.0)
        .build()
    );

    private final Setting<SettingColor> nearColor = sgGeneral.add(new ColorSetting.Builder()
        .name("near-color")
        .description("Highlight color for close spear targets.")
        .defaultValue(new SettingColor(255, 230, 40, 90))
        .build()
    );

    private final Setting<SettingColor> farColor = sgGeneral.add(new ColorSetting.Builder()
        .name("far-color")
        .description("Highlight color for far spear targets.")
        .defaultValue(new SettingColor(70, 255, 90, 80))
        .build()
    );

    private boolean attackWasDown;
    private boolean autoAttackPrimed;
    private double dashRemaining;
    private boolean reverseDash;

    public SpearAssistModule() {
        super(WurstMeteorAddon.CATEGORY, "spear-assist", "Adds boost, charge, highlight, and attack helpers for spear-like modded weapons.");
    }

    @Override
    public void onDeactivate() {
        attackWasDown = false;
        autoAttackPrimed = false;
        dashRemaining = 0.0;
        reverseDash = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null || mc.options == null) return;

        boolean holdingSpear = isSpear(mc.player.getMainHandItem());
        if (!holdingSpear) {
            attackWasDown = false;
            autoAttackPrimed = false;
            dashRemaining = 0.0;
            return;
        }

        if (autoResumeCharge.get() && mc.options.keyUse.isDown() && !mc.player.isUsingItem()) {
            InteractionResult result = mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            if (result.consumesAction()) mc.player.swing(InteractionHand.MAIN_HAND);
        }

        boolean charging = mc.player.isUsingItem() && isSpear(mc.player.getUseItem());
        boolean attackDown = mc.options.keyAttack.isDown();
        boolean attackPressed = attackDown && !attackWasDown;

        if (charging) {
            Vec3 direction = boostDirection();
            if (attackPressed && boostMode.get().dash) {
                dashRemaining = dashDistance.get();
                reverseDash = allowReverse.get() && mc.options.keyDown.isDown();
            }

            if (attackDown && boostMode.get().hold) applyBoost(direction, boostSpeed.get());
            if (dashRemaining > 0.0 && boostMode.get().dash) {
                Vec3 dashDirection = reverseDash ? direction.reverse() : direction;
                double step = Math.min(boostSpeed.get(), dashRemaining);
                applyBoost(dashDirection, step);
                dashRemaining -= step;
            }
        } else {
            dashRemaining = 0.0;
        }

        handleAutoAttack(holdingSpear, attackDown);
        attackWasDown = attackDown;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null || !isSpear(mc.player.getMainHandItem())) return;

        double farSq = farRange.get() * farRange.get();
        double nearSq = nearRange.get() * nearRange.get();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || living == mc.player || !living.isAlive()) continue;
            double distanceSq = mc.player.distanceToSqr(living);
            if (distanceSq > farSq) continue;

            SettingColor settingColor = distanceSq <= nearSq ? nearColor.get() : farColor.get();
            Color side = new Color(settingColor);
            Color line = new Color(settingColor).a(220);
            event.renderer.box(interpolatedBox(living, event.tickDelta).inflate(0.1), side, line, ShapeMode.Both, 0);
        }
    }

    private void handleAutoAttack(boolean holdingSpear, boolean attackDown) {
        if (!autoAttackWhenReady.get() || !holdingSpear || !attackDown) {
            autoAttackPrimed = false;
            return;
        }

        float strength = mc.player.getAttackStrengthScale(0.0F);
        if (strength < 0.999F) {
            if (strength <= 0.1F) autoAttackPrimed = false;
            return;
        }

        if (autoAttackPrimed || !(mc.hitResult instanceof EntityHitResult hit)) return;

        mc.gameMode.attack(mc.player, hit.getEntity());
        mc.player.swing(InteractionHand.MAIN_HAND);
        autoAttackPrimed = true;
    }

    private Vec3 boostDirection() {
        Vec3 direction = mc.player.getLookAngle();
        if (allowReverse.get() && mc.options.keyDown.isDown()) direction = direction.reverse();
        if (stayGrounded.get()) direction = new Vec3(direction.x, 0.0, direction.z);
        if (direction.lengthSqr() < 1.0E-6) return Vec3.ZERO;
        return direction.normalize();
    }

    private void applyBoost(Vec3 direction, double speed) {
        if (speed <= 0.0 || direction.lengthSqr() < 1.0E-6) return;

        Vec3 motion = direction.scale(speed);
        if (stayGrounded.get()) motion = new Vec3(motion.x, 0.0, motion.z);
        mc.player.setDeltaMovement(mc.player.getDeltaMovement().add(motion));
        if (!stayGrounded.get()) mc.player.setOnGround(false);
        mc.player.fallDistance = 0.0F;
    }

    private boolean isSpear(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getPath().toLowerCase(Locale.ROOT).contains("spear");
    }

    private AABB interpolatedBox(Entity entity, float tickDelta) {
        double x = Mth.lerp(tickDelta, entity.xOld, entity.getX()) - entity.getX();
        double y = Mth.lerp(tickDelta, entity.yOld, entity.getY()) - entity.getY();
        double z = Mth.lerp(tickDelta, entity.zOld, entity.getZ()) - entity.getZ();
        return entity.getBoundingBox().move(x, y, z);
    }

    public enum BoostMode {
        HoldOnly(true, false),
        DashOnly(false, true),
        HoldAndDash(true, true);

        private final boolean hold;
        private final boolean dash;

        BoostMode(boolean hold, boolean dash) {
            this.hold = hold;
            this.dash = dash;
        }
    }
}
