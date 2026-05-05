package de.njlent.wurstmeteor.modules.render;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class BedrockStashModule extends Module {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int MAX_POCKET_BLOCKS = 4096;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> chunkRadius = sgGeneral.add(new IntSetting.Builder()
        .name("chunk-radius")
        .description("Chunk radius around the player to scan.")
        .defaultValue(2)
        .range(1, 8)
        .sliderRange(1, 8)
        .build()
    );

    private final Setting<Integer> chunksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("chunks-per-tick")
        .description("Chunks scanned each tick.")
        .defaultValue(1)
        .range(1, 8)
        .sliderRange(1, 8)
        .build()
    );

    private final Setting<Integer> verticalRange = sgGeneral.add(new IntSetting.Builder()
        .name("vertical-range")
        .description("Blocks above and below the scan center to inspect.")
        .defaultValue(48)
        .range(8, 192)
        .sliderRange(8, 192)
        .build()
    );

    private final Setting<Integer> renderLimit = sgGeneral.add(new IntSetting.Builder()
        .name("render-limit")
        .description("Maximum stash columns rendered.")
        .defaultValue(300)
        .range(50, 5000)
        .sliderRange(50, 1000)
        .build()
    );

    private final Setting<Boolean> fillBoxes = sgGeneral.add(new BoolSetting.Builder()
        .name("fill-boxes")
        .description("Renders solid fill in addition to outlines.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> airColor = sgGeneral.add(new ColorSetting.Builder()
        .name("air-color")
        .description("Color for air-filled bedrock pockets.")
        .defaultValue(new SettingColor(90, 220, 255, 120))
        .build()
    );

    private final Setting<SettingColor> breakableColor = sgGeneral.add(new ColorSetting.Builder()
        .name("breakable-color")
        .description("Color for pockets containing breakable blocks.")
        .defaultValue(new SettingColor(255, 170, 60, 120))
        .build()
    );

    private final ArrayDeque<ChunkPos> scanQueue = new ArrayDeque<>();
    private final HashMap<ChunkPos, ArrayList<StashHit>> hitsByChunk = new HashMap<>();
    private final ArrayList<StashHit> visibleHits = new ArrayList<>();
    private final HashSet<Long> visitedPositions = new HashSet<>();

    private Level activeLevel;
    private ChunkPos lastPlayerChunk;
    private int lastRadius = -1;
    private int lastCenterY = Integer.MIN_VALUE;
    private int sideBoundaryY = Integer.MIN_VALUE;
    private boolean playerAboveSideBoundary;
    private boolean hasSideBoundary;
    private int foundAir;
    private int foundBreakable;

    public BedrockStashModule() {
        super(WurstMeteorAddon.CATEGORY, "bedrock-stash", "Scans bedrock layers for enclosed two-block-tall stash pockets.");
    }

    @Override
    public void onActivate() {
        clearState();
    }

    @Override
    public void onDeactivate() {
        clearState();
    }

    @Override
    public String getInfoString() {
        int total = foundAir + foundBreakable;
        return total == 0 ? null : foundAir + "A " + foundBreakable + "B";
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.level == null || mc.player == null) {
            clearState();
            return;
        }

        if (activeLevel != mc.level) {
            clearState();
            activeLevel = mc.level;
        }

        updateSideBoundary();
        ChunkPos currentChunk = mc.player.chunkPosition();
        int centerY = getScanCenterY();
        if (lastPlayerChunk == null || !lastPlayerChunk.equals(currentChunk) || lastRadius != chunkRadius.get() || lastCenterY != centerY) {
            lastPlayerChunk = currentChunk;
            lastRadius = chunkRadius.get();
            lastCenterY = centerY;
            rebuildScanQueue();
            hitsByChunk.clear();
        }

        if (scanQueue.isEmpty()) rebuildScanQueue();
        for (int i = 0; i < chunksPerTick.get() && !scanQueue.isEmpty(); i++) scanChunk(scanQueue.removeFirst());
        recountHits();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (visibleHits.isEmpty()) return;

        ShapeMode mode = fillBoxes.get() ? ShapeMode.Both : ShapeMode.Lines;
        int rendered = 0;
        for (StashHit hit : visibleHits) {
            if (rendered++ >= renderLimit.get()) break;

            SettingColor base = hit.air ? airColor.get() : breakableColor.get();
            Color side = new Color(base);
            Color line = new Color(base).a(220);
            event.renderer.box(hit.box, side, line, mode, 0);
        }
    }

    private void rebuildScanQueue() {
        scanQueue.clear();
        visitedPositions.clear();
        if (mc.player == null) return;

        ChunkPos center = mc.player.chunkPosition();
        int radius = chunkRadius.get();
        HashSet<ChunkPos> currentArea = new HashSet<>();

        for (int x = center.x() - radius; x <= center.x() + radius; x++) {
            for (int z = center.z() - radius; z <= center.z() + radius; z++) {
                ChunkPos pos = new ChunkPos(x, z);
                currentArea.add(pos);
                scanQueue.addLast(pos);
            }
        }

        hitsByChunk.keySet().removeIf(pos -> !currentArea.contains(pos));
    }

    private void scanChunk(ChunkPos chunkPos) {
        ArrayList<StashHit> hits = new ArrayList<>();
        int minY = mc.level.getMinY();
        int maxY = minY + mc.level.getHeight() - 1;
        int centerY = getScanCenterY();
        int scanMinY = Math.max(minY, centerY - verticalRange.get());
        int scanMaxY = Math.min(maxY, centerY + verticalRange.get());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
            for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                for (int y = scanMinY; y <= scanMaxY; y++) {
                    pos.set(x, y, z);
                    if (mc.level.getBlockState(pos).is(Blocks.BEDROCK)) continue;
                    if (!hasAdjacentBedrock(pos)) continue;
                    tryAddPocket(pos.immutable(), hits);
                }
            }
        }

        hitsByChunk.put(chunkPos, hits);
    }

    private void tryAddPocket(BlockPos start, ArrayList<StashHit> hits) {
        long startKey = start.asLong();
        if (visitedPositions.contains(startKey)) return;

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        HashSet<Long> componentSet = new HashSet<>();
        ArrayList<BlockPos> component = new ArrayList<>();
        queue.add(start);
        componentSet.add(startKey);

        boolean allAir = true;
        boolean leaked = false;
        boolean tooLarge = false;

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            if (!mc.level.hasChunkAt(current)) {
                leaked = true;
                continue;
            }

            BlockState state = mc.level.getBlockState(current);
            if (state.is(Blocks.BEDROCK)) continue;

            component.add(current);
            if (component.size() > MAX_POCKET_BLOCKS) {
                tooLarge = true;
                break;
            }

            if (!state.isAir()) allAir = false;

            for (Direction direction : DIRECTIONS) {
                BlockPos neighbor = current.relative(direction);
                if (!mc.level.hasChunkAt(neighbor)) {
                    leaked = true;
                    continue;
                }
                if (mc.level.getBlockState(neighbor).is(Blocks.BEDROCK)) continue;
                if (componentSet.add(neighbor.asLong())) queue.add(neighbor);
            }
        }

        visitedPositions.addAll(componentSet);
        if (leaked || tooLarge || component.size() < 2) return;
        if (!hasTwoTallColumn(component, componentSet)) return;
        if (!isEnclosedByBedrock(component, componentSet)) return;

        addColumnHits(component, componentSet, allAir, hits);
    }

    private boolean isEnclosedByBedrock(ArrayList<BlockPos> component, HashSet<Long> componentSet) {
        for (BlockPos pos : component) {
            for (Direction direction : DIRECTIONS) {
                BlockPos neighbor = pos.relative(direction);
                if (componentSet.contains(neighbor.asLong())) continue;
                if (!mc.level.hasChunkAt(neighbor) || !mc.level.getBlockState(neighbor).is(Blocks.BEDROCK)) return false;
            }
        }
        return true;
    }

    private boolean hasAdjacentBedrock(BlockPos pos) {
        for (Direction direction : DIRECTIONS) {
            BlockPos neighbor = pos.relative(direction);
            if (mc.level.hasChunkAt(neighbor) && mc.level.getBlockState(neighbor).is(Blocks.BEDROCK)) return true;
        }
        return false;
    }

    private boolean hasTwoTallColumn(ArrayList<BlockPos> component, HashSet<Long> componentSet) {
        for (BlockPos pos : component) {
            if (!componentSet.contains(pos.below().asLong()) && componentSet.contains(pos.above().asLong())) return true;
        }
        return false;
    }

    private void addColumnHits(ArrayList<BlockPos> component, HashSet<Long> componentSet, boolean allAir, ArrayList<StashHit> hits) {
        for (BlockPos pos : component) {
            if (componentSet.contains(pos.below().asLong())) continue;

            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            int topY = y;
            while (componentSet.contains(BlockPos.asLong(x, topY + 1, z))) topY++;
            if (topY <= y) continue;

            hits.add(new StashHit(new AABB(x, y, z, x + 1, topY + 1, z + 1), allAir));
        }
    }

    private void recountHits() {
        visibleHits.clear();
        foundAir = 0;
        foundBreakable = 0;

        for (ArrayList<StashHit> hits : hitsByChunk.values()) {
            for (StashHit hit : hits) {
                if (!isHitOnPlayerSide(hit.box)) continue;
                visibleHits.add(hit);
                if (hit.air) foundAir++;
                else foundBreakable++;
            }
        }
    }

    private int getScanCenterY() {
        return hasSideBoundary ? sideBoundaryY : mc.player.getBlockY();
    }

    private void updateSideBoundary() {
        hasSideBoundary = false;
        if (mc.level == null || mc.player == null) return;

        int px = mc.player.getBlockX();
        int py = mc.player.getBlockY();
        int pz = mc.player.getBlockZ();
        int minY = mc.level.getMinY();
        int maxY = minY + mc.level.getHeight() - 1;

        int aboveY = Integer.MIN_VALUE;
        int belowY = Integer.MIN_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = py; y <= maxY; y++) {
            pos.set(px, y, pz);
            if (mc.level.getBlockState(pos).is(Blocks.BEDROCK)) {
                aboveY = y;
                break;
            }
        }

        for (int y = py; y >= minY; y--) {
            pos.set(px, y, pz);
            if (mc.level.getBlockState(pos).is(Blocks.BEDROCK)) {
                belowY = y;
                break;
            }
        }

        boolean hasAbove = aboveY != Integer.MIN_VALUE;
        boolean hasBelow = belowY != Integer.MIN_VALUE;
        if (!hasAbove && !hasBelow) return;

        if (hasBelow && (!hasAbove || py - belowY <= aboveY - py)) {
            sideBoundaryY = belowY;
            playerAboveSideBoundary = true;
        } else {
            sideBoundaryY = aboveY;
            playerAboveSideBoundary = false;
        }

        hasSideBoundary = true;
    }

    private boolean isHitOnPlayerSide(AABB box) {
        if (mc.level != null && mc.level.dimension() == Level.NETHER) {
            boolean onRoofSide = mc.player.getBlockY() >= 123;
            return onRoofSide ? box.minY >= 123 : box.maxY < 123;
        }

        if (!hasSideBoundary) return true;
        return playerAboveSideBoundary ? box.minY >= sideBoundaryY + 1 : box.maxY <= sideBoundaryY;
    }

    private void clearState() {
        activeLevel = null;
        lastPlayerChunk = null;
        lastRadius = -1;
        lastCenterY = Integer.MIN_VALUE;
        sideBoundaryY = Integer.MIN_VALUE;
        playerAboveSideBoundary = false;
        hasSideBoundary = false;
        scanQueue.clear();
        hitsByChunk.clear();
        visibleHits.clear();
        visitedPositions.clear();
        foundAir = 0;
        foundBreakable = 0;
    }

    private record StashHit(AABB box, boolean air) {}
}
