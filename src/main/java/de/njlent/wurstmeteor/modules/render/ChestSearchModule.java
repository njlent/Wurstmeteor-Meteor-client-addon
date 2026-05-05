package de.njlent.wurstmeteor.modules.render;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import de.njlent.wurstmeteor.util.BlockEntityUtils;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.AABB;

public class ChestSearchModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> chunkRadius = sgGeneral.add(new IntSetting.Builder()
        .name("chunk-radius")
        .description("Loaded chunk radius to scan for container block entities.")
        .defaultValue(8)
        .range(1, 32)
        .sliderRange(1, 16)
        .build()
    );

    private final Setting<Boolean> chests = sgGeneral.add(new BoolSetting.Builder()
        .name("chests")
        .description("Highlights chests, trapped chests, and ender chests.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> shulkers = sgGeneral.add(new BoolSetting.Builder()
        .name("shulkers")
        .description("Highlights shulker boxes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> barrels = sgGeneral.add(new BoolSetting.Builder()
        .name("barrels")
        .description("Highlights barrels.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> otherStorage = sgGeneral.add(new BoolSetting.Builder()
        .name("other-storage")
        .description("Highlights hoppers and furnaces.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> tracers = sgGeneral.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draws tracer lines to matching containers.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Container ESP color.")
        .defaultValue(new SettingColor(40, 255, 140, 100))
        .build()
    );

    private int count;

    public ChestSearchModule() {
        super(WurstMeteorAddon.CATEGORY, "chest-search", "Highlights loaded storage block entities.");
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

        for (BlockEntity blockEntity : BlockEntityUtils.getLoadedBlockEntities(mc.level, mc.player.blockPosition(), chunkRadius.get())) {
            if (!matches(blockEntity.getType())) continue;
            count++;

            AABB box = new AABB(blockEntity.getBlockPos());
            event.renderer.box(box, side, line, ShapeMode.Both, 0);

            if (tracers.get() && RenderUtils.center != null) {
                var center = box.getCenter();
                event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, center.x, center.y, center.z, line);
            }
        }
    }

    private boolean matches(BlockEntityType<?> type) {
        if (chests.get() && (type == BlockEntityType.CHEST || type == BlockEntityType.TRAPPED_CHEST || type == BlockEntityType.ENDER_CHEST)) return true;
        if (shulkers.get() && type == BlockEntityType.SHULKER_BOX) return true;
        if (barrels.get() && type == BlockEntityType.BARREL) return true;
        return otherStorage.get() && (type == BlockEntityType.HOPPER || type == BlockEntityType.FURNACE || type == BlockEntityType.BLAST_FURNACE || type == BlockEntityType.SMOKER);
    }
}
