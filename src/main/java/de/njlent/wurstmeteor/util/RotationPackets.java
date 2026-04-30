package de.njlent.wurstmeteor.util;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;

public final class RotationPackets {
    private RotationPackets() {
    }

    public static void face(Vec3 target) {
        Minecraft mc = MeteorClient.mc;
        if (mc.player == null || mc.getConnection() == null) return;

        float[] angles = PlayerUtils.calculateAngle(target);
        mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(
            angles[0],
            angles[1],
            mc.player.onGround(),
            mc.player.horizontalCollision
        ));
    }
}
