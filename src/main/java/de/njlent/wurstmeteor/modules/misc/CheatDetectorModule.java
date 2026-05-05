package de.njlent.wurstmeteor.modules.misc;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CheatDetectorModule extends Module {
    private static final int ALERT_COOLDOWN_TICKS = 200;
    private static final int AURA_WINDOW_TICKS = 40;
    private static final double TELEPORT_THRESHOLD = 6.0;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> speed = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-speed")
        .description("Warns when another player keeps moving above the speed threshold.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> speedThreshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed-threshold")
        .description("Allowed horizontal speed in blocks per second.")
        .defaultValue(10.0)
        .min(1.0)
        .sliderRange(4.0, 40.0)
        .build()
    );

    private final Setting<Integer> speedBurst = sgGeneral.add(new IntSetting.Builder()
        .name("speed-burst")
        .description("Consecutive speed violations before warning.")
        .defaultValue(12)
        .range(1, 80)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<Boolean> flight = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-flight")
        .description("Warns when another player stays airborne above ground.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> flightTicks = sgGeneral.add(new IntSetting.Builder()
        .name("flight-ticks")
        .description("Airborne ticks before warning.")
        .defaultValue(40)
        .range(10, 200)
        .sliderRange(10, 100)
        .build()
    );

    private final Setting<Double> flightClearance = sgGeneral.add(new DoubleSetting.Builder()
        .name("flight-clearance")
        .description("Minimum distance above support blocks.")
        .defaultValue(6.0)
        .min(1.0)
        .sliderRange(2.0, 24.0)
        .build()
    );

    private final Setting<Boolean> boatFly = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-boat-fly")
        .description("Warns when a boat stays unsupported in the air.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> boatTicks = sgGeneral.add(new IntSetting.Builder()
        .name("boat-air-ticks")
        .description("Unsupported boat ticks before warning.")
        .defaultValue(25)
        .range(5, 200)
        .sliderRange(5, 100)
        .build()
    );

    private final Setting<Boolean> aura = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-killaura")
        .description("Warns when another player swings faster than the aura threshold.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> auraThreshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("aura-threshold")
        .description("Swing threshold per second.")
        .defaultValue(12.0)
        .min(2.0)
        .sliderRange(4.0, 30.0)
        .build()
    );

    private final Map<UUID, Stats> stats = new HashMap<>();
    private long ticks;

    public CheatDetectorModule() {
        super(WurstMeteorAddon.CATEGORY, "cheat-detector", "Flags suspicious movement and combat patterns from nearby players.");
    }

    @Override
    public void onActivate() {
        stats.clear();
        ticks = 0;
    }

    @Override
    public void onDeactivate() {
        stats.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) {
            stats.clear();
            return;
        }

        ticks++;
        Set<UUID> seen = new HashSet<>();

        for (Player player : mc.level.players()) {
            if (player == mc.player || player.isRemoved()) continue;
            UUID uuid = player.getUUID();
            seen.add(uuid);
            Stats playerStats = stats.computeIfAbsent(uuid, ignored -> new Stats());
            process(player, playerStats);
        }

        stats.keySet().removeIf(uuid -> !seen.contains(uuid));
    }

    private void process(Player player, Stats s) {
        if (player.isSpectator() || player.isCreative()) {
            s.reset(player);
            return;
        }

        if (!s.initialized) {
            s.reset(player);
            return;
        }

        double dx = player.getX() - s.x;
        double dy = player.getY() - s.y;
        double dz = player.getZ() - s.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double blocksPerSecond = horizontal * 20.0;

        if (horizontal > TELEPORT_THRESHOLD || Math.abs(dy) > TELEPORT_THRESHOLD) s.ignoreTicks = 5;
        else if (s.ignoreTicks > 0) s.ignoreTicks--;

        if (s.ignoreTicks == 0) checkSpeed(player, s, blocksPerSecond);
        checkFlight(player, s);
        checkBoatFly(player, s);
        checkAura(player, s);

        s.x = player.getX();
        s.y = player.getY();
        s.z = player.getZ();
    }

    private void checkSpeed(Player player, Stats s, double speedNow) {
        if (!speed.get() || player.isInWater() || player.isSwimming()) return;

        double allowed = speedThreshold.get();
        if (isUsingElytra(player)) allowed *= 2.0;
        if (player.getVehicle() != null) allowed *= 2.0;
        if (player.hasEffect(MobEffects.SPEED)) allowed *= 1.35;

        if (speedNow <= allowed) {
            s.speedViolations = 0;
            return;
        }

        s.speedViolations++;
        if (s.speedViolations < speedBurst.get()) return;
        if (ticks - s.lastSpeedAlert < ALERT_COOLDOWN_TICKS) return;

        s.lastSpeedAlert = ticks;
        s.speedViolations = 0;
        info("%s may be speed hacking: %.1f b/s.", player.getName().getString(), speedNow);
    }

    private void checkFlight(Player player, Stats s) {
        if (!flight.get() || isUsingElytra(player)) {
            s.airTicks = 0;
            return;
        }

        boolean airborne = !player.onGround() && !player.isInWater() && player.getVehicle() == null
            && !player.onClimbable() && !player.hasEffect(MobEffects.SLOW_FALLING) && !player.hasEffect(MobEffects.LEVITATION);

        if (!airborne) {
            s.airTicks = 0;
            return;
        }

        s.airTicks++;
        if (s.airTicks < flightTicks.get()) return;
        if (clearance(player, 16) < flightClearance.get()) return;
        if (ticks - s.lastFlightAlert < ALERT_COOLDOWN_TICKS) return;

        s.lastFlightAlert = ticks;
        info("%s may be flying.", player.getName().getString());
    }

    private void checkBoatFly(Player player, Stats s) {
        if (!boatFly.get() || !(player.getVehicle() instanceof Boat boat)) {
            s.boatAirTicks = 0;
            return;
        }

        if (isBoatSupported(boat)) {
            s.boatAirTicks = 0;
            return;
        }

        s.boatAirTicks++;
        if (s.boatAirTicks < boatTicks.get()) return;
        if (ticks - s.lastBoatAlert < ALERT_COOLDOWN_TICKS) return;

        s.lastBoatAlert = ticks;
        info("%s may be boat flying.", player.getName().getString());
    }

    private void checkAura(Player player, Stats s) {
        if (!aura.get() || isUsingElytra(player)) {
            s.swingTicks.clear();
            s.lastSwingProgress = player.getAttackAnim(1.0F);
            return;
        }

        float progress = player.getAttackAnim(1.0F);
        if (s.lastSwingProgress > 0.6F && progress < 0.2F) s.swingTicks.addLast(ticks);
        s.lastSwingProgress = progress;

        while (!s.swingTicks.isEmpty() && ticks - s.swingTicks.peekFirst() > AURA_WINDOW_TICKS) s.swingTicks.removeFirst();
        if (s.swingTicks.size() < 2) return;

        long span = Math.max(1, s.swingTicks.peekLast() - s.swingTicks.peekFirst());
        double swingsPerSecond = (s.swingTicks.size() - 1) * 20.0 / span;
        if (swingsPerSecond <= auraThreshold.get()) return;
        if (ticks - s.lastAuraAlert < ALERT_COOLDOWN_TICKS) return;

        s.lastAuraAlert = ticks;
        info("%s may be using killaura: %.1f swings/s.", player.getName().getString(), swingsPerSecond);
    }

    private double clearance(Entity entity, int maxDepth) {
        if (mc.level == null) return 0.0;
        AABB box = entity.getBoundingBox();
        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.ceil(box.maxX);
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.ceil(box.maxZ);
        int maxY = (int) Math.floor(box.minY);
        int minY = Math.max(mc.level.getMinY(), maxY - maxDepth);

        for (int y = maxY; y >= minY; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = mc.level.getBlockState(pos);
                    FluidState fluid = mc.level.getFluidState(pos);
                    if (!state.getCollisionShape(mc.level, pos).isEmpty() || (!fluid.isEmpty() && (fluid.is(FluidTags.WATER) || fluid.is(FluidTags.LAVA)))) {
                        return Math.max(0.0, box.minY - (y + 1.0));
                    }
                }
            }
        }

        return maxDepth;
    }

    private boolean isBoatSupported(Boat boat) {
        if (mc.level == null) return false;
        AABB box = boat.getBoundingBox().move(0.0, -0.15, 0.0);
        for (int x = (int) Math.floor(box.minX); x <= (int) Math.floor(box.maxX); x++) {
            for (int y = (int) Math.floor(box.minY); y <= (int) Math.floor(box.maxY); y++) {
                for (int z = (int) Math.floor(box.minZ); z <= (int) Math.floor(box.maxZ); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = mc.level.getBlockState(pos);
                    FluidState fluid = mc.level.getFluidState(pos);
                    if (!state.getCollisionShape(mc.level, pos).isEmpty() || (!fluid.isEmpty() && fluid.is(FluidTags.WATER))) return true;
                }
            }
        }

        return false;
    }

    private boolean isUsingElytra(Player player) {
        if (player.isFallFlying()) return true;
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        return !player.onGround() && !chest.isEmpty() && chest.is(Items.ELYTRA);
    }

    private static class Stats {
        double x;
        double y;
        double z;
        boolean initialized;
        int ignoreTicks;
        int speedViolations;
        int airTicks;
        int boatAirTicks;
        float lastSwingProgress;
        long lastSpeedAlert;
        long lastFlightAlert;
        long lastBoatAlert;
        long lastAuraAlert;
        final ArrayDeque<Long> swingTicks = new ArrayDeque<>();

        void reset(Player player) {
            x = player.getX();
            y = player.getY();
            z = player.getZ();
            initialized = true;
            ignoreTicks = 0;
            speedViolations = 0;
            airTicks = 0;
            boatAirTicks = 0;
            swingTicks.clear();
            lastSwingProgress = player.getAttackAnim(1.0F);
        }
    }
}
