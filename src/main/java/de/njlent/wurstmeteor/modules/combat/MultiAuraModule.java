package de.njlent.wurstmeteor.modules.combat;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import de.njlent.wurstmeteor.util.RotationPackets;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class MultiAuraModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum attack distance.")
        .defaultValue(5.0)
        .min(0.0)
        .sliderMax(6.0)
        .build()
    );

    private final Setting<Double> wallsRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("walls-range")
        .description("Maximum range through walls.")
        .defaultValue(3.0)
        .min(0.0)
        .sliderMax(6.0)
        .build()
    );

    private final Setting<Double> attacksPerSecond = sgGeneral.add(new DoubleSetting.Builder()
        .name("attacks-per-second")
        .description("How often MultiAura runs attack sweeps.")
        .defaultValue(8.0)
        .min(1.0)
        .sliderRange(1.0, 20.0)
        .build()
    );

    private final Setting<Double> fov = sgGeneral.add(new DoubleSetting.Builder()
        .name("fov")
        .description("Field of view used for target filtering.")
        .defaultValue(360.0)
        .min(1.0)
        .sliderRange(30.0, 360.0)
        .build()
    );

    private final Setting<Integer> maxTargets = sgGeneral.add(new IntSetting.Builder()
        .name("max-targets")
        .description("Maximum entities attacked in one sweep.")
        .defaultValue(5)
        .min(1)
        .sliderRange(1, 16)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Sends look packets to each target before attacking.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swings your hand after each attack sweep.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseInContainers = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-in-containers")
        .description("Pauses while container screens are open.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnUse = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-on-use")
        .description("Pauses while eating, drinking, or breaking blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Never attacks friends.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Set<EntityType<?>>> entities = sgTargeting.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entity types to attack.")
        .defaultValue(EntityType.PLAYER)
        .onlyAttackable()
        .build()
    );

    private long nextAttackTime;

    public MultiAuraModule() {
        super(WurstMeteorAddon.CATEGORY, "multi-aura", "Attacks multiple targets in range each attack cycle.");
    }

    @Override
    public void onActivate() {
        nextAttackTime = 0L;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (pauseInContainers.get() && mc.screen instanceof AbstractContainerScreen<?>) return;
        if (pauseOnUse.get() && (mc.player.isUsingItem() || mc.gameMode.isDestroying())) return;

        long now = System.currentTimeMillis();
        long delayMs = Math.max(1L, (long) (1000.0 / attacksPerSecond.get()));
        if (now < nextAttackTime) return;

        List<Entity> targets = collectTargets();
        if (targets.isEmpty()) return;

        int attacks = Math.min(maxTargets.get(), targets.size());
        for (int i = 0; i < attacks; i++) {
            Entity target = targets.get(i);
            if (rotate.get()) RotationPackets.face(target.getBoundingBox().getCenter());
            mc.gameMode.attack(mc.player, target);
        }

        if (swing.get()) mc.player.swing(InteractionHand.MAIN_HAND);
        nextAttackTime = now + delayMs;
    }

    private List<Entity> collectTargets() {
        double rangeSq = range.get() * range.get();
        double wallsRangeSq = wallsRange.get() * wallsRange.get();

        List<Entity> targets = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity livingEntity)) continue;
            if (!livingEntity.isAlive()) continue;
            if (entity == mc.player) continue;
            if (!entities.get().contains(entity.getType())) continue;
            if (ignoreFriends.get() && entity instanceof Player player && Friends.get().isFriend(player)) continue;

            double distanceSq = mc.player.distanceToSqr(entity);
            if (distanceSq > rangeSq) continue;

            if (!PlayerUtils.canSeeEntity(entity) && distanceSq > wallsRangeSq) continue;
            if (!isInsideFov(entity)) continue;

            targets.add(entity);
        }

        targets.sort(Comparator.comparingDouble(mc.player::distanceToSqr));
        return targets;
    }

    private boolean isInsideFov(Entity entity) {
        double fovValue = fov.get();
        if (fovValue >= 360.0) return true;

        Vec3 look = mc.player.getLookAngle().normalize();
        Vec3 toTarget = entity.getBoundingBox().getCenter().subtract(mc.player.getEyePosition()).normalize();

        double dot = look.dot(toTarget);
        dot = Math.max(-1.0, Math.min(1.0, dot));
        double angle = Math.toDegrees(Math.acos(dot));

        return angle <= fovValue * 0.5;
    }
}
