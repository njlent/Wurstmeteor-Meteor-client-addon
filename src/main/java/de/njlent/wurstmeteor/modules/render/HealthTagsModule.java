package de.njlent.wurstmeteor.modules.render;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public class HealthTagsModule extends Module {
    public HealthTagsModule() {
        super(WurstMeteorAddon.CATEGORY, "health-tags", "Adds colored health values to nametags.");
    }

    public Component addHealth(LivingEntity entity, MutableComponent nametag) {
        if (!isActive()) return nametag;

        int health = (int) entity.getHealth();
        MutableComponent formattedHealth = Component.literal(" " + health).withStyle(getColor(health));
        return nametag.append(formattedHealth);
    }

    private ChatFormatting getColor(int health) {
        if (health <= 5) return ChatFormatting.DARK_RED;
        if (health <= 10) return ChatFormatting.GOLD;
        if (health <= 15) return ChatFormatting.YELLOW;
        return ChatFormatting.GREEN;
    }
}
