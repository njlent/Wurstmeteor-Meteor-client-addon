package de.njlent.wurstmeteor.modules.combat;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.entity.player.StoppedUsingItemEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

public class ArrowDmgModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> strength = sgGeneral.add(new DoubleSetting.Builder()
        .name("strength")
        .description("Packet offset strength.")
        .defaultValue(10)
        .min(0.1)
        .sliderRange(0.1, 10)
        .decimalPlaces(1)
        .build()
    );

    private final Setting<Boolean> yeetTridents = sgGeneral.add(new BoolSetting.Builder()
        .name("trident-yeet-mode")
        .description("Also applies ArrowDMG packets when throwing tridents.")
        .defaultValue(false)
        .build()
    );

    public ArrowDmgModule() {
        super(WurstMeteorAddon.CATEGORY, "arrow-dmg", "Increases arrow/trident damage by spoofing movement packets on release.");
    }

    @EventHandler
    private void onStoppedUsingItem(StoppedUsingItemEvent event) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (!isValidItem(mc.player.getMainHandStack().getItem())) return;

        mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));

        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();

        double adjustedStrength = strength.get() / 10.0 * Math.sqrt(500.0);
        Vec3d lookVec = mc.player.getRotationVec(1.0F).multiply(adjustedStrength);

        for (int i = 0; i < 4; i++) sendPos(x, y, z, true);

        sendPos(x - lookVec.x, y, z - lookVec.z, true);
        sendPos(x, y, z, false);
    }

    private void sendPos(double x, double y, double z, boolean onGround) {
        if (mc.getNetworkHandler() == null || mc.player == null) return;

        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
            x,
            y,
            z,
            onGround,
            mc.player.horizontalCollision
        ));
    }

    private boolean isValidItem(Item item) {
        if (yeetTridents.get() && item == Items.TRIDENT) return true;
        return item == Items.BOW;
    }
}
