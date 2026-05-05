package de.njlent.wurstmeteor.modules.render;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import de.njlent.wurstmeteor.mixin.TrialSpawnerStateDataAccessor;
import de.njlent.wurstmeteor.util.BlockEntityUtils;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerConfig;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerStateData;
import net.minecraft.world.level.block.entity.vault.VaultState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    private final Setting<Boolean> textOverlay = sgGeneral.add(new BoolSetting.Builder()
        .name("text-overlay")
        .description("Shows status text above trial spawners and vaults.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> overlayScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("overlay-scale")
        .description("Text overlay scale.")
        .defaultValue(1.0)
        .min(0.5)
        .sliderRange(0.5, 2.0)
        .build()
    );

    private final Setting<Boolean> showMobType = sgGeneral.add(new BoolSetting.Builder()
        .name("show-mob-type")
        .description("Shows the next mob type when available.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showStatus = sgGeneral.add(new BoolSetting.Builder()
        .name("show-status")
        .description("Shows trial spawner or vault state.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showTimers = sgGeneral.add(new BoolSetting.Builder()
        .name("show-timers")
        .description("Shows next-spawn and cooldown timers.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showDistance = sgGeneral.add(new BoolSetting.Builder()
        .name("show-distance")
        .description("Shows distance from the player.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showActivationRange = sgGeneral.add(new BoolSetting.Builder()
        .name("show-activation-range")
        .description("Shows the required player range for trial spawners.")
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

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!textOverlay.get() || mc.player == null || mc.level == null) return;

        for (BlockEntity blockEntity : BlockEntityUtils.getLoadedBlockEntities(mc.level, mc.player.blockPosition(), chunkRadius.get())) {
            boolean spawner = blockEntity.getType() == BlockEntityType.TRIAL_SPAWNER;
            boolean vault = showVaults.get() && blockEntity.getType() == BlockEntityType.VAULT;
            if (!spawner && !vault) continue;

            List<Line> lines = spawner ? spawnerLines(blockEntity) : vaultLines(blockEntity);
            if (lines.isEmpty()) continue;

            Vector3d pos = new Vector3d(blockEntity.getBlockPos().getX() + 0.5, blockEntity.getBlockPos().getY() + (spawner ? 1.65 : 1.25), blockEntity.getBlockPos().getZ() + 0.5);
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

    private List<Line> spawnerLines(BlockEntity blockEntity) {
        List<Line> lines = new ArrayList<>();
        if (!(blockEntity instanceof TrialSpawnerBlockEntity spawner)) return lines;

        TrialSpawner logic = spawner.getTrialSpawner();
        TrialSpawnerState state = logic.getState();
        TrialSpawnerStateData data = logic.getStateData();
        TrialSpawnerStateDataAccessor accessor = (TrialSpawnerStateDataAccessor) data;
        boolean ominous = logic.isOminous();
        SettingColor header = ominous ? vaultColor.get() : spawnerColor.get();

        lines.add(new Line(ominous ? "Ominous Trial Spawner" : "Trial Spawner", new Color(header).a(255)));

        if (showMobType.get()) {
            String mob = mobName(data.getUpdateTag(state));
            if (!mob.isBlank()) lines.add(new Line("Mob: " + mob, Color.WHITE));
        }

        if (showStatus.get()) lines.add(new Line("Status: " + statusName(state), Color.WHITE));

        TrialSpawnerConfig config = logic.activeConfig();
        int targetTotal = Math.max(1, config.calculateTargetTotalMobs(0));
        int spawned = Math.max(0, Math.min(accessor.wurstmeteor$getTotalMobsSpawned(), targetTotal));
        int alive = accessor.wurstmeteor$getCurrentMobs() == null ? 0 : accessor.wurstmeteor$getCurrentMobs().size();
        if (state == TrialSpawnerState.ACTIVE) lines.add(new Line("Mobs: " + Math.max(spawned, alive) + "/" + targetTotal + " alive " + alive, Color.WHITE));

        if (showTimers.get()) {
            long now = mc.level.getGameTime();
            long nextTicks = accessor.wurstmeteor$getNextMobSpawnsAt() - now;
            if (state == TrialSpawnerState.ACTIVE && nextTicks > 0) lines.add(new Line("Next: " + formatSeconds(nextTicks), Color.WHITE));

            long cooldownTicks = accessor.wurstmeteor$getCooldownEndsAt() - now;
            if (cooldownTicks > 0) lines.add(new Line("Cooldown: " + formatSeconds(cooldownTicks), Color.WHITE));
        }

        if (showActivationRange.get()) lines.add(new Line("Range: " + logic.getRequiredPlayerRange() + "m", Color.WHITE));
        if (showDistance.get()) lines.add(new Line("Distance: " + Math.round(Math.sqrt(mc.player.distanceToSqr(blockEntity.getBlockPos().getCenter()))) + "m", Color.WHITE));
        return lines;
    }

    private List<Line> vaultLines(BlockEntity blockEntity) {
        List<Line> lines = new ArrayList<>();
        BlockState state = mc.level.getBlockState(blockEntity.getBlockPos());
        boolean ominous = state.is(Blocks.VAULT) && state.hasProperty(VaultBlock.OMINOUS) && state.getValue(VaultBlock.OMINOUS);
        SettingColor header = ominous ? spawnerColor.get() : vaultColor.get();
        lines.add(new Line(ominous ? "Ominous Vault" : "Vault", new Color(header).a(255)));

        if (showStatus.get() && state.is(Blocks.VAULT) && state.hasProperty(VaultBlock.STATE)) {
            VaultState vaultState = state.getValue(VaultBlock.STATE);
            lines.add(new Line("Status: " + title(vaultState.getSerializedName()), Color.WHITE));
        }

        if (showDistance.get()) lines.add(new Line("Distance: " + Math.round(Math.sqrt(mc.player.distanceToSqr(blockEntity.getBlockPos().getCenter()))) + "m", Color.WHITE));
        return lines;
    }

    private String mobName(CompoundTag tag) {
        CompoundTag spawnData = tag.getCompound("spawn_data").orElse(tag);
        CompoundTag entity = spawnData.getCompound("entity").orElse(null);
        if (entity == null) return "";

        String id = entity.getString("id").orElse("");
        if (id.isBlank()) return "";

        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) return title(id);

        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getValue(identifier);
        if (entityType == null) return title(identifier.getPath());
        return entityType.getDescription().getString();
    }

    private String statusName(TrialSpawnerState state) {
        return title(state.getSerializedName());
    }

    private String formatSeconds(long ticks) {
        return String.format(Locale.ROOT, "%.1fs", Math.max(0.0, ticks / 20.0));
    }

    private String title(String raw) {
        String cleaned = raw;
        int colon = cleaned.indexOf(':');
        if (colon >= 0) cleaned = cleaned.substring(colon + 1);

        StringBuilder result = new StringBuilder();
        for (String part : cleaned.split("_")) {
            if (part.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }

        return result.isEmpty() ? raw : result.toString();
    }

    private record Line(String text, Color color) {}
}
