package de.njlent.wurstmeteor.modules.misc;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AntiCheatDetectModule extends Module {
    private static final long BLOCK_BURST_WINDOW_MS = 2000L;
    private static final long BLOCK_FLIP_WINDOW_MS = 1500L;
    private static final long BLOCK_HISTORY_TTL_MS = 10000L;
    private static final long BE_BURST_WINDOW_MS = 2000L;
    private static final int BE_BURST_THRESHOLD = 40;
    private static final long ALERT_COOLDOWN_MS = 8000L;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> announceServer = sgGeneral.add(new BoolSetting.Builder()
        .name("announce-server")
        .description("Guesses common anti-cheats from the current server address.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> suppressUnknown = sgGeneral.add(new BoolSetting.Builder()
        .name("suppress-unknown")
        .description("Does not print a message when no known anti-cheat was guessed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectGlobalAntiEsp = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-global-anti-esp")
        .description("Detects suspicious global block update patterns often used by anti-xray or anti-ESP systems.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> blockBurstThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("block-burst-threshold")
        .description("Block changes inside two seconds before warning.")
        .defaultValue(400)
        .range(50, 5000)
        .sliderRange(50, 2000)
        .build()
    );

    private final ArrayDeque<BlockChange> blockEvents = new ArrayDeque<>();
    private final ArrayDeque<Long> blockEntityPackets = new ArrayDeque<>();
    private final Map<Long, Integer> blockCounts = new HashMap<>();
    private final Map<Long, Snapshot> lastBlocks = new HashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private String announced;

    public AntiCheatDetectModule() {
        super(WurstMeteorAddon.CATEGORY, "anti-cheat-detect", "Guesses server anti-cheats and warns about global anti-ESP packet patterns.");
    }

    @Override
    public void onActivate() {
        resetPacketState();
        announceGuess();
    }

    @Override
    public void onDeactivate() {
        resetPacketState();
        announced = null;
    }

    @Override
    public String getInfoString() {
        return announced;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!detectGlobalAntiEsp.get()) return;
        prune(System.currentTimeMillis());
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!detectGlobalAntiEsp.get()) return;

        if (event.packet instanceof ClientboundBlockUpdatePacket packet) {
            recordBlock(packet.getPos(), packet.getBlockState());
        } else if (event.packet instanceof ClientboundSectionBlocksUpdatePacket packet) {
            packet.runUpdates(this::recordBlock);
        } else if (event.packet instanceof ClientboundBlockEntityDataPacket packet) {
            recordBlockEntity(packet.getPos());
        }
    }

    private void announceGuess() {
        if (!announceServer.get()) return;

        String guess = guessAntiCheat();
        if (guess == null) {
            announced = null;
            return;
        }

        announced = guess;
        if (!"Unknown".equals(guess) || !suppressUnknown.get()) info("Detected anti-cheat: %s.", guess);
    }

    private String guessAntiCheat() {
        String address = getServerAddress();
        if (address == null || address.isBlank()) return "Unknown";

        String lower = address.toLowerCase(Locale.ROOT);
        if (lower.contains("hypixel")) return "Watchdog";
        if (lower.contains("cubecraft")) return "Sentinel";
        if (lower.contains("mineplex")) return "GWEN";
        if (lower.contains("hive")) return "Hive anti-cheat";
        if (lower.contains("2b2t") || lower.contains("anarchy")) return "NoCheatPlus / custom";
        if (lower.contains("minemen") || lower.contains("mmc")) return "AGC";
        if (lower.contains("gomme")) return "AAC / custom";
        return "Unknown";
    }

    private String getServerAddress() {
        if (mc.getCurrentServer() != null) return mc.getCurrentServer().ip;
        if (mc.getConnection() != null && mc.getConnection().getServerData() != null) return mc.getConnection().getServerData().ip;
        return null;
    }

    private void recordBlock(BlockPos pos, BlockState state) {
        if (pos == null || state == null || state.isAir() || !state.getFluidState().isEmpty()) return;

        long now = System.currentTimeMillis();
        long key = pos.asLong();
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        Snapshot previous = lastBlocks.put(key, new Snapshot(id, now));

        if (previous != null && !previous.blockId.equals(id) && now - previous.timestamp <= BLOCK_FLIP_WINDOW_MS && !isSuppressedTransition(previous.blockId, id)) {
            alert("flip-flop", "Rapid global block swap at " + pos.toShortString() + " (" + previous.blockId + " -> " + id + ").");
        }

        blockEvents.addLast(new BlockChange(key, now));
        blockCounts.merge(key, 1, Integer::sum);
        pruneBlockWindow(now);

        int threshold = blockBurstThreshold.get();
        int uniqueMin = Math.max(24, Math.round(threshold * 0.35F));
        if (blockEvents.size() >= threshold && blockCounts.size() >= uniqueMin) {
            alert("global-burst", "Observed " + blockEvents.size() + " block changes across " + blockCounts.size() + " unique blocks in 2s.");
        }
    }

    private void recordBlockEntity(BlockPos pos) {
        if (pos == null) return;

        long now = System.currentTimeMillis();
        blockEntityPackets.addLast(now);
        while (!blockEntityPackets.isEmpty() && now - blockEntityPackets.peekFirst() > BE_BURST_WINDOW_MS) blockEntityPackets.removeFirst();

        if (blockEntityPackets.size() >= BE_BURST_THRESHOLD) {
            alert("be-burst", "Received " + blockEntityPackets.size() + " block-entity packets in 2s.");
        }
    }

    private void alert(String key, String message) {
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(key);
        if (last != null && now - last < ALERT_COOLDOWN_MS) return;

        cooldowns.put(key, now);
        warning("Global anti-ESP signal: %s", message);
    }

    private void prune(long now) {
        pruneBlockWindow(now);
        while (!blockEntityPackets.isEmpty() && now - blockEntityPackets.peekFirst() > BE_BURST_WINDOW_MS) blockEntityPackets.removeFirst();
        lastBlocks.entrySet().removeIf(entry -> now - entry.getValue().timestamp > BLOCK_HISTORY_TTL_MS);
        cooldowns.entrySet().removeIf(entry -> now - entry.getValue() > 30000L);
    }

    private void pruneBlockWindow(long now) {
        while (!blockEvents.isEmpty() && now - blockEvents.peekFirst().timestamp > BLOCK_BURST_WINDOW_MS) {
            BlockChange event = blockEvents.removeFirst();
            blockCounts.computeIfPresent(event.posKey, (ignored, count) -> count <= 1 ? null : count - 1);
        }
    }

    private boolean isSuppressedTransition(String oldId, String newId) {
        if (oldId.equals(newId)) return true;
        if (isAirOrFluid(oldId) || isAirOrFluid(newId)) return true;
        if (oldId.contains("fire") || newId.contains("fire")) return true;
        return oldId.endsWith("_vines") && newId.endsWith("_vines_plant")
            || oldId.endsWith("_vines_plant") && newId.endsWith("_vines")
            || oldId.endsWith("kelp") && newId.endsWith("kelp_plant")
            || oldId.endsWith("kelp_plant") && newId.endsWith("kelp");
    }

    private boolean isAirOrFluid(String id) {
        return id.endsWith(":air") || id.endsWith(":cave_air") || id.endsWith(":void_air") || id.endsWith(":water") || id.endsWith(":lava");
    }

    private void resetPacketState() {
        blockEvents.clear();
        blockEntityPackets.clear();
        blockCounts.clear();
        lastBlocks.clear();
        cooldowns.clear();
    }

    private record BlockChange(long posKey, long timestamp) {}

    private record Snapshot(String blockId, long timestamp) {}
}
