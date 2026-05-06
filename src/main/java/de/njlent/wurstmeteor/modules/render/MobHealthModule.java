package de.njlent.wurstmeteor.modules.render;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class MobHealthModule extends Module {
    private static final Identifier HEART_CONTAINER = Identifier.withDefaultNamespace("hud/heart/container");
    private static final Identifier HEART_FULL = Identifier.withDefaultNamespace("hud/heart/full");
    private static final Identifier HEART_HALF = Identifier.withDefaultNamespace("hud/heart/half");

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
        .description("Shows mob health through walls and finds the looked-at mob through walls.")
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
        if (isExcludedMob(mob)) return false;
        if (hostileOnly.get() && !(mob instanceof Enemy)) return false;
        if (!throughWalls.get() && mc.player != null && !mc.player.hasLineOfSight(mob, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, maxDistance.get())) return false;
        return !onlyLookedAt.get() || getLookedAtMob() == mob;
    }

    public Component getDisplayText(Mob mob, MutableComponent existingName) {
        MutableComponent health = Component.literal(Integer.toString(Math.round(Math.max(0.0F, mob.getHealth())))).withStyle(color(mob.getHealth()));
        if (showHearts.get()) {
            return existingName != null ? existingName.copy() : mob.getName().copy();
        }

        if (!showNames.get()) return health;

        MutableComponent name = existingName != null ? existingName.copy() : mob.getName().copy();
        return name.append(Component.literal(" ")).append(health);
    }

    public boolean shouldForceNameTag() {
        return !showHearts.get() || showNames.get();
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!showHearts.get() || mc.player == null || mc.level == null) return;

        double maxDistanceSq = maxDistance.get() * maxDistance.get();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player || !(entity instanceof Mob mob)) continue;
            if (mc.player.distanceToSqr(mob) > maxDistanceSq) continue;
            if (!shouldDisplayFor(mob)) continue;
            drawHeartsAtEntity(event, mob);
        }
    }

    private void drawHeartsAtEntity(Render2DEvent event, Mob mob) {
        GuiGraphicsExtractor graphics = event.graphics;
        Vec3 worldPos = new Vec3(
            Mth.lerp(event.tickDelta, mob.xOld, mob.getX()),
            Mth.lerp(event.tickDelta, mob.yOld, mob.getY()) + mob.getBbHeight() + (showNames.get() ? 0.35 : 0.2),
            Mth.lerp(event.tickDelta, mob.zOld, mob.getZ())
        );

        Vec3 projected = mc.gameRenderer.projectPointToScreen(worldPos);
        if (projected.z <= -1 || projected.z >= 1 || projected.x <= -1 || projected.x >= 1 || projected.y <= -1 || projected.y >= 1) return;

        float x = (float) ((projected.x + 1.0) * 0.5 * graphics.guiWidth());
        float y = (float) ((1.0 - (projected.y + 1.0) * 0.5) * graphics.guiHeight());
        double distance = mc.gameRenderer.getMainCamera().position().distanceTo(worldPos);
        float heartScale = Mth.clamp(scale.get().floatValue() * (distance > 12.0 ? (float) (12.0 / distance) : 1.0F), 0.25F, 1.6F);
        drawHeartRow(graphics, x, y, heartScale, mob);
    }

    private void drawHeartRow(GuiGraphicsExtractor graphics, float centerX, float y, float heartScale, Mob mob) {
        float currentHealth = Math.max(0.0F, mob.getHealth());
        float maxHealth = Math.max(1.0F, mob.getMaxHealth());
        int slots = Math.min(10, Math.max(1, Mth.ceil(maxHealth / 2.0F)));
        int filledHalfHearts = Mth.clamp(Math.round(currentHealth), 0, slots * 2);
        float width = ((slots - 1) * 8 + 9) * heartScale;
        float x = centerX - width / 2.0F;

        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(heartScale, heartScale);

        for (int i = 0; i < slots; i++) {
            int heartX = i * 8;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_CONTAINER, heartX, 0, 9, 9);

            int halfIndex = i * 2;
            if (filledHalfHearts >= halfIndex + 2) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_FULL, heartX, 0, 9, 9);
            } else if (filledHalfHearts == halfIndex + 1) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_HALF, heartX, 0, 9, 9);
            }
        }

        graphics.pose().popMatrix();
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

    private boolean isExcludedMob(Entity entity) {
        if (entity instanceof ArmorStand) return true;
        return ignoreNpcs.get() && entity instanceof Mob mob && mob.isNoAi();
    }
}
