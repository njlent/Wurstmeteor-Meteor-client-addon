package de.njlent.wurstmeteor.modules.render;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

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
}
