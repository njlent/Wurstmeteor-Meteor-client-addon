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
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class ItemEspModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How item boxes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<BoxSize> boxSize = sgGeneral.add(new EnumSetting.Builder<BoxSize>()
        .name("box-size")
        .description("Accurate uses item hitboxes, Fancy renders larger easy-to-read boxes.")
        .defaultValue(BoxSize.Fancy)
        .build()
    );

    private final Setting<Boolean> tracers = sgGeneral.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draws tracer lines to each item.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> maxDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-distance")
        .description("Maximum render distance for item ESP.")
        .defaultValue(128.0)
        .min(1.0)
        .sliderRange(16.0, 256.0)
        .build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("ESP color for dropped items.")
        .defaultValue(new SettingColor(255, 220, 65, 120))
        .build()
    );

    public ItemEspModule() {
        super(WurstMeteorAddon.CATEGORY, "item-esp", "Highlights dropped items with boxes and optional tracers, from Wurstmeter Addon.");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        Color sideColor = new Color(color.get());
        Color lineColor = new Color(color.get()).a(200);

        double maxDistanceSq = maxDistance.get() * maxDistance.get();
        double extraSize = boxSize.get().extraSize * 0.5;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ItemEntity item)) continue;
            if (mc.player.squaredDistanceTo(item) > maxDistanceSq) continue;

            Box box = getInterpolatedBox(item, event.tickDelta);
            if (extraSize > 0.0) box = box.expand(extraSize).offset(0.0, extraSize, 0.0);

            event.renderer.box(box, sideColor, lineColor, shapeMode.get(), 0);

            if (tracers.get() && RenderUtils.center != null) {
                Vec3d center = box.getCenter();
                event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, center.x, center.y, center.z, lineColor);
            }
        }
    }

    private Box getInterpolatedBox(Entity entity, float tickDelta) {
        double x = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()) - entity.getX();
        double y = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) - entity.getY();
        double z = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()) - entity.getZ();
        return entity.getBoundingBox().offset(x, y, z);
    }

    public enum BoxSize {
        Accurate(0.0),
        Fancy(0.4);

        private final double extraSize;

        BoxSize(double extraSize) {
            this.extraSize = extraSize;
        }
    }
}
