package de.njlent.wurstmeteor.modules.combat;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.HitResult;

public class MaceDmgModule extends Module {
    public MaceDmgModule() {
        super(WurstMeteorAddon.CATEGORY, "mace-dmg", "Spoofs vertical movement packets before mace hits for higher smash damage.");
    }

    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        if (mc.player == null || mc.getConnection() == null) return;
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.ENTITY) return;
        if (!mc.player.getMainHandItem().is(Items.MACE)) return;

        for (int i = 0; i < 4; i++) sendFakeY(0.0);
        sendFakeY(Math.sqrt(500.0));
        sendFakeY(0.0);
    }

    private void sendFakeY(double offset) {
        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(
            mc.player.getX(),
            mc.player.getY() + offset,
            mc.player.getZ(),
            false,
            mc.player.horizontalCollision
        ));
    }
}
