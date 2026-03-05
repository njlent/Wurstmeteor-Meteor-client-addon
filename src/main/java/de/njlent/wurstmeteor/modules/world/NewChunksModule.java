package de.njlent.wurstmeteor.modules.world;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class NewChunksModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Style> style = sgGeneral.add(new EnumSetting.Builder<Style>()
        .name("style")
        .description("How chunk markers are rendered.")
        .defaultValue(Style.Outline)
        .build()
    );

    private final Setting<Show> show = sgGeneral.add(new EnumSetting.Builder<Show>()
        .name("show")
        .description("Which chunk classes to render.")
        .defaultValue(Show.Both)
        .build()
    );

    private final Setting<Boolean> showReasons = sgGeneral.add(new BoolSetting.Builder()
        .name("show-reasons")
        .description("Renders source blocks used to classify chunks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showCounter = sgGeneral.add(new BoolSetting.Builder()
        .name("show-counter")
        .description("Shows counts in the module info string.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> logChunks = sgGeneral.add(new BoolSetting.Builder()
        .name("log-chunks")
        .description("Logs chunk discoveries in chat.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> altitude = sgRender.add(new IntSetting.Builder()
        .name("altitude")
        .description("Y level used for chunk markers.")
        .defaultValue(0)
        .range(-64, 320)
        .sliderRange(-64, 320)
        .build()
    );

    private final Setting<Integer> drawDistance = sgRender.add(new IntSetting.Builder()
        .name("draw-distance")
        .description("Chunk draw distance from player.")
        .defaultValue(32)
        .min(1)
        .sliderRange(4, 64)
        .build()
    );

    private final Setting<Double> opacity = sgRender.add(new DoubleSetting.Builder()
        .name("opacity")
        .description("Fill opacity for square style.")
        .defaultValue(0.75)
        .min(0.05)
        .sliderRange(0.05, 1.0)
        .build()
    );

    private final Setting<SettingColor> newChunkColor = sgRender.add(new ColorSetting.Builder()
        .name("new-chunks-color")
        .defaultValue(new SettingColor(255, 70, 70))
        .build()
    );

    private final Setting<SettingColor> oldChunkColor = sgRender.add(new ColorSetting.Builder()
        .name("old-chunks-color")
        .defaultValue(new SettingColor(90, 145, 255))
        .build()
    );

    private final Set<ChunkPos> newChunks = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> oldChunks = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> dontCheckAgain = ConcurrentHashMap.newKeySet();

    private final Set<BlockPos> newChunkReasons = ConcurrentHashMap.newKeySet();
    private final Set<BlockPos> oldChunkReasons = ConcurrentHashMap.newKeySet();

    private RegistryKey<World> lastDimension;

    public NewChunksModule() {
        super(WurstMeteorAddon.CATEGORY, "new-chunks", "Detects likely new and old chunks using flowing-fluid heuristics.");
    }

    @Override
    public void onActivate() {
        reset();
    }

    @Override
    public String getInfoString() {
        if (!showCounter.get()) return null;
        return newChunks.size() + "/" + oldChunks.size();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null) return;
        if (lastDimension != mc.world.getRegistryKey()) reset();
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.world == null) return;

        WorldChunk chunk = event.chunk();
        if (chunk == null) return;

        ChunkPos chunkPos = chunk.getPos();
        if (newChunks.contains(chunkPos) || oldChunks.contains(chunkPos) || dontCheckAgain.contains(chunkPos)) return;

        MeteorExecutor.execute(() -> checkLoadedChunk(chunk));
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (mc.world == null) return;

        FluidState fluidState = event.newState.getFluidState();
        if (fluidState.isEmpty() || fluidState.isStill()) return;

        ChunkPos chunkPos = new ChunkPos(event.pos);
        if (newChunks.contains(chunkPos) || oldChunks.contains(chunkPos)) return;

        newChunks.add(chunkPos);
        newChunkReasons.add(event.pos.toImmutable());

        if (logChunks.get()) info("New chunk at %s, %s", chunkPos.x, chunkPos.z);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        Color newLine = new Color(newChunkColor.get()).a(255);
        Color newSide = new Color(newChunkColor.get()).a((int) (255 * opacity.get()));

        Color oldLine = new Color(oldChunkColor.get()).a(255);
        Color oldSide = new Color(oldChunkColor.get()).a((int) (255 * opacity.get()));

        if (show.get().includesNew()) {
            for (ChunkPos chunkPos : newChunks) {
                if (!isInRenderDistance(chunkPos)) continue;
                renderChunkMarker(event, chunkPos, newSide, newLine);
            }

            if (showReasons.get()) {
                for (BlockPos pos : newChunkReasons) {
                    if (!isInRenderDistance(new ChunkPos(pos))) continue;
                    event.renderer.box(pos, newSide, newLine, ShapeMode.Both, 0);
                }
            }
        }

        if (show.get().includesOld()) {
            for (ChunkPos chunkPos : oldChunks) {
                if (!isInRenderDistance(chunkPos)) continue;
                renderChunkMarker(event, chunkPos, oldSide, oldLine);
            }

            if (showReasons.get()) {
                for (BlockPos pos : oldChunkReasons) {
                    if (!isInRenderDistance(new ChunkPos(pos))) continue;
                    event.renderer.box(pos, oldSide, oldLine, ShapeMode.Both, 0);
                }
            }
        }
    }

    private void renderChunkMarker(Render3DEvent event, ChunkPos chunkPos, Color sideColor, Color lineColor) {
        int y = altitude.get();
        double minX = chunkPos.getStartX();
        double maxX = chunkPos.getEndX() + 1;
        double minZ = chunkPos.getStartZ();
        double maxZ = chunkPos.getEndZ() + 1;

        if (style.get() == Style.Outline) {
            event.renderer.boxLines(minX, y, minZ, maxX, y + 1, maxZ, lineColor, 0);
        } else {
            event.renderer.box(minX, y, minZ, maxX, y + 0.1, maxZ, sideColor, lineColor, ShapeMode.Both, 0);
        }
    }

    private void checkLoadedChunk(WorldChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        if (newChunks.contains(chunkPos) || oldChunks.contains(chunkPos) || dontCheckAgain.contains(chunkPos)) return;

        int minY = mc.world.getBottomY();
        int maxY = minY + mc.world.getHeight();

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int x = chunkPos.getStartX(); x <= chunkPos.getEndX(); x++) {
            for (int z = chunkPos.getStartZ(); z <= chunkPos.getEndZ(); z++) {
                for (int y = minY; y < maxY; y++) {
                    mutable.set(x, y, z);
                    FluidState fluidState = chunk.getFluidState(mutable);

                    if (fluidState.isEmpty() || fluidState.isStill()) continue;

                    oldChunks.add(chunkPos);
                    oldChunkReasons.add(mutable.toImmutable());

                    if (logChunks.get()) info("Old chunk at %s, %s", chunkPos.x, chunkPos.z);
                    return;
                }
            }
        }

        dontCheckAgain.add(chunkPos);
    }

    private boolean isInRenderDistance(ChunkPos chunkPos) {
        ChunkPos playerChunk = mc.player.getChunkPos();
        int dd = drawDistance.get();

        return Math.abs(chunkPos.x - playerChunk.x) <= dd && Math.abs(chunkPos.z - playerChunk.z) <= dd;
    }

    private void reset() {
        newChunks.clear();
        oldChunks.clear();
        dontCheckAgain.clear();

        newChunkReasons.clear();
        oldChunkReasons.clear();

        lastDimension = mc.world == null ? null : mc.world.getRegistryKey();
    }

    public enum Style {
        Outline,
        Square
    }

    public enum Show {
        Both,
        NewOnly,
        OldOnly;

        public boolean includesNew() {
            return this != OldOnly;
        }

        public boolean includesOld() {
            return this != NewOnly;
        }
    }
}
