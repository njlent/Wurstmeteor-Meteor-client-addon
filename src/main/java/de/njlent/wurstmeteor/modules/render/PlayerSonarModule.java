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
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerSonarModule extends Module {
    private static final double PLAYER_ESP_LIMIT_SQ = 128.0 * 128.0;

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

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Sonar ping color.")
        .defaultValue(new SettingColor(80, 170, 255, 120))
        .build()
    );

    private final Map<BlockPos, Ping> pings = new ConcurrentHashMap<>();

    public PlayerSonarModule() {
        super(WurstMeteorAddon.CATEGORY, "player-sonar", "Highlights suspicious far-away block activity packets.");
    }

    @Override
    public void onDeactivate() {
        pings.clear();
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

        Color side = new Color(color.get());
        Color line = new Color(color.get()).a(220);

        for (Ping ping : pings.values()) {
            AABB box = new AABB(ping.pos);
            event.renderer.box(box, side, line, ShapeMode.Both, 0);
            if (RenderUtils.center != null) {
                var center = box.getCenter();
                event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, center.x, center.y, center.z, line);
            }
        }
    }

    private void record(BlockPos pos, BlockState state) {
        if (pos == null || state == null || state.isAir()) return;
        if (onlyBeyondPlayerEsp.get() && mc.player.distanceToSqr(pos.getCenter()) <= PLAYER_ESP_LIMIT_SQ) return;

        BlockPos immutable = pos.immutable();
        Ping ping = pings.computeIfAbsent(immutable, Ping::new);
        boolean first = ping.hits == 0;
        ping.hits++;
        ping.lastUpdateMs = System.currentTimeMillis();

        if (first && chatAlerts.get()) info("Sonar ping at %s.", immutable.toShortString());
    }

    private static final class Ping {
        private final BlockPos pos;
        private long lastUpdateMs = System.currentTimeMillis();
        private int hits;

        private Ping(BlockPos pos) {
            this.pos = pos;
        }
    }
}
