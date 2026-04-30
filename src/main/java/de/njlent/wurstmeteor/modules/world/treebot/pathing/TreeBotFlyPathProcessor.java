package de.njlent.wurstmeteor.modules.world.treebot.pathing;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.Flight;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;

public class TreeBotFlyPathProcessor extends TreeBotPathProcessor {
    private final boolean creativeFlying;

    public TreeBotFlyPathProcessor(Minecraft mc, ArrayList<TreeBotPathPos> path, boolean creativeFlying) {
        super(mc, path);
        this.creativeFlying = creativeFlying;
    }

    @Override
    public void process() {
        BlockPos pos = BlockPos.containing(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        Vec3 posVec = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        BlockPos nextPos = path.get(index);
        int posIndex = indexOfPos(pos);

        var nextBox = new net.minecraft.world.phys.AABB(
            nextPos.getX() + 0.3,
            nextPos.getY(),
            nextPos.getZ() + 0.3,
            nextPos.getX() + 0.7,
            nextPos.getY() + 0.2,
            nextPos.getZ() + 0.7
        );

        if (posIndex == -1) ticksOffPath++;
        else ticksOffPath = 0;

        if (posIndex > index || nextBox.contains(posVec)) {
            if (posIndex > index) index = posIndex + 1;
            else index++;

            if (creativeFlying) {
                Vec3 velocity = mc.player.getDeltaMovement();
                mc.player.setDeltaMovement(
                    velocity.x / Math.max(Math.abs(velocity.x) * 50, 1),
                    velocity.y / Math.max(Math.abs(velocity.y) * 50, 1),
                    velocity.z / Math.max(Math.abs(velocity.z) * 50, 1)
                );
            }

            if (index >= path.size()) done = true;
            return;
        }

        lockControls(mc);
        mc.player.getAbilities().flying = creativeFlying;

        boolean x = posVec.x < nextBox.minX || posVec.x > nextBox.maxX;
        boolean y = posVec.y < nextBox.minY || posVec.y > nextBox.maxY;
        boolean z = posVec.z < nextBox.minZ || posVec.z > nextBox.maxZ;
        boolean horizontal = x || z;

        if (horizontal) {
            facePosition(nextPos);
            if (Math.abs(Mth.wrapDegrees(getHorizontalAngleTo(Vec3.atCenterOf(nextPos)))) > 1) return;
        }

        BlockPos offset = nextPos.subtract(pos);
        while (index < path.size() - 1 && path.get(index).offset(offset).equals(path.get(index + 1))) index++;

        if (creativeFlying) {
            Vec3 velocity = mc.player.getDeltaMovement();

            if (!x) {
                mc.player.setDeltaMovement(velocity.x / Math.max(Math.abs(velocity.x) * 50, 1), velocity.y, velocity.z);
                velocity = mc.player.getDeltaMovement();
            }

            if (!y) {
                mc.player.setDeltaMovement(velocity.x, velocity.y / Math.max(Math.abs(velocity.y) * 50, 1), velocity.z);
                velocity = mc.player.getDeltaMovement();
            }

            if (!z) mc.player.setDeltaMovement(velocity.x, velocity.y, velocity.z / Math.max(Math.abs(velocity.z) * 50, 1));
        }

        Vec3 vecInPos = new Vec3(nextPos.getX() + 0.5, nextPos.getY() + 0.1, nextPos.getZ() + 0.5);

        if (horizontal) {
            float horizontalSpeed = getHorizontalSpeed();
            if (!creativeFlying && horizontalSpeed > 0 && posVec.distanceTo(vecInPos) <= horizontalSpeed) {
                mc.player.setPos(vecInPos);
                return;
            }

            mc.options.keyUp.setDown(true);

            if (mc.player.horizontalCollision) {
                if (posVec.y > nextBox.maxY) mc.options.keyShift.setDown(true);
                else if (posVec.y < nextBox.minY) mc.options.keyJump.setDown(true);
            }
        } else if (y) {
            float verticalSpeed = getVerticalSpeed();
            if (!creativeFlying && verticalSpeed > 0 && posVec.distanceTo(vecInPos) <= verticalSpeed) {
                mc.player.setPos(vecInPos);
                return;
            }

            if (posVec.y < nextBox.minY) mc.options.keyJump.setDown(true);
            else mc.options.keyShift.setDown(true);

            if (mc.player.verticalCollision) {
                mc.options.keyShift.setDown(false);
                mc.options.keyUp.setDown(true);
            }
        }
    }

    @Override
    public boolean canBreakBlocks() {
        return true;
    }

    private float getHorizontalSpeed() {
        Modules modules = Modules.get();
        if (modules == null) return 0;

        Flight flight = modules.get(Flight.class);
        if (flight == null) return 0;

        float speed = flight.getFlyingSpeed();
        return Math.max(speed, 0);
    }

    private float getVerticalSpeed() {
        return Math.max((float) Math.abs(mc.player.getDeltaMovement().y), getHorizontalSpeed() / 2f);
    }
}
