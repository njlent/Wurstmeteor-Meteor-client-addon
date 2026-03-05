package de.njlent.wurstmeteor.modules.render;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class HealthTagsModule extends Module {
    public HealthTagsModule() {
        super(WurstMeteorAddon.CATEGORY, "health-tags", "Adds colored health values to nametags.");
    }

    public Text addHealth(LivingEntity entity, MutableText nametag) {
        if (!isActive()) return nametag;

        int health = (int) entity.getHealth();
        MutableText formattedHealth = Text.literal(" " + health).formatted(getColor(health));
        return nametag.append(formattedHealth);
    }

    private Formatting getColor(int health) {
        if (health <= 5) return Formatting.DARK_RED;
        if (health <= 10) return Formatting.GOLD;
        if (health <= 15) return Formatting.YELLOW;
        return Formatting.GREEN;
    }
}
