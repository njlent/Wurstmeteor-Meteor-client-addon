package de.njlent.wurstmeteor.modules.world.autolibrarian;

import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public record BlockHitSelection(BlockPos neighbor, Direction side, Vec3 hitPos, boolean lineOfSight, double distanceSq) {
    public BlockHitResult toHitResult() {
        return new BlockHitResult(hitPos, side, neighbor, false);
    }

    public boolean isBetterThan(BlockHitSelection other) {
        if (lineOfSight && !other.lineOfSight) return true;
        if (!lineOfSight && other.lineOfSight) return false;

        return distanceSq > other.distanceSq;
    }
}
