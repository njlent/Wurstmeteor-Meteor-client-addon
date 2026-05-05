package de.njlent.wurstmeteor.modules.render;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

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
        .description("Finds the looked-at mob through walls when only-looked-at is enabled.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> onlyLookedAt = sgGeneral.add(new BoolSetting.Builder()
        .name("only-looked-at")
        .description("Only renders health for the mob you are looking at.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showNames = sgGeneral.add(new BoolSetting.Builder()
        .name("show-names")
        .description("Shows mob names above the health line.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showHearts = sgGeneral.add(new BoolSetting.Builder()
        .name("show-hearts")
        .description("Shows health as hearts instead of a number.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> maxDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-distance")
        .description("Maximum mob health render distance.")
        .defaultValue(48.0)
        .min(1.0)
        .sliderRange(8.0, 128.0)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Health overlay scale.")
        .defaultValue(1.0)
        .min(0.5)
        .sliderRange(0.5, 2.0)
        .build()
    );

    public MobHealthModule() {
        super(WurstMeteorAddon.CATEGORY, "mob-health", "Shows health above mobs.");
    }

    public boolean shouldDisplayFor(Mob mob) {
        if (!isActive() || mob == null || !mob.isAlive()) return false;
        if (ignoreNpcs.get() && mob.isNoAi()) return false;
        if (hostileOnly.get() && !(mob instanceof Enemy)) return false;
        return !onlyLookedAt.get() || getLookedAtMob() == mob;
    }

    public Component getDisplayText(Mob mob, MutableComponent existingName) {
        MutableComponent health = showHearts.get() ? hearts(mob) : Component.literal(Integer.toString(Math.round(Math.max(0.0F, mob.getHealth())))).withStyle(color(mob.getHealth()));
        if (!showNames.get()) return health;

        MutableComponent name = existingName != null ? existingName.copy() : mob.getName().copy();
        return name.append(Component.literal(" ")).append(health);
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.level == null) return;

        double maxDistanceSq = maxDistance.get() * maxDistance.get();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player || !(entity instanceof Mob mob)) continue;
            if (mc.player.distanceToSqr(mob) > maxDistanceSq) continue;
            if (!shouldDisplayFor(mob)) continue;

            Vector3d pos = new Vector3d(mob.getX(), mob.getBoundingBox().maxY + 0.35, mob.getZ());
            if (!NametagUtils.to2D(pos, scale.get())) continue;

            NametagUtils.begin(pos, event.graphics);
            TextRenderer renderer = TextRenderer.get();
            renderer.begin(1.0, false, true);

            String healthText = showHearts.get() ? heartsString(mob) : Integer.toString(Math.round(Math.max(0.0F, mob.getHealth())));
            double y = showNames.get() ? -renderer.getHeight() : -renderer.getHeight() / 2.0;
            if (showNames.get()) {
                String name = mob.getName().getString();
                renderer.render(name, -renderer.getWidth(name) / 2.0, y, Color.WHITE);
                y += renderer.getHeight();
            }

            renderer.render(healthText, -renderer.getWidth(healthText) / 2.0, y, healthColor(mob.getHealth()));
            renderer.end();
            NametagUtils.end(event.graphics);
        }
    }

    private MutableComponent hearts(Mob mob) {
        return Component.literal(heartsString(mob)).withStyle(color(mob.getHealth()));
    }

    private String heartsString(Mob mob) {
        int health = Math.max(0, Math.round(mob.getHealth()));
        int maxHealth = Math.max(health, Math.round(mob.getMaxHealth()));
        int fullHearts = Math.max(1, (int) Math.ceil(health / 2.0));
        int maxHearts = Math.max(fullHearts, (int) Math.ceil(maxHealth / 2.0));
        int shownFull = Math.min(fullHearts, 10);
        int shownEmpty = Math.max(0, Math.min(maxHearts, 10) - shownFull);
        String text = "\u2665".repeat(shownFull) + "\u2661".repeat(shownEmpty);
        return maxHearts > 10 ? text + " " + health : text;
    }

    private Color healthColor(float health) {
        if (health <= 5.0F) return new Color(170, 0, 0, 255);
        if (health <= 10.0F) return new Color(255, 170, 0, 255);
        if (health <= 15.0F) return new Color(255, 255, 85, 255);
        return new Color(85, 255, 85, 255);
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
