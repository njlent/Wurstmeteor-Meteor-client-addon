package de.njlent.wurstmeteor.modules.world.treebot;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;

public final class BlockBreakingHelper {
    private BlockBreakingHelper() {
    }

    public static BlockBreakingParams getBlockBreakingParams(MinecraftClient mc, BlockPos pos) {
        return getBlockBreakingParams(mc, mc.player.getEyePos(), pos);
    }

    public static BlockBreakingParams getBlockBreakingParams(MinecraftClient mc, Vec3d eyes, BlockPos pos) {
        Direction[] sides = Direction.values();

        BlockState state = mc.world.getBlockState(pos);
        VoxelShape shape = state.getOutlineShape(mc.world, pos);
        if (shape.isEmpty()) return null;

        var box = shape.getBoundingBox();
        Vec3d halfSize = new Vec3d(box.maxX - box.minX, box.maxY - box.minY, box.maxZ - box.minZ).multiply(0.5);
        Vec3d center = Vec3d.of(pos).add(box.getCenter());

        Vec3d[] hitVecs = new Vec3d[sides.length];
        for (int i = 0; i < sides.length; i++) {
            var dirVec = sides[i].getVector();
            Vec3d relHitVec = new Vec3d(
                halfSize.x * dirVec.getX(),
                halfSize.y * dirVec.getY(),
                halfSize.z * dirVec.getZ()
            );
            hitVecs[i] = center.add(relHitVec);
        }

        double distanceSqToCenter = eyes.squaredDistanceTo(center);
        double[] distancesSq = new double[sides.length];
        boolean[] linesOfSight = new boolean[sides.length];

        for (int i = 0; i < sides.length; i++) {
            distancesSq[i] = eyes.squaredDistanceTo(hitVecs[i]);

            if (distancesSq[i] >= distanceSqToCenter) continue;
            linesOfSight[i] = hasLineOfSight(mc, eyes, hitVecs[i]);
        }

        Direction side = sides[0];
        for (int i = 1; i < sides.length; i++) {
            int bestSide = side.ordinal();

            if (!linesOfSight[bestSide] && linesOfSight[i]) {
                side = sides[i];
                continue;
            }

            if (linesOfSight[bestSide] && !linesOfSight[i]) continue;
            if (distancesSq[i] < distancesSq[bestSide]) side = sides[i];
        }

        return new BlockBreakingParams(pos, side, hitVecs[side.ordinal()], distancesSq[side.ordinal()], linesOfSight[side.ordinal()]);
    }

    public static boolean hasLineOfSight(MinecraftClient mc, Vec3d from, Vec3d to) {
        HitResult hitResult = mc.world.raycast(new RaycastContext(
            from,
            to,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            mc.player
        ));

        return hitResult.getType() == HitResult.Type.MISS;
    }

    public record BlockBreakingParams(
        BlockPos pos,
        Direction side,
        Vec3d hitPos,
        double distanceSq,
        boolean lineOfSight
    ) {
        public BlockHitResult toHitResult() {
            return new BlockHitResult(hitPos, side, pos, false);
        }
    }
}
