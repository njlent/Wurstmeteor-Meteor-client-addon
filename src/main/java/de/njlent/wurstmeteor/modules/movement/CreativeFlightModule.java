package de.njlent.wurstmeteor.modules.movement;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import de.njlent.wurstmeteor.util.KeyBindingUtils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.Flight;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.phys.Vec3;

public class CreativeFlightModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> antiKick = sgGeneral.add(new BoolSetting.Builder()
        .name("anti-kick")
        .description("Makes you briefly fall and rise to reset fly kick timers.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> antiKickInterval = sgGeneral.add(new IntSetting.Builder()
        .name("anti-kick-interval")
        .description("How often anti-kick runs, in ticks.")
        .defaultValue(30)
        .range(5, 80)
        .sliderRange(5, 80)
        .build()
    );

    private final Setting<Double> antiKickDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("anti-kick-distance")
        .description("How far anti-kick moves you each pulse.")
        .defaultValue(0.07)
        .min(0.01)
        .sliderRange(0.01, 0.2)
        .decimalPlaces(3)
        .build()
    );

    private int tickCounter;

    public CreativeFlightModule() {
        super(WurstMeteorAddon.CATEGORY, "creative-flight", "Enables creative-style flight, including optional anti-kick pulses.");
    }

    @Override
    public void onActivate() {
        tickCounter = 0;

        Flight flight = Modules.get().get(Flight.class);
        if (flight != null && flight.isActive()) flight.toggle();
    }

    @Override
    public void onDeactivate() {
        if (mc.player == null) return;

        Abilities abilities = mc.player.getAbilities();
        boolean creative = abilities.instabuild;

        abilities.flying = creative && !mc.player.onGround();
        abilities.mayfly = creative;

        restoreKeyPresses();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        Abilities abilities = mc.player.getAbilities();
        abilities.mayfly = true;

        if (antiKick.get() && abilities.flying) doAntiKick();
    }

    private void doAntiKick() {
        if (tickCounter > antiKickInterval.get() + 2) tickCounter = 0;

        switch (tickCounter) {
            case 0 -> {
                if (mc.options.keyShift.isDown() && !mc.options.keyJump.isDown()) {
                    tickCounter = 3;
                } else {
                    setMotionY(-antiKickDistance.get());
                }
            }
            case 1 -> setMotionY(antiKickDistance.get());
            case 2 -> setMotionY(0);
            case 3 -> restoreKeyPresses();
            default -> {
            }
        }

        tickCounter++;
    }

    private void setMotionY(double motionY) {
        if (mc.player == null) return;

        mc.options.keyShift.setDown(false);
        mc.options.keyJump.setDown(false);

        Vec3 velocity = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(velocity.x, motionY, velocity.z);
    }

    private void restoreKeyPresses() {
        KeyBindingUtils.resetPressedState(mc.options.keyJump);
        KeyBindingUtils.resetPressedState(mc.options.keyShift);
    }
}
