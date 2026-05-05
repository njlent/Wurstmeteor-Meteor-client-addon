package de.njlent.wurstmeteor.modules.misc;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;

public class XCarryModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> ignoreSafetyChecks = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-safety-checks")
        .description("Cancels all close-container packets. Risky outside the inventory screen.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> disableInCreative = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-in-creative")
        .description("Disables XCarry in Creative mode.")
        .defaultValue(false)
        .build()
    );

    public XCarryModule() {
        super(WurstMeteorAddon.CATEGORY, "x-carry", "Keeps the 2x2 crafting grid from syncing closed while your inventory is closed.");
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null) return;
        if (!(event.packet instanceof ServerboundContainerClosePacket packet)) return;
        if (disableInCreative.get() && mc.player.getAbilities().instabuild) return;

        if (ignoreSafetyChecks.get() || packet.getContainerId() == mc.player.inventoryMenu.containerId) event.cancel();
    }
}
