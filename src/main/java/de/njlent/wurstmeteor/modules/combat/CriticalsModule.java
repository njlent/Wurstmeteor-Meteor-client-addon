package de.njlent.wurstmeteor.modules.combat;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public class CriticalsModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("How critical hits are triggered.")
        .defaultValue(Mode.Packet)
        .build()
    );

    public CriticalsModule() {
        super(WurstMeteorAddon.CATEGORY, "criticals", "Forces critical hits when attacking valid targets, from Wurstmeter Addon.");
    }

    @Override
    public String getInfoString() {
        return mode.get().toString();
    }

    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (!(event.entity instanceof LivingEntity)) return;

        MaceDmgModule maceDmg = Modules.get().get(MaceDmgModule.class);
        if (maceDmg != null && maceDmg.isActive() && mc.player.getMainHandStack().isOf(Items.MACE)) return;

        if (!mc.player.isOnGround()) return;
        if (mc.player.isSubmergedInWater() || mc.player.isInLava() || mc.player.isClimbing()) return;

        switch (mode.get()) {
            case Packet -> doPacketJump();
            case MiniJump -> doMiniJump();
            case FullJump -> mc.player.jump();
        }
    }

    private void doPacketJump() {
        sendFakeY(0.0625, true);
        sendFakeY(0.0, false);
        sendFakeY(1.1e-5, false);
        sendFakeY(0.0, false);
    }

    private void sendFakeY(double offset, boolean onGround) {
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
            mc.player.getX(),
            mc.player.getY() + offset,
            mc.player.getZ(),
            onGround,
            mc.player.horizontalCollision
        ));
    }

    private void doMiniJump() {
        mc.player.addVelocity(0.0, 0.1, 0.0);
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
