package de.njlent.wurstmeteor.modules.world.treebot.pathing;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.Flight;
import meteordevelopment.meteorclient.systems.modules.movement.Jesus;
import meteordevelopment.meteorclient.systems.modules.movement.NoFall;
import meteordevelopment.meteorclient.systems.modules.movement.Spider;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerAbilities;

public record TreeBotPlayerAbilities(
    boolean invulnerable,
    boolean creativeFlying,
    boolean flying,
    boolean immuneToFallDamage,
    boolean noWaterSlowdown,
    boolean jesus,
    boolean spider
) {
    public static TreeBotPlayerAbilities get(MinecraftClient mc) {
        Modules modules = Modules.get();

        boolean flight = modules != null && modules.get(Flight.class).isActive();
        boolean noFall = modules != null && modules.get(NoFall.class).isActive();
        boolean jesus = modules != null && modules.get(Jesus.class).isActive();
        boolean spider = modules != null && modules.get(Spider.class).isActive();

        PlayerAbilities mcAbilities = mc.player.getAbilities();

        boolean invulnerable = mcAbilities.invulnerable || mcAbilities.creativeMode;
        boolean creativeFlying = mcAbilities.flying;
        boolean flying = creativeFlying || flight;
        boolean immuneToFallDamage = invulnerable || noFall;

        return new TreeBotPlayerAbilities(
            invulnerable,
            creativeFlying,
            flying,
            immuneToFallDamage,
            false,
            jesus,
            spider
        );
    }
}
