package de.njlent.wurstmeteor.modules.render;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

import java.util.ArrayList;
import java.util.function.Predicate;

public class TrajectoriesModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<SettingColor> missColor = sgGeneral.add(new ColorSetting.Builder()
        .name("miss-color")
        .description("Color of trajectories that miss.")
        .defaultValue(new SettingColor(128, 128, 128, 192))
        .build()
    );

    private final Setting<SettingColor> entityHitColor = sgGeneral.add(new ColorSetting.Builder()
        .name("entity-hit-color")
        .description("Color of trajectories that hit entities.")
        .defaultValue(new SettingColor(255, 0, 0, 192))
        .build()
    );

    private final Setting<SettingColor> blockHitColor = sgGeneral.add(new ColorSetting.Builder()
        .name("block-hit-color")
        .description("Color of trajectories that hit blocks.")
        .defaultValue(new SettingColor(0, 255, 0, 192))
        .build()
    );

    public TrajectoriesModule() {
        super(WurstMeteorAddon.CATEGORY, "wurst-trajectories", "Predicts trajectories for bows, tridents, and throwable items.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;

        Trajectory trajectory = getTrajectory(event.tickDelta);
        if (trajectory.isEmpty()) return;

        SettingColor selected = getColor(trajectory);
        Color lineColor = new Color(selected.r, selected.g, selected.b, 192);
        Color sideColor = new Color(selected.r, selected.g, selected.b, 64);

        ArrayList<Vec3> path = trajectory.path();
        for (int i = 1; i < path.size(); i++) {
            Vec3 from = path.get(i - 1);
            Vec3 to = path.get(i);
            event.renderer.line(from.x, from.y, from.z, to.x, to.y, to.z, lineColor);
        }

        AABB endBox = trajectory.getEndBox();
        event.renderer.box(endBox, sideColor, lineColor, meteordevelopment.meteorclient.renderer.ShapeMode.Both, 0);
    }

    private Trajectory getTrajectory(float tickDelta) {
        ArrayList<Vec3> path = new ArrayList<>();
        HitResult.Type type = HitResult.Type.MISS;

        InteractionHand hand = InteractionHand.MAIN_HAND;
        ItemStack stack = mc.player.getMainHandItem();
        if (!isThrowable(stack)) {
            hand = InteractionHand.OFF_HAND;
            stack = mc.player.getOffhandItem();
            if (!isThrowable(stack)) return new Trajectory(path, type);
        }

        Item item = stack.getItem();
        double throwPower = getThrowPower(item);
        double gravity = getProjectileGravity(item);
        ClipContext.Fluid fluidHandling = getFluidHandling(item);

        double yaw = Math.toRadians(mc.player.getYRot());
        double pitch = Math.toRadians(mc.player.getXRot());

        Vec3 arrowPos = getLerpedPos(mc.player, tickDelta).add(getHandOffset(hand, yaw));
        Vec3 arrowMotion = getStartingMotion(yaw, pitch, throwPower);

        for (int i = 0; i < 1000; i++) {
            path.add(arrowPos);

            arrowPos = arrowPos.add(arrowMotion.scale(0.1));
            arrowMotion = arrowMotion.scale(0.999);
            arrowMotion = arrowMotion.add(0, -gravity * 0.1, 0);

            Vec3 lastPos = path.size() > 1 ? path.get(path.size() - 2) : mc.player.getEyePosition();

            BlockHitResult bResult = mc.level.clip(new ClipContext(
                lastPos,
                arrowPos,
                ClipContext.Block.COLLIDER,
                fluidHandling,
                mc.player
            ));

            if (bResult.getType() != HitResult.Type.MISS) {
                type = HitResult.Type.BLOCK;
                path.set(path.size() - 1, bResult.getLocation());
                break;
            }

            AABB box = new AABB(lastPos, arrowPos);
            Predicate<Entity> predicate = e -> !e.isSpectator() && e.canBeHitByProjectile();
            double maxDistSq = 64 * 64;

            EntityHitResult eResult = ProjectileUtil.getEntityHitResult(mc.player, lastPos, arrowPos, box, predicate, maxDistSq);
            if (eResult != null && eResult.getType() != HitResult.Type.MISS) {
                type = HitResult.Type.ENTITY;
                path.set(path.size() - 1, eResult.getLocation());
                break;
            }
        }

        return new Trajectory(path, type);
    }

    private boolean isThrowable(ItemStack stack) {
        if (stack.isEmpty()) return false;

        Item item = stack.getItem();
        return item instanceof ProjectileWeaponItem
            || item instanceof SnowballItem
            || item instanceof EggItem
            || item instanceof EnderpearlItem
            || item instanceof ThrowablePotionItem
            || item instanceof FishingRodItem
            || item instanceof TridentItem;
    }

    private double getThrowPower(Item item) {
        if (!(item instanceof ProjectileWeaponItem)) return 1.5;

        float bowPower = (72000 - mc.player.getUseItemRemainingTicks()) / 20.0F;
        bowPower = bowPower * bowPower + bowPower * 2.0F;

        if (bowPower > 3 || bowPower <= 0.3F) bowPower = 3;

        return bowPower;
    }

    private double getProjectileGravity(Item item) {
        if (item instanceof ProjectileWeaponItem) return 0.05;
        if (item instanceof ThrowablePotionItem) return 0.4;
        if (item instanceof FishingRodItem) return 0.15;
        if (item instanceof TridentItem) return 0.015;
        return 0.03;
    }

    private ClipContext.Fluid getFluidHandling(Item item) {
        if (item instanceof FishingRodItem) return ClipContext.Fluid.ANY;
        return ClipContext.Fluid.NONE;
    }

    private Vec3 getHandOffset(InteractionHand hand, double yaw) {
        HumanoidArm mainArm = mc.options.mainHand().get();

        boolean rightSide = mainArm == HumanoidArm.RIGHT && hand == InteractionHand.MAIN_HAND
            || mainArm == HumanoidArm.LEFT && hand == InteractionHand.OFF_HAND;

        double sideMultiplier = rightSide ? -1 : 1;
        double handOffsetX = Math.cos(yaw) * 0.16 * sideMultiplier;
        double handOffsetY = mc.player.getEyeHeight(mc.player.getPose()) - 0.1;
        double handOffsetZ = Math.sin(yaw) * 0.16 * sideMultiplier;

        return new Vec3(handOffsetX, handOffsetY, handOffsetZ);
    }

    private Vec3 getStartingMotion(double yaw, double pitch, double throwPower) {
        double cosPitch = Math.cos(pitch);

        double motionX = -Math.sin(yaw) * cosPitch;
        double motionY = -Math.sin(pitch);
        double motionZ = Math.cos(yaw) * cosPitch;

        return new Vec3(motionX, motionY, motionZ).normalize().scale(throwPower);
    }

    private SettingColor getColor(Trajectory trajectory) {
        return switch (trajectory.type()) {
            case MISS -> missColor.get();
            case ENTITY -> entityHitColor.get();
            case BLOCK -> blockHitColor.get();
        };
    }

    private static Vec3 getLerpedPos(Entity entity, float tickDelta) {
        return new Vec3(
            Mth.lerp(tickDelta, entity.xOld, entity.getX()),
            Mth.lerp(tickDelta, entity.yOld, entity.getY()),
            Mth.lerp(tickDelta, entity.zOld, entity.getZ())
        );
    }

    private record Trajectory(ArrayList<Vec3> path, HitResult.Type type) {
        public boolean isEmpty() {
            return path.isEmpty();
        }

        public AABB getEndBox() {
            Vec3 end = path.get(path.size() - 1);
            return new AABB(end.subtract(0.5, 0.5, 0.5), end.add(0.5, 0.5, 0.5));
        }
    }
}
