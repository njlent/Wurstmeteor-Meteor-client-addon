package de.njlent.wurstmeteor.modules.combat;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.hit.HitResult;

public class MaceDmgModule extends Module {
    public MaceDmgModule() {
        super(WurstMeteorAddon.CATEGORY, "mace-dmg", "Spoofs vertical movement packets before mace hits for higher smash damage.");
    }

    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.ENTITY) return;
        if (!mc.player.getMainHandStack().isOf(Items.MACE)) return;

        for (int i = 0; i < 4; i++) sendFakeY(0.0);
        sendFakeY(Math.sqrt(500.0));
        sendFakeY(0.0);
    }

    private void sendFakeY(double offset) {
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
            mc.player.getX(),
            mc.player.getY() + offset,
            mc.player.getZ(),
            false,
            mc.player.horizontalCollision
        ));
    }
}
