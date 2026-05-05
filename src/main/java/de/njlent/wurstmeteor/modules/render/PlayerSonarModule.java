package de.njlent.wurstmeteor.modules.render;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerSonarModule extends Module {
    private static final double PLAYER_ESP_LIMIT_SQ = 128.0 * 128.0;
    private static final long STATE_CACHE_TTL_MS = 120000L;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> ttl = sgGeneral.add(new IntSetting.Builder()
        .name("ttl")
        .description("How long pings remain visible, in seconds.")
        .defaultValue(45)
        .range(5, 300)
        .sliderRange(5, 120)
        .build()
    );

    private final Setting<Boolean> onlyBeyondPlayerEsp = sgGeneral.add(new BoolSetting.Builder()
        .name("only-beyond-player-esp")
        .description("Only records activity beyond normal player ESP distance.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> chatAlerts = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-alerts")
        .description("Prints a warning when a new sonar ping appears.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> detectRedstone = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-redstone")
        .description("Also records powered, open, triggered, and lit block-state changes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> placeColor = sgGeneral.add(new ColorSetting.Builder()
        .name("place-color")
        .description("Sonar color for block placement updates.")
        .defaultValue(new SettingColor(255, 80, 80, 120))
        .build()
    );

    private final Setting<SettingColor> breakColor = sgGeneral.add(new ColorSetting.Builder()
        .name("break-color")
        .description("Sonar color for block break updates.")
        .defaultValue(new SettingColor(80, 200, 255, 120))
        .build()
    );

    private final Setting<SettingColor> redstoneColor = sgGeneral.add(new ColorSetting.Builder()
        .name("redstone-color")
        .description("Sonar color for interactive redstone-style updates.")
        .defaultValue(new SettingColor(255, 200, 80, 120))
        .build()
    );

    private final Map<BlockPos, Ping> pings = new ConcurrentHashMap<>();
    private final Map<BlockPos, CachedState> knownStates = new ConcurrentHashMap<>();

    public PlayerSonarModule() {
        super(WurstMeteorAddon.CATEGORY, "player-sonar", "Highlights suspicious far-away block activity packets.");
    }

    @Override
    public void onDeactivate() {
        pings.clear();
        knownStates.clear();
    }

    @Override
    public String getInfoString() {
        return pings.isEmpty() ? null : Integer.toString(pings.size());
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        long expireBefore = System.currentTimeMillis() - ttl.get() * 1000L;
        Iterator<Ping> iterator = pings.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().lastUpdateMs < expireBefore) iterator.remove();
        }

        long staleStateBefore = System.currentTimeMillis() - STATE_CACHE_TTL_MS;
        knownStates.entrySet().removeIf(entry -> entry.getValue().lastUpdateMs < staleStateBefore);
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null || mc.level == null) return;

        if (event.packet instanceof ClientboundBlockUpdatePacket packet) {
            record(packet.getPos(), packet.getBlockState());
        } else if (event.packet instanceof ClientboundSectionBlocksUpdatePacket packet) {
            packet.runUpdates(this::record);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;

        for (Ping ping : pings.values()) {
            Color side = new Color(colorFor(ping.kind));
            Color line = new Color(colorFor(ping.kind)).a(220);
            AABB box = new AABB(ping.pos);
            event.renderer.box(box, side, line, ShapeMode.Both, 0);
            if (RenderUtils.center != null) {
                var center = box.getCenter();
                event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, center.x, center.y, center.z, line);
            }
        }
    }

    private void record(BlockPos pos, BlockState state) {
        if (pos == null || state == null) return;
        if (onlyBeyondPlayerEsp.get() && mc.player.distanceToSqr(pos.getCenter()) <= PLAYER_ESP_LIMIT_SQ) return;

        BlockState oldState = resolveOldState(pos, state);
        SonarKind kind = classify(oldState, state);
        BlockPos immutable = pos.immutable();
        knownStates.put(immutable, new CachedState(state, System.currentTimeMillis()));
        if (kind == SonarKind.None) return;

        Ping ping = pings.computeIfAbsent(immutable, Ping::new);
        boolean first = ping.hits == 0;
        ping.hits++;
        ping.lastUpdateMs = System.currentTimeMillis();
        ping.kind = kind;
        ping.oldId = id(oldState);
        ping.newId = id(state);

        if (first && chatAlerts.get()) info("Sonar %s at %s: %s -> %s.", kind.label, immutable.toShortString(), ping.oldId, ping.newId);
    }

    private BlockState resolveOldState(BlockPos pos, BlockState newState) {
        CachedState cached = knownStates.get(pos);
        if (cached != null && System.currentTimeMillis() - cached.lastUpdateMs <= STATE_CACHE_TTL_MS && cached.state != newState) return cached.state;
        return mc.level.getBlockState(pos);
    }

    private SonarKind classify(BlockState oldState, BlockState newState) {
        if (oldState == null || newState == null || oldState == newState) return SonarKind.None;

        String oldId = id(oldState);
        String newId = id(newState);
        if (isLikelyNatural(oldId) || isLikelyNatural(newId)) return SonarKind.None;
        if (isFluidOrFire(oldState) || isFluidOrFire(newState)) return SonarKind.None;

        boolean oldAir = oldState.isAir();
        boolean newAir = newState.isAir();
        boolean oldFluid = !oldState.getFluidState().isEmpty();
        boolean newFluid = !newState.getFluidState().isEmpty();

        if (oldAir && !newAir && !newFluid) return SonarKind.Place;
        if (!oldAir && !oldFluid && newAir) return SonarKind.Break;
        if (detectRedstone.get() && oldState.getBlock() == newState.getBlock() && hasInteractiveFlip(oldState, newState)) return SonarKind.Redstone;
        return SonarKind.None;
    }

    private boolean hasInteractiveFlip(BlockState oldState, BlockState newState) {
        try {
            for (var property : oldState.getProperties()) {
                String name = property.getName();
                if (!name.equals("open") && !name.equals("powered") && !name.equals("triggered") && !name.equals("lit")) continue;
                if (!oldState.getValue(property).equals(newState.getValue(property))) return true;
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    private boolean isFluidOrFire(BlockState state) {
        if (state == null || !state.getFluidState().isEmpty()) return true;
        String id = id(state);
        return id.contains("fire");
    }

    private boolean isLikelyNatural(String blockId) {
        if (blockId == null || blockId.equals("minecraft:air")) return false;
        return blockId.contains("vine") || blockId.contains("amethyst_bud") || blockId.contains("mushroom")
            || blockId.contains("short_grass") || blockId.contains("tall_grass") || blockId.contains("fern")
            || blockId.contains("lichen") || blockId.contains("moss") || blockId.contains("seagrass")
            || blockId.contains("kelp") || blockId.contains("sugar_cane") || blockId.contains("cactus")
            || blockId.contains("bamboo") || blockId.contains("dripleaf") || blockId.contains("dripstone")
            || blockId.contains("cocoa") || blockId.contains("nether_wart") || blockId.contains("crop")
            || blockId.contains("sweet_berry_bush");
    }

    private String id(BlockState state) {
        if (state == null) return "minecraft:air";
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().toLowerCase(Locale.ROOT);
    }

    private SettingColor colorFor(SonarKind kind) {
        return switch (kind) {
            case Break -> breakColor.get();
            case Redstone -> redstoneColor.get();
            default -> placeColor.get();
        };
    }

    private static final class Ping {
        private final BlockPos pos;
        private long lastUpdateMs = System.currentTimeMillis();
        private int hits;
        private SonarKind kind = SonarKind.Place;
        private String oldId = "unknown";
        private String newId = "unknown";

        private Ping(BlockPos pos) {
            this.pos = pos;
        }
    }

    private record CachedState(BlockState state, long lastUpdateMs) {}

    private enum SonarKind {
        None("unknown"),
        Place("place"),
        Break("break"),
        Redstone("redstone");

        private final String label;

        SonarKind(String label) {
            this.label = label;
        }
    }
}
