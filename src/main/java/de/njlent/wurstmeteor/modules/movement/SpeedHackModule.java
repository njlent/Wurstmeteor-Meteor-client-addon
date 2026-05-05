package de.njlent.wurstmeteor.modules.movement;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.phys.Vec3;

public class SpeedHackModule extends Module {
    private static final double WALK_BPS = 4.317;
    private static final double SPRINT_BPS = 5.6112;
    private static final double SPEED_I_WALK_BPS = 5.180;
    private static final double SPEED_I_SPRINT_BPS = 6.734;
    private static final double SPEED_II_WALK_BPS = 6.044;
    private static final double SPEED_II_SPRINT_BPS = 7.857;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> maxSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-speed")
        .description("Maximum horizontal speed in blocks per tick.")
        .defaultValue(0.65)
        .range(0.1, 9.9)
        .sliderRange(0.1, 2.0)
        .build()
    );

    private final Setting<Double> groundAccel = sgGeneral.add(new DoubleSetting.Builder()
        .name("ground-accel")
        .description("How quickly speed approaches the target on ground.")
        .defaultValue(0.35)
        .range(0.01, 9.9)
        .sliderRange(0.01, 1.0)
        .build()
    );

    private final Setting<Double> airAccel = sgGeneral.add(new DoubleSetting.Builder()
        .name("air-accel")
        .description("How quickly speed approaches the target in air.")
        .defaultValue(0.02)
        .range(0.0, 5.0)
        .sliderRange(0.0, 0.5)
        .build()
    );

    private final Setting<Double> sidewaysDamping = sgGeneral.add(new DoubleSetting.Builder()
        .name("sideways-damping")
        .description("How much sideways inertia is kept while turning.")
        .defaultValue(0.15)
        .range(0.0, 1.0)
        .sliderRange(0.0, 1.0)
        .build()
    );

    private final Setting<Boolean> autoSprint = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-sprint")
        .description("Sprints automatically while moving forward.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoNormalise = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-normalise")
        .description("Compensates persistent slowdown to maintain vanilla walking speed.")
        .defaultValue(false)
        .build()
    );

    private final Setting<PotionMode> potionMode = sgGeneral.add(new EnumSetting.Builder<PotionMode>()
        .name("potion-mode")
        .description("Vanilla-like Speed effect presets with slowdown compensation.")
        .defaultValue(PotionMode.Off)
        .build()
    );

    private double currentBoost = 1.0;

    public SpeedHackModule() {
        super(WurstMeteorAddon.CATEGORY, "speed-hack", "Adjusts horizontal movement speed with CevAPI-style compensation modes.");
    }

    @Override
    public void onActivate() {
        currentBoost = 1.0;
    }

    @Override
    public void onDeactivate() {
        currentBoost = 1.0;
    }

    @Override
    public String getInfoString() {
        if (mc.player == null) return null;
        long bps = Math.round(horizontalSpeed(mc.player.getDeltaMovement()) * 20.0);
        return bps + "b/s";
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;
        if (mc.player.isShiftKeyDown() || (mc.player.zza == 0 && mc.player.xxa == 0)) return;
        if (mc.player.getAbilities().flying || mc.player.isInWater() || mc.player.isInLava()) return;

        boolean vanillaProfile = autoNormalise.get() || potionMode.get() != PotionMode.Off;
        if (!vanillaProfile && autoSprint.get() && mc.player.zza > 0 && !mc.player.horizontalCollision) mc.player.setSprinting(true);

        Vec3 direction = getMoveDir(mc.player.zza, mc.player.xxa);
        if (direction == Vec3.ZERO) return;

        Vec3 velocity = mc.player.getDeltaMovement();
        Vec3 horizontal = new Vec3(velocity.x, 0, velocity.z);
        double currentSpeed = horizontalSpeed(horizontal);

        double alongSpeed = horizontal.dot(direction);
        Vec3 along = direction.scale(alongSpeed);
        Vec3 side = horizontal.subtract(along);
        double damping = vanillaProfile ? 0.15 : sidewaysDamping.get();
        horizontal = along.add(side.scale(damping));

        double targetSpeed = vanillaProfile ? getVanillaSpeedPerTick() : maxSpeed.get();
        if (vanillaProfile) targetSpeed = updateCompensatedTarget(targetSpeed, currentSpeed);
        else currentBoost = 1.0;

        double accel = mc.player.onGround() ? (vanillaProfile ? 0.35 : groundAccel.get()) : (vanillaProfile ? 0.02 : airAccel.get());
        horizontal = horizontal.add(direction.scale(targetSpeed).subtract(horizontal).scale(accel));

        double speed = horizontalSpeed(horizontal);
        if (speed > targetSpeed) horizontal = horizontal.scale(targetSpeed / speed);

        mc.player.setDeltaMovement(horizontal.x, velocity.y, horizontal.z);
    }

    private Vec3 getMoveDir(float forward, float strafe) {
        double yaw = Math.toRadians(mc.player.getYRot());
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        double x = (-sin * forward) + (cos * strafe);
        double z = (cos * forward) + (sin * strafe);
        double length = Math.sqrt(x * x + z * z);
        return length < 1.0E-6 ? Vec3.ZERO : new Vec3(x / length, 0, z / length);
    }

    private double updateCompensatedTarget(double baseSpeed, double currentSpeed) {
        boolean candidate = mc.player.onGround() && !mc.player.horizontalCollision && mc.player.zza > 0 && !mc.player.isShiftKeyDown();
        double maxBoost = potionMode.get() == PotionMode.Off ? 2.0 : 4.0;

        if (candidate && currentSpeed > 0.08 && currentSpeed < baseSpeed * 0.8) currentBoost = Math.min(maxBoost, currentBoost + 0.05);
        else if (currentBoost > 1.0 && (!candidate || currentSpeed >= baseSpeed * 0.95)) currentBoost = Math.max(1.0, currentBoost - 0.02);

        return baseSpeed * currentBoost;
    }

    private double getVanillaSpeedPerTick() {
        boolean sprinting = mc.player.zza > 0 && (mc.options.keySprint.isDown() || mc.player.isSprinting());
        return switch (potionMode.get()) {
            case SpeedI -> (sprinting ? SPEED_I_SPRINT_BPS : SPEED_I_WALK_BPS) / 20.0;
            case SpeedII -> (sprinting ? SPEED_II_SPRINT_BPS : SPEED_II_WALK_BPS) / 20.0;
            case Off -> (sprinting ? SPRINT_BPS : WALK_BPS) / 20.0;
        };
    }

    private double horizontalSpeed(Vec3 velocity) {
        return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
    }

    public enum PotionMode {
        Off,
        SpeedI,
        SpeedII
    }
}
