package de.njlent.wurstmeteor.modules.movement;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class ExtraElytraModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> instantFly = sgGeneral.add(new BoolSetting.Builder()
        .name("instant-fly")
        .description("Starts elytra flight from a single jump press.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> speedControl = sgGeneral.add(new BoolSetting.Builder()
        .name("speed-control")
        .description("Uses forward/back keys to control elytra speed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> heightControl = sgGeneral.add(new BoolSetting.Builder()
        .name("height-control")
        .description("Uses jump/sneak keys to control elytra height.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> stopInWater = sgGeneral.add(new BoolSetting.Builder()
        .name("stop-in-water")
        .description("Stops fall-flying when entering water.")
        .defaultValue(true)
        .build()
    );

    private int jumpTimer;

    public ExtraElytraModule() {
        super(WurstMeteorAddon.CATEGORY, "extra-elytra", "Adds instant elytra launch plus optional speed and height controls.");
    }

    @Override
    public void onActivate() {
        jumpTimer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.player.connection == null) return;
        if (jumpTimer > 0) jumpTimer--;
        if (!Player.canGlideUsing(mc.player.getItemBySlot(EquipmentSlot.CHEST), EquipmentSlot.CHEST)) return;

        if (mc.player.isFallFlying()) {
            if (stopInWater.get() && mc.player.isInWater()) {
                sendStartFallFlyingPacket();
                return;
            }

            controlSpeed();
            controlHeight();
            return;
        }

        if (mc.options.keyJump.isDown()) doInstantFly();
    }

    private void doInstantFly() {
        if (!instantFly.get()) return;

        if (jumpTimer <= 0) {
            jumpTimer = 20;
            mc.player.setJumping(false);
            mc.player.setSprinting(true);
            mc.player.jumpFromGround();
        }

        sendStartFallFlyingPacket();
    }

    private void sendStartFallFlyingPacket() {
        mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
    }

    private void controlSpeed() {
        if (!speedControl.get()) return;

        float yaw = (float) Math.toRadians(mc.player.getYRot());
        Vec3 forward = new Vec3(-Mth.sin(yaw) * 0.05, 0, Mth.cos(yaw) * 0.05);
        Vec3 velocity = mc.player.getDeltaMovement();

        if (mc.options.keyUp.isDown()) mc.player.setDeltaMovement(velocity.add(forward));
        else if (mc.options.keyDown.isDown()) mc.player.setDeltaMovement(velocity.subtract(forward));
    }

    private void controlHeight() {
        if (!heightControl.get()) return;

        Vec3 velocity = mc.player.getDeltaMovement();
        boolean jump = mc.options.keyJump.isDown();
        boolean sneak = mc.options.keyShift.isDown();

        if (sneak) mc.options.keyShift.setDown(false);

        if (jump && !sneak) mc.player.setDeltaMovement(velocity.x, velocity.y + 0.08, velocity.z);
        else if (sneak && !jump) mc.player.setDeltaMovement(velocity.x, velocity.y - 0.04, velocity.z);
    }
}
