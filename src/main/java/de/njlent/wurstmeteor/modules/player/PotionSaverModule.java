package de.njlent.wurstmeteor.modules.player;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public class PotionSaverModule extends Module {
    public PotionSaverModule() {
        super(WurstMeteorAddon.CATEGORY, "wurst-potion-saver", "Stops movement packets while frozen to preserve active potion effects.");
    }

    @EventHandler
    private void onSentPacket(PacketEvent.Send event) {
        if (!isFrozen()) return;
        if (event.packet instanceof PlayerMoveC2SPacket) event.cancel();
    }

    public boolean isFrozen() {
        return isActive() && mc.player != null
            && !mc.player.getStatusEffects().isEmpty()
            && mc.player.getVelocity().x == 0
            && mc.player.getVelocity().z == 0;
    }
}
