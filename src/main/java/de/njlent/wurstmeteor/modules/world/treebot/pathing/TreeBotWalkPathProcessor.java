package de.njlent.wurstmeteor.modules.world.treebot.pathing;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.Jesus;
import net.minecraft.block.Block;
import net.minecraft.block.CobwebBlock;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.VineBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;

public class TreeBotWalkPathProcessor extends TreeBotPathProcessor {
    public TreeBotWalkPathProcessor(MinecraftClient mc, ArrayList<TreeBotPathPos> path) {
        super(mc, path);
    }

    @Override
    public void process() {
        BlockPos pos;
        if (mc.player.isOnGround()) {
            pos = BlockPos.ofFloored(mc.player.getX(), mc.player.getY() + 0.5, mc.player.getZ());
        } else {
            pos = BlockPos.ofFloored(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        }

        TreeBotPathPos nextPos = path.get(index);
        int posIndex = indexOfPos(pos);

        if (posIndex == -1) ticksOffPath++;
        else ticksOffPath = 0;

        if (pos.equals(nextPos)) {
            index++;
            if (index >= path.size()) done = true;
            return;
        }

        if (posIndex > index) {
            index = posIndex + 1;
            if (index >= path.size()) done = true;
            return;
        }

        lockControls(mc);
        mc.player.getAbilities().flying = false;

        facePosition(nextPos);
        if (Math.abs(MathHelper.wrapDegrees(getHorizontalAngleTo(Vec3d.ofCenter(nextPos)))) > 90) return;

        if (isJesusActive()) {
            if (mc.player.getY() < nextPos.getY() && (mc.player.isTouchingWater() || mc.player.isInLava())) return;

            if (mc.player.getY() - nextPos.getY() > 0.5
                && (mc.player.isTouchingWater() || mc.player.isInLava() || isOverLiquid())) {
                mc.options.sneakKey.setPressed(true);
            }
        }

        if (pos.getX() != nextPos.getX() || pos.getZ() != nextPos.getZ()) {
            mc.options.forwardKey.setPressed(true);

            if ((index > 0 && path.get(index - 1).isJumping()) || pos.getY() < nextPos.getY()) {
                mc.options.jumpKey.setPressed(true);
            }
        } else if (pos.getY() != nextPos.getY()) {
            if (pos.getY() < nextPos.getY()) {
                Block block = mc.world.getBlockState(pos).getBlock();

                if (block instanceof LadderBlock || block instanceof VineBlock) {
                    facePosition(pos);
                    mc.options.forwardKey.setPressed(true);
                } else {
                    if (index < path.size() - 1 && !nextPos.up().equals(path.get(index + 1))) index++;
                    mc.options.jumpKey.setPressed(true);
                }
            } else {
                while (index < path.size() - 1 && path.get(index).down().equals(path.get(index + 1))) index++;
                if (mc.player.isOnGround()) mc.options.forwardKey.setPressed(true);
            }
        }
    }

    @Override
    public boolean canBreakBlocks() {
        return mc.player.isOnGround();
    }

    private boolean isJesusActive() {
        Modules modules = Modules.get();
        return modules != null && modules.get(Jesus.class).isActive();
    }

    private boolean isOverLiquid() {
        BlockPos pos = mc.player.getBlockPos();
        Block feet = mc.world.getBlockState(pos).getBlock();
        Block below = mc.world.getBlockState(pos.down()).getBlock();

        return feet instanceof FluidBlock || below instanceof FluidBlock || feet instanceof CobwebBlock || below instanceof CobwebBlock;
    }
}
