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

public class TrialSpawnerEspModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> chunkRadius = sgGeneral.add(new IntSetting.Builder()
        .name("chunk-radius")
        .description("Loaded chunk radius to scan.")
        .defaultValue(8)
        .range(1, 32)
        .sliderRange(1, 16)
        .build()
    );

    private final Setting<Boolean> showVaults = sgGeneral.add(new BoolSetting.Builder()
        .name("show-vaults")
        .description("Also highlights vaults.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> tracers = sgGeneral.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draws tracer lines to trial spawners and vaults.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> spawnerColor = sgGeneral.add(new ColorSetting.Builder()
        .name("spawner-color")
        .description("Trial spawner ESP color.")
        .defaultValue(new SettingColor(244, 197, 66, 120))
        .build()
    );

    private final Setting<SettingColor> vaultColor = sgGeneral.add(new ColorSetting.Builder()
        .name("vault-color")
        .description("Vault ESP color.")
        .defaultValue(new SettingColor(124, 242, 201, 120))
        .build()
    );

    private int count;

    public TrialSpawnerEspModule() {
        super(WurstMeteorAddon.CATEGORY, "trial-spawner-esp", "Highlights trial spawners and vaults in loaded chunks.");
    }

    @Override
    public String getInfoString() {
        return count == 0 ? null : Integer.toString(count);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;

        count = 0;
        for (BlockEntity blockEntity : BlockEntityUtils.getLoadedBlockEntities(mc.level, mc.player.blockPosition(), chunkRadius.get())) {
            boolean spawner = blockEntity.getType() == BlockEntityType.TRIAL_SPAWNER;
            boolean vault = showVaults.get() && blockEntity.getType() == BlockEntityType.VAULT;
            if (!spawner && !vault) continue;

            count++;
            Color side = new Color(spawner ? spawnerColor.get() : vaultColor.get());
            Color line = new Color(side).a(220);
            AABB box = new AABB(blockEntity.getBlockPos());
            event.renderer.box(box, side, line, ShapeMode.Both, 0);

            if (tracers.get() && RenderUtils.center != null) {
                var center = box.getCenter();
                event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, center.x, center.y, center.z, line);
            }
        }
    }
}
