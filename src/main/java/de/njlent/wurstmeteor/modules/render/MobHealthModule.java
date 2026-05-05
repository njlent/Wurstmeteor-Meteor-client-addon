package de.njlent.wurstmeteor.modules.render;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;

public class MobHealthModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> hostileOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("hostile-only")
        .description("Only shows health for hostile mobs.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ignoreNpcs = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-npcs")
        .description("Ignores likely server NPC mobs with no AI.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> throughWalls = sgGeneral.add(new BoolSetting.Builder()
        .name("through-walls")
        .description("Finds the looked-at mob through walls.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showNames = sgGeneral.add(new BoolSetting.Builder()
        .name("show-names")
        .description("Keeps the mob name next to its health.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showHearts = sgGeneral.add(new BoolSetting.Builder()
        .name("show-hearts")
        .description("Shows health as hearts instead of a number.")
        .defaultValue(true)
        .build()
    );

    public MobHealthModule() {
        super(WurstMeteorAddon.CATEGORY, "mob-health", "Shows health for the mob you are looking at.");
    }

    public boolean shouldDisplayFor(Mob mob) {
        if (!isActive() || mob == null || !mob.isAlive()) return false;
        if (ignoreNpcs.get() && mob.isNoAi()) return false;
        if (hostileOnly.get() && !(mob instanceof Enemy)) return false;
        return getLookedAtMob() == mob;
    }

    public Component getDisplayText(Mob mob, MutableComponent existingName) {
        MutableComponent health = showHearts.get() ? hearts(mob) : Component.literal(Integer.toString(Math.round(Math.max(0.0F, mob.getHealth())))).withStyle(color(mob.getHealth()));
        if (!showNames.get()) return health;

        MutableComponent name = existingName != null ? existingName.copy() : mob.getName().copy();
        return name.append(Component.literal(" ")).append(health);
    }

    private MutableComponent hearts(Mob mob) {
        int health = Math.max(0, Math.round(mob.getHealth()));
        int maxHealth = Math.max(health, Math.round(mob.getMaxHealth()));
        int fullHearts = Math.max(1, (int) Math.ceil(health / 2.0));
        int maxHearts = Math.max(fullHearts, (int) Math.ceil(maxHealth / 2.0));
        int shownFull = Math.min(fullHearts, 10);
        int shownEmpty = Math.max(0, Math.min(maxHearts, 10) - shownFull);

        String text = "♥".repeat(shownFull) + "♡".repeat(shownEmpty);
        if (maxHearts > 10) text += " " + health;
        return Component.literal(text).withStyle(color(health));
    }

    private ChatFormatting color(float health) {
        if (health <= 5.0F) return ChatFormatting.DARK_RED;
        if (health <= 10.0F) return ChatFormatting.GOLD;
        if (health <= 15.0F) return ChatFormatting.YELLOW;
        return ChatFormatting.GREEN;
    }

    private Mob getLookedAtMob() {
        if (mc.player == null || mc.level == null) return null;

        if (!throughWalls.get()) {
            Entity target = mc.crosshairPickEntity;
            return target instanceof Mob mob ? mob : null;
        }

        double range = mc.player.entityInteractionRange();
        Vec3 start = mc.player.getEyePosition(1.0F);
        Vec3 look = mc.player.getViewVector(1.0F);
        Vec3 end = start.add(look.scale(range));
        AABB box = mc.player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(mc.player, start, end, box, entity -> entity instanceof Mob && entity.isAlive(), range * range);
        return hit != null && hit.getEntity() instanceof Mob mob ? mob : null;
    }
}
