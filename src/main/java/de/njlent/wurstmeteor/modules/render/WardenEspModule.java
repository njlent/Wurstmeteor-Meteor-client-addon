package de.njlent.wurstmeteor.modules.render;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.warden.AngerLevel;
import net.minecraft.world.entity.monster.warden.Warden;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

public class WardenEspModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How Warden boxes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Boolean> tracers = sgGeneral.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draws tracer lines to wardens.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> textOverlay = sgGeneral.add(new BoolSetting.Builder()
        .name("text-overlay")
        .description("Shows Warden state text above each Warden.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> overlayScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("overlay-scale")
        .description("Warden state overlay scale.")
        .defaultValue(1.1)
        .min(0.5)
        .sliderRange(0.5, 2.5)
        .build()
    );

    private final Setting<Boolean> showTarget = sgGeneral.add(new BoolSetting.Builder()
        .name("show-target")
        .description("Shows whether the Warden targets you or another entity.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showAnger = sgGeneral.add(new BoolSetting.Builder()
        .name("show-anger")
        .description("Shows the synced anger value.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showPose = sgGeneral.add(new BoolSetting.Builder()
        .name("show-pose")
        .description("Shows sniffing, emerging, digging, and attack poses.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Warden ESP color.")
        .defaultValue(new SettingColor(255, 65, 65, 120))
        .build()
    );

    private int count;

    public WardenEspModule() {
        super(WurstMeteorAddon.CATEGORY, "warden-esp", "Highlights nearby wardens.");
    }

    @Override
    public String getInfoString() {
        return count == 0 ? null : Integer.toString(count);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;

        count = 0;
        Color side = new Color(color.get());
        Color line = new Color(color.get()).a(220);

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity.getType() != EntityType.WARDEN) continue;
            count++;

            event.renderer.box(entity.getBoundingBox(), side, line, shapeMode.get(), 0);
            if (tracers.get() && RenderUtils.center != null) {
                var center = entity.getBoundingBox().getCenter();
                event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, center.x, center.y, center.z, line);
            }
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!textOverlay.get() || mc.player == null || mc.level == null) return;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Warden warden) || warden.isRemoved() || !warden.isAlive()) continue;

            List<Line> lines = stateLines(warden);
            if (lines.isEmpty()) continue;

            Vector3d pos = new Vector3d(warden.getX(), warden.getBoundingBox().maxY + 0.65, warden.getZ());
            if (!NametagUtils.to2D(pos, overlayScale.get())) continue;

            NametagUtils.begin(pos, event.graphics);
            TextRenderer renderer = TextRenderer.get();
            renderer.begin(1.0, false, true);

            double width = 0.0;
            for (Line line : lines) width = Math.max(width, renderer.getWidth(line.text()));

            double y = -renderer.getHeight() * lines.size() / 2.0;
            for (Line line : lines) {
                renderer.render(line.text(), -width / 2.0, y, line.color());
                y += renderer.getHeight();
            }

            renderer.end();
            NametagUtils.end(event.graphics);
        }
    }

    private List<Line> stateLines(Warden warden) {
        List<Line> lines = new ArrayList<>();

        int anger = warden.getClientAngerLevel();
        AngerLevel angerLevel = AngerLevel.byAnger(anger);
        Pose pose = warden.getPose();
        LivingEntity target = target(warden);
        boolean targetYou = target == mc.player;
        boolean hasTarget = target != null;
        boolean sonicCharge = warden.getBrain().getTimeUntilExpiry(MemoryModuleType.SONIC_BOOM_SOUND_DELAY) > 0;
        boolean attacking = sonicCharge || warden.attackAnimationState.isStarted() || warden.sonicBoomAnimationState.isStarted();

        String state = stateName(angerLevel, anger, pose, hasTarget);
        lines.add(new Line(state, stateColor(angerLevel, anger, pose, hasTarget, attacking)));

        if (showTarget.get() && hasTarget) {
            lines.add(new Line(targetYou ? "TARGET: YOU" : "TARGET: OTHER", targetYou ? new Color(255, 65, 65, 255) : new Color(180, 180, 180, 255)));
        }

        if (showPose.get()) {
            if (pose == Pose.SNIFFING) lines.add(new Line("SNIFFING", new Color(255, 235, 150, 255)));
            else if (pose == Pose.DIGGING) lines.add(new Line("DIGGING", new Color(85, 255, 255, 255)));
            else if (pose == Pose.EMERGING) lines.add(new Line("EMERGING", new Color(85, 255, 255, 255)));
            else if (pose == Pose.ROARING) lines.add(new Line("ROARING", new Color(255, 170, 0, 255)));
        }

        if (showAnger.get()) lines.add(new Line("Anger: " + anger, new Color(220, 220, 220, 255)));

        return lines;
    }

    private LivingEntity target(Warden warden) {
        LivingEntity target = warden.getTarget();
        if (target != null) return target;
        return warden.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }

    private String stateName(AngerLevel angerLevel, int anger, Pose pose, boolean hasTarget) {
        if (pose == Pose.DIGGING) return "DIGGING";
        if (pose == Pose.EMERGING) return "EMERGING";
        if (hasTarget || angerLevel.isAngry()) return "LOCKED";
        if (pose == Pose.SNIFFING || angerLevel == AngerLevel.AGITATED) return "AGITATED";
        if (anger > 0) return "SEARCHING";
        return "CALM";
    }

    private Color stateColor(AngerLevel angerLevel, int anger, Pose pose, boolean hasTarget, boolean attacking) {
        if (attacking || hasTarget || angerLevel.isAngry()) return new Color(255, 65, 65, 255);
        if (pose == Pose.DIGGING || pose == Pose.EMERGING) return new Color(85, 255, 255, 255);
        if (pose == Pose.SNIFFING || angerLevel == AngerLevel.AGITATED) return new Color(255, 170, 0, 255);
        if (anger > 0) return new Color(255, 255, 85, 255);
        return new Color(85, 255, 85, 255);
    }

    private record Line(String text, Color color) {}
}
