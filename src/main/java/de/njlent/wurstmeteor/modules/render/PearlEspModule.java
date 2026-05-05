package de.njlent.wurstmeteor.modules.render;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PearlEspModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Color for pearl ESP.")
        .defaultValue(new SettingColor(255, 0, 255, 120))
        .build()
    );

    private final Setting<Boolean> highlightHeld = sgGeneral.add(new BoolSetting.Builder()
        .name("highlight-held")
        .description("Highlights players holding ender pearls.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> trajectory = sgGeneral.add(new BoolSetting.Builder()
        .name("trajectory")
        .description("Draws a simple predicted pearl trajectory.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> tracers = sgGeneral.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draws tracers to pearls and holders.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> chatAlerts = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-alerts")
        .description("Prints a warning when a new pearl appears.")
        .defaultValue(false)
        .build()
    );

    private final Set<UUID> alertedPearls = new HashSet<>();

    public PearlEspModule() {
        super(WurstMeteorAddon.CATEGORY, "pearl-esp", "Highlights thrown ender pearls and players holding pearls.");
    }

    @Override
    public void onDeactivate() {
        alertedPearls.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!chatAlerts.get() || mc.level == null) return;

        Set<UUID> seen = new HashSet<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity.getType() != EntityType.ENDER_PEARL) continue;
            seen.add(entity.getUUID());
            if (alertedPearls.add(entity.getUUID())) {
                info("Ender pearl detected at %s, %s, %s.", (int) entity.getX(), (int) entity.getY(), (int) entity.getZ());
            }
        }
        alertedPearls.retainAll(seen);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;

        Color side = new Color(color.get());
        Color line = new Color(color.get()).a(220);

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity.getType() == EntityType.ENDER_PEARL) {
                AABB box = entity.getBoundingBox().inflate(0.15);
                event.renderer.box(box, side, line, ShapeMode.Both, 0);
                if (tracers.get() && RenderUtils.center != null) event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, box.getCenter().x, box.getCenter().y, box.getCenter().z, line);
                if (trajectory.get()) renderTrajectory(event, entity, line);
                continue;
            }

            if (highlightHeld.get() && entity instanceof Player player && player != mc.player && isHoldingPearl(player)) {
                AABB box = player.getBoundingBox().inflate(0.08);
                event.renderer.box(box, side, line, ShapeMode.Lines, 0);
                if (tracers.get() && RenderUtils.center != null) event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, box.getCenter().x, box.getCenter().y, box.getCenter().z, line);
            }
        }
    }

    private void renderTrajectory(Render3DEvent event, Entity pearl, Color line) {
        Vec3 pos = pearl.position();
        Vec3 velocity = pearl.getDeltaMovement();

        for (int i = 0; i < 80; i++) {
            Vec3 next = pos.add(velocity);
            HitResult hit = mc.level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, pearl));
            Vec3 end = hit.getType() == HitResult.Type.MISS ? next : hit.getLocation();
            event.renderer.line(pos.x, pos.y, pos.z, end.x, end.y, end.z, line);
            if (hit.getType() != HitResult.Type.MISS) {
                event.renderer.box(new AABB(end.subtract(0.25, 0.25, 0.25), end.add(0.25, 0.25, 0.25)), new Color(line).a(45), line, ShapeMode.Both, 0);
                return;
            }

            pos = next;
            velocity = velocity.scale(pearl.isInWater() ? 0.8 : 0.99);
            if (!pearl.isNoGravity()) velocity = velocity.add(0, -0.03, 0);
            if (pos.y < mc.level.getMinY() - 4 || velocity.lengthSqr() < 1.0E-4) return;
        }
    }

    private boolean isHoldingPearl(Player player) {
        return player.getMainHandItem().is(Items.ENDER_PEARL) || player.getOffhandItem().is(Items.ENDER_PEARL);
    }
}
