package de.njlent.wurstmeteor.modules.player;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class PotionSaverModule extends Module {
    public PotionSaverModule() {
        super(WurstMeteorAddon.CATEGORY, "wurst-potion-saver", "Stops movement packets while frozen to preserve active potion effects.");
    }

    @EventHandler
    private void onSentPacket(PacketEvent.Send event) {
        if (!isFrozen()) return;
        if (event.packet instanceof ServerboundMovePlayerPacket) event.cancel();
    }

    public boolean isFrozen() {
        return isActive() && mc.player != null
            && !mc.player.getActiveEffects().isEmpty()
            && mc.player.getDeltaMovement().x == 0
            && mc.player.getDeltaMovement().z == 0;
    }
}
