package de.njlent.wurstmeteor.util;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

public final class RotationPackets {
    private RotationPackets() {
    }

    public static void face(Vec3d target) {
        MinecraftClient mc = MeteorClient.mc;
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        float[] angles = PlayerUtils.calculateAngle(target);
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
            angles[0],
            angles[1],
            mc.player.isOnGround(),
            mc.player.horizontalCollision
        ));
    }
}
