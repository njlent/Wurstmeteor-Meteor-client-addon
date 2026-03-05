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
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.*;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

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
        if (mc.player == null || mc.world == null) return;

        Trajectory trajectory = getTrajectory(event.tickDelta);
        if (trajectory.isEmpty()) return;

        SettingColor selected = getColor(trajectory);
        Color lineColor = new Color(selected.r, selected.g, selected.b, 192);
        Color sideColor = new Color(selected.r, selected.g, selected.b, 64);

        ArrayList<Vec3d> path = trajectory.path();
        for (int i = 1; i < path.size(); i++) {
            Vec3d from = path.get(i - 1);
            Vec3d to = path.get(i);
            event.renderer.line(from.x, from.y, from.z, to.x, to.y, to.z, lineColor);
        }

        Box endBox = trajectory.getEndBox();
        event.renderer.box(endBox, sideColor, lineColor, meteordevelopment.meteorclient.renderer.ShapeMode.Both, 0);
    }

    private Trajectory getTrajectory(float tickDelta) {
        ArrayList<Vec3d> path = new ArrayList<>();
        HitResult.Type type = HitResult.Type.MISS;

        Hand hand = Hand.MAIN_HAND;
        ItemStack stack = mc.player.getMainHandStack();
        if (!isThrowable(stack)) {
            hand = Hand.OFF_HAND;
            stack = mc.player.getOffHandStack();
            if (!isThrowable(stack)) return new Trajectory(path, type);
        }

        Item item = stack.getItem();
        double throwPower = getThrowPower(item);
        double gravity = getProjectileGravity(item);
        RaycastContext.FluidHandling fluidHandling = getFluidHandling(item);

        double yaw = Math.toRadians(mc.player.getYaw());
        double pitch = Math.toRadians(mc.player.getPitch());

        Vec3d arrowPos = getLerpedPos(mc.player, tickDelta).add(getHandOffset(hand, yaw));
        Vec3d arrowMotion = getStartingMotion(yaw, pitch, throwPower);

        for (int i = 0; i < 1000; i++) {
            path.add(arrowPos);

            arrowPos = arrowPos.add(arrowMotion.multiply(0.1));
            arrowMotion = arrowMotion.multiply(0.999);
            arrowMotion = arrowMotion.add(0, -gravity * 0.1, 0);

            Vec3d lastPos = path.size() > 1 ? path.get(path.size() - 2) : mc.player.getEyePos();

            BlockHitResult bResult = mc.world.raycast(new RaycastContext(
                lastPos,
                arrowPos,
                RaycastContext.ShapeType.COLLIDER,
                fluidHandling,
                mc.player
            ));

            if (bResult.getType() != HitResult.Type.MISS) {
                type = HitResult.Type.BLOCK;
                path.set(path.size() - 1, bResult.getPos());
                break;
            }

            Box box = new Box(lastPos, arrowPos);
            Predicate<Entity> predicate = e -> !e.isSpectator() && e.canHit();
            double maxDistSq = 64 * 64;

            EntityHitResult eResult = ProjectileUtil.raycast(mc.player, lastPos, arrowPos, box, predicate, maxDistSq);
            if (eResult != null && eResult.getType() != HitResult.Type.MISS) {
                type = HitResult.Type.ENTITY;
                path.set(path.size() - 1, eResult.getPos());
                break;
            }
        }

        return new Trajectory(path, type);
    }

    private boolean isThrowable(ItemStack stack) {
        if (stack.isEmpty()) return false;

        Item item = stack.getItem();
        return item instanceof RangedWeaponItem
            || item instanceof SnowballItem
            || item instanceof EggItem
            || item instanceof EnderPearlItem
            || item instanceof ThrowablePotionItem
            || item instanceof FishingRodItem
            || item instanceof TridentItem;
    }

    private double getThrowPower(Item item) {
        if (!(item instanceof RangedWeaponItem)) return 1.5;

        float bowPower = (72000 - mc.player.getItemUseTimeLeft()) / 20.0F;
        bowPower = bowPower * bowPower + bowPower * 2.0F;

        if (bowPower > 3 || bowPower <= 0.3F) bowPower = 3;

        return bowPower;
    }

    private double getProjectileGravity(Item item) {
        if (item instanceof RangedWeaponItem) return 0.05;
        if (item instanceof ThrowablePotionItem) return 0.4;
        if (item instanceof FishingRodItem) return 0.15;
        if (item instanceof TridentItem) return 0.015;
        return 0.03;
    }

    private RaycastContext.FluidHandling getFluidHandling(Item item) {
        if (item instanceof FishingRodItem) return RaycastContext.FluidHandling.ANY;
        return RaycastContext.FluidHandling.NONE;
    }

    private Vec3d getHandOffset(Hand hand, double yaw) {
        Arm mainArm = mc.options.getMainArm().getValue();

        boolean rightSide = mainArm == Arm.RIGHT && hand == Hand.MAIN_HAND
            || mainArm == Arm.LEFT && hand == Hand.OFF_HAND;

        double sideMultiplier = rightSide ? -1 : 1;
        double handOffsetX = Math.cos(yaw) * 0.16 * sideMultiplier;
        double handOffsetY = mc.player.getEyeHeight(mc.player.getPose()) - 0.1;
        double handOffsetZ = Math.sin(yaw) * 0.16 * sideMultiplier;

        return new Vec3d(handOffsetX, handOffsetY, handOffsetZ);
    }

    private Vec3d getStartingMotion(double yaw, double pitch, double throwPower) {
        double cosPitch = Math.cos(pitch);

        double motionX = -Math.sin(yaw) * cosPitch;
        double motionY = -Math.sin(pitch);
        double motionZ = Math.cos(yaw) * cosPitch;

        return new Vec3d(motionX, motionY, motionZ).normalize().multiply(throwPower);
    }

    private SettingColor getColor(Trajectory trajectory) {
        return switch (trajectory.type()) {
            case MISS -> missColor.get();
            case ENTITY -> entityHitColor.get();
            case BLOCK -> blockHitColor.get();
        };
    }

    private static Vec3d getLerpedPos(Entity entity, float tickDelta) {
        return new Vec3d(
            MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()),
            MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()),
            MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ())
        );
    }

    private record Trajectory(ArrayList<Vec3d> path, HitResult.Type type) {
        public boolean isEmpty() {
            return path.isEmpty();
        }

        public Box getEndBox() {
            Vec3d end = path.get(path.size() - 1);
            return new Box(end.subtract(0.5, 0.5, 0.5), end.add(0.5, 0.5, 0.5));
        }
    }
}
