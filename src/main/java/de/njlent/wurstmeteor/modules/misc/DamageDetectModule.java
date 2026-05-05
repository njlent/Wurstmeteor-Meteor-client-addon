package de.njlent.wurstmeteor.modules.misc;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

public class DamageDetectModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> markAttacker = sgGeneral.add(new BoolSetting.Builder()
        .name("mark-attacker")
        .description("Highlights the entity that caused the most recent damage.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> markDuration = sgGeneral.add(new IntSetting.Builder()
        .name("mark-duration")
        .description("How long to keep the damage source highlighted, in ticks.")
        .defaultValue(100)
        .range(1, 600)
        .sliderRange(20, 200)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the damage source ESP box is rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("ESP color for the damage source.")
        .defaultValue(new SettingColor(255, 60, 60, 120))
        .build()
    );

    private int lastHurtTime;
    private Entity markedEntity;
    private int markedTicks;

    public DamageDetectModule() {
        super(WurstMeteorAddon.CATEGORY, "damage-detect", "Prints the damage source and attacker location when you take damage.");
    }

    @Override
    public void onActivate() {
        lastHurtTime = 0;
        markedEntity = null;
        markedTicks = 0;
    }

    @Override
    public void onDeactivate() {
        markedEntity = null;
        markedTicks = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (markedTicks > 0) markedTicks--;
        else markedEntity = null;

        if (player.hurtTime > lastHurtTime) {
            DamageSource source = player.getLastDamageSource();
            info("DamageDetect: %s.", formatDamageSource(source));
            markDamageSource(source);
        }

        lastHurtTime = player.hurtTime;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!markAttacker.get() || markedEntity == null || !markedEntity.isAlive() || markedEntity.isRemoved()) return;

        AABB box = getInterpolatedBox(markedEntity, event.tickDelta);
        Color side = new Color(color.get());
        Color line = new Color(color.get()).a(220);
        event.renderer.box(box, side, line, shapeMode.get(), 0);
    }

    private String formatDamageSource(DamageSource source) {
        if (source == null) return "unknown cause";

        String msg = source.getMsgId();
        if (msg == null || msg.isBlank()) msg = "damage";

        Entity actor = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
        if (actor != null) msg += " by " + actor.getName().getString() + " at " + formatLocation(actor);

        return msg;
    }

    private void markDamageSource(DamageSource source) {
        if (!markAttacker.get() || source == null) return;

        Entity actor = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
        if (actor == null || actor == mc.player) return;

        markedEntity = actor;
        markedTicks = markDuration.get();
    }

    private AABB getInterpolatedBox(Entity entity, float tickDelta) {
        double x = Mth.lerp(tickDelta, entity.xOld, entity.getX()) - entity.getX();
        double y = Mth.lerp(tickDelta, entity.yOld, entity.getY()) - entity.getY();
        double z = Mth.lerp(tickDelta, entity.zOld, entity.getZ()) - entity.getZ();
        return entity.getBoundingBox().move(x, y, z);
    }

    private String formatLocation(Entity entity) {
        return String.format("(%d, %d, %d)", (int) Math.floor(entity.getX()), (int) Math.floor(entity.getY()), (int) Math.floor(entity.getZ()));
    }
}
