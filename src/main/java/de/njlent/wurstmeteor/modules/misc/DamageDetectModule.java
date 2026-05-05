package de.njlent.wurstmeteor.modules.misc;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

public class DamageDetectModule extends Module {
    private int lastHurtTime;

    public DamageDetectModule() {
        super(WurstMeteorAddon.CATEGORY, "damage-detect", "Prints the damage source and attacker location when you take damage.");
    }

    @Override
    public void onActivate() {
        lastHurtTime = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (player.hurtTime > lastHurtTime) info("DamageDetect: %s.", formatDamageSource(player.getLastDamageSource()));
        lastHurtTime = player.hurtTime;
    }

    private String formatDamageSource(DamageSource source) {
        if (source == null) return "unknown cause";

        String msg = source.getMsgId();
        if (msg == null || msg.isBlank()) msg = "damage";

        Entity actor = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
        if (actor != null) msg += " by " + actor.getName().getString() + " at " + formatLocation(actor);

        return msg;
    }

    private String formatLocation(Entity entity) {
        return String.format("(%d, %d, %d)", (int) Math.floor(entity.getX()), (int) Math.floor(entity.getY()), (int) Math.floor(entity.getZ()));
    }
}
