package de.njlent.wurstmeteor.modules.world.autolibrarian;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public record BlockHitSelection(BlockPos neighbor, Direction side, Vec3d hitPos, boolean lineOfSight, double distanceSq) {
    public BlockHitResult toHitResult() {
        return new BlockHitResult(hitPos, side, neighbor, false);
    }

    public boolean isBetterThan(BlockHitSelection other) {
        if (lineOfSight && !other.lineOfSight) return true;
        if (!lineOfSight && other.lineOfSight) return false;

        return distanceSq > other.distanceSq;
    }
}
