package de.njlent.wurstmeteor.modules.render;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.effect.MobEffects;

public class AntiWobbleModule extends Module {
    public AntiWobbleModule() {
        super(WurstMeteorAddon.CATEGORY, "anti-wobble", "Prevents nausea from wobbling the camera.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player != null) mc.player.removeEffect(MobEffects.NAUSEA);
    }
}
