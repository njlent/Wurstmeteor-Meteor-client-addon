package de.njlent.wurstmeteor.modules.world.treebot.pathing;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.Flight;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;

public class TreeBotFlyPathProcessor extends TreeBotPathProcessor {
    private final boolean creativeFlying;

    public TreeBotFlyPathProcessor(MinecraftClient mc, ArrayList<TreeBotPathPos> path, boolean creativeFlying) {
        super(mc, path);
        this.creativeFlying = creativeFlying;
    }

    @Override
    public void process() {
        BlockPos pos = BlockPos.ofFloored(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        Vec3d posVec = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        BlockPos nextPos = path.get(index);
        int posIndex = indexOfPos(pos);

        var nextBox = new net.minecraft.util.math.Box(
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
                Vec3d velocity = mc.player.getVelocity();
                mc.player.setVelocity(
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
            if (Math.abs(MathHelper.wrapDegrees(getHorizontalAngleTo(Vec3d.ofCenter(nextPos)))) > 1) return;
        }

        BlockPos offset = nextPos.subtract(pos);
        while (index < path.size() - 1 && path.get(index).add(offset).equals(path.get(index + 1))) index++;

        if (creativeFlying) {
            Vec3d velocity = mc.player.getVelocity();

            if (!x) {
                mc.player.setVelocity(velocity.x / Math.max(Math.abs(velocity.x) * 50, 1), velocity.y, velocity.z);
                velocity = mc.player.getVelocity();
            }

            if (!y) {
                mc.player.setVelocity(velocity.x, velocity.y / Math.max(Math.abs(velocity.y) * 50, 1), velocity.z);
                velocity = mc.player.getVelocity();
            }

            if (!z) mc.player.setVelocity(velocity.x, velocity.y, velocity.z / Math.max(Math.abs(velocity.z) * 50, 1));
        }

        Vec3d vecInPos = new Vec3d(nextPos.getX() + 0.5, nextPos.getY() + 0.1, nextPos.getZ() + 0.5);

        if (horizontal) {
            float horizontalSpeed = getHorizontalSpeed();
            if (!creativeFlying && horizontalSpeed > 0 && posVec.distanceTo(vecInPos) <= horizontalSpeed) {
                mc.player.setPosition(vecInPos);
                return;
            }

            mc.options.forwardKey.setPressed(true);

            if (mc.player.horizontalCollision) {
                if (posVec.y > nextBox.maxY) mc.options.sneakKey.setPressed(true);
                else if (posVec.y < nextBox.minY) mc.options.jumpKey.setPressed(true);
            }
        } else if (y) {
            float verticalSpeed = getVerticalSpeed();
            if (!creativeFlying && verticalSpeed > 0 && posVec.distanceTo(vecInPos) <= verticalSpeed) {
                mc.player.setPosition(vecInPos);
                return;
            }

            if (posVec.y < nextBox.minY) mc.options.jumpKey.setPressed(true);
            else mc.options.sneakKey.setPressed(true);

            if (mc.player.verticalCollision) {
                mc.options.sneakKey.setPressed(false);
                mc.options.forwardKey.setPressed(true);
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

        float speed = flight.getOffGroundSpeed();
        return Math.max(speed, 0);
    }

    private float getVerticalSpeed() {
        return Math.max((float) Math.abs(mc.player.getVelocity().y), getHorizontalSpeed() / 2f);
    }
}
