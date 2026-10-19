package de.njlent.wurstmeteor.util;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.world.InteractionHand;

public final class SwingUtils {
    private SwingUtils() {
    }

    // Matches vanilla attacking/mining: local animation plus a punch packet so the server swings too.
    public static void swingAttack() {
        swingAttackClient(InteractionHand.MAIN_HAND);
        sendPunch();
    }

    public static void swingAttackClient(InteractionHand hand) {
        Minecraft mc = MeteorClient.mc;
        if (mc.player == null) return;

        mc.player.swing(hand, mc.player.getItemInHand(hand).getAttackAnimation(), false);
    }

    public static void sendPunch() {
        Minecraft mc = MeteorClient.mc;
        if (mc.getConnection() == null) return;

        mc.getConnection().send(ServerboundPunchPacket.INSTANCE);
    }

    // Item use/interaction swings are broadcast by the server, so only the local animation is needed.
    public static void swingInteract(InteractionHand hand) {
        Minecraft mc = MeteorClient.mc;
        if (mc.player == null) return;

        mc.player.swing(hand, mc.player.getItemInHand(hand).getInteractAnimation(), false);
    }
}
