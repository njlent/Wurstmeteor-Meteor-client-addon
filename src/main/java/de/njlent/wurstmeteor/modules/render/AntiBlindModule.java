package de.njlent.wurstmeteor.modules.render;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.effect.MobEffects;

public class AntiBlindModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> blindness = sgGeneral.add(new BoolSetting.Builder()
        .name("blindness")
        .description("Removes the blindness effect client-side.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> darkness = sgGeneral.add(new BoolSetting.Builder()
        .name("darkness")
        .description("Removes the darkness effect client-side.")
        .defaultValue(true)
        .build()
    );

    public AntiBlindModule() {
        super(WurstMeteorAddon.CATEGORY, "anti-blind", "Prevents blindness and darkness from affecting your view.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;
        if (blindness.get()) mc.player.removeEffect(MobEffects.BLINDNESS);
        if (darkness.get()) mc.player.removeEffect(MobEffects.DARKNESS);
    }
}
