package de.njlent.wurstmeteor.modules.world.treebot;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.ClipContext;

public final class BlockBreakingHelper {
    private BlockBreakingHelper() {
    }

    public static BlockBreakingParams getBlockBreakingParams(Minecraft mc, BlockPos pos) {
        return getBlockBreakingParams(mc, mc.player.getEyePosition(), pos);
    }

    public static BlockBreakingParams getBlockBreakingParams(Minecraft mc, Vec3 eyes, BlockPos pos) {
        Direction[] sides = Direction.values();

        BlockState state = mc.level.getBlockState(pos);
        VoxelShape shape = state.getShape(mc.level, pos);
        if (shape.isEmpty()) return null;

        var box = shape.bounds();
        Vec3 halfSize = new Vec3(box.maxX - box.minX, box.maxY - box.minY, box.maxZ - box.minZ).scale(0.5);
        Vec3 center = Vec3.atLowerCornerOf(pos).add(box.getCenter());

        Vec3[] hitVecs = new Vec3[sides.length];
        for (int i = 0; i < sides.length; i++) {
            var dirVec = sides[i].step();
            Vec3 relHitVec = new Vec3(
                halfSize.x * dirVec.x,
                halfSize.y * dirVec.y,
                halfSize.z * dirVec.z
            );
            hitVecs[i] = center.add(relHitVec);
        }

        double distanceSqToCenter = eyes.distanceToSqr(center);
        double[] distancesSq = new double[sides.length];
        boolean[] linesOfSight = new boolean[sides.length];

        for (int i = 0; i < sides.length; i++) {
            distancesSq[i] = eyes.distanceToSqr(hitVecs[i]);

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

    public static boolean hasLineOfSight(Minecraft mc, Vec3 from, Vec3 to) {
        HitResult hitResult = mc.level.clip(new ClipContext(
            from,
            to,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            mc.player
        ));

        return hitResult.getType() == HitResult.Type.MISS;
    }

    public record BlockBreakingParams(
        BlockPos pos,
        Direction side,
        Vec3 hitPos,
        double distanceSq,
        boolean lineOfSight
    ) {
        public BlockHitResult toHitResult() {
            return new BlockHitResult(hitPos, side, pos, false);
        }
    }
}
