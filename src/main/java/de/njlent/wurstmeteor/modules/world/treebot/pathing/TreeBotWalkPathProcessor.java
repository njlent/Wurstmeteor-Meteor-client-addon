package de.njlent.wurstmeteor.modules.world.treebot.pathing;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.Jesus;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;

public class TreeBotWalkPathProcessor extends TreeBotPathProcessor {
    public TreeBotWalkPathProcessor(Minecraft mc, ArrayList<TreeBotPathPos> path) {
        super(mc, path);
    }

    @Override
    public void process() {
        BlockPos pos;
        if (mc.player.onGround()) {
            pos = BlockPos.containing(mc.player.getX(), mc.player.getY() + 0.5, mc.player.getZ());
        } else {
            pos = BlockPos.containing(mc.player.getX(), mc.player.getY(), mc.player.getZ());
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
        if (Math.abs(Mth.wrapDegrees(getHorizontalAngleTo(Vec3.atCenterOf(nextPos)))) > 90) return;

        if (isJesusActive()) {
            if (mc.player.getY() < nextPos.getY() && (mc.player.isInWater() || mc.player.isInLava())) return;

            if (mc.player.getY() - nextPos.getY() > 0.5
                && (mc.player.isInWater() || mc.player.isInLava() || isOverLiquid())) {
                mc.options.keyShift.setDown(true);
            }
        }

        if (pos.getX() != nextPos.getX() || pos.getZ() != nextPos.getZ()) {
            mc.options.keyUp.setDown(true);

            if ((index > 0 && path.get(index - 1).isJumping()) || pos.getY() < nextPos.getY()) {
                mc.options.keyJump.setDown(true);
            }
        } else if (pos.getY() != nextPos.getY()) {
            if (pos.getY() < nextPos.getY()) {
                Block block = mc.level.getBlockState(pos).getBlock();

                if (block instanceof LadderBlock || block instanceof VineBlock) {
                    facePosition(pos);
                    mc.options.keyUp.setDown(true);
                } else {
                    if (index < path.size() - 1 && !nextPos.above().equals(path.get(index + 1))) index++;
                    mc.options.keyJump.setDown(true);
                }
            } else {
                while (index < path.size() - 1 && path.get(index).below().equals(path.get(index + 1))) index++;
                if (mc.player.onGround()) mc.options.keyUp.setDown(true);
            }
        }
    }

    @Override
    public boolean canBreakBlocks() {
        return mc.player.onGround();
    }

    private boolean isJesusActive() {
        Modules modules = Modules.get();
        return modules != null && modules.get(Jesus.class).isActive();
    }

    private boolean isOverLiquid() {
        BlockPos pos = mc.player.blockPosition();
        Block feet = mc.level.getBlockState(pos).getBlock();
        Block below = mc.level.getBlockState(pos.below()).getBlock();

        return feet instanceof LiquidBlock || below instanceof LiquidBlock || feet instanceof WebBlock || below instanceof WebBlock;
    }
}
