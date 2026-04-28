package de.njlent.wurstmeteor.modules.combat;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class CriticalsModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("How critical hits are triggered.")
        .defaultValue(Mode.Packet)
        .build()
    );

    public CriticalsModule() {
        super(WurstMeteorAddon.CATEGORY, "criticals", "Forces critical hits when attacking valid targets.");
    }

    @Override
    public String getInfoString() {
        return mode.get().toString();
    }

    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        if (mc.player == null || mc.getConnection() == null) return;
        if (!(event.entity instanceof LivingEntity)) return;

        MaceDmgModule maceDmg = Modules.get().get(MaceDmgModule.class);
        if (maceDmg != null && maceDmg.isActive() && mc.player.getMainHandItem().is(Items.MACE)) return;

        if (!mc.player.onGround()) return;
        if (mc.player.isUnderWater() || mc.player.isInLava() || mc.player.onClimbable()) return;

        switch (mode.get()) {
            case Packet -> doPacketJump();
            case MiniJump -> doMiniJump();
            case FullJump -> mc.player.jumpFromGround();
        }
    }

    private void doPacketJump() {
        sendFakeY(0.0625, true);
        sendFakeY(0.0, false);
        sendFakeY(1.1e-5, false);
        sendFakeY(0.0, false);
    }

    private void sendFakeY(double offset, boolean onGround) {
        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(
            mc.player.getX(),
            mc.player.getY() + offset,
            mc.player.getZ(),
            onGround,
            mc.player.horizontalCollision
        ));
    }

    private void doMiniJump() {
        mc.player.addDeltaMovement(new net.minecraft.world.phys.Vec3(0.0, 0.1, 0.0));
        mc.player.fallDistance = 0.1F;
        mc.player.setOnGround(false);
    }

    public enum Mode {
        Packet("Packet"),
        MiniJump("Mini Jump"),
        FullJump("Full Jump");

        private final String title;

        Mode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }
}
