package de.njlent.wurstmeteor.modules.world.treebot.pathing;

import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class TreeBotPathFinder {
    protected final MinecraftClient mc;

    private final TreeBotPlayerAbilities abilities;
    protected boolean fallingAllowed = true;
    protected boolean divingAllowed = true;

    private final TreeBotPathPos start;
    protected TreeBotPathPos current;
    private final BlockPos goal;

    private final HashMap<TreeBotPathPos, Float> costMap = new HashMap<>();
    protected final HashMap<TreeBotPathPos, TreeBotPathPos> prevPosMap = new HashMap<>();
    private final TreeBotPathQueue queue = new TreeBotPathQueue();

    protected int thinkSpeed = 1024;
    protected int thinkTime = 200;
    private int iterations;

    protected boolean done;
    protected boolean failed;
    private final ArrayList<TreeBotPathPos> path = new ArrayList<>();

    public TreeBotPathFinder(MinecraftClient mc, BlockPos goal) {
        this.mc = mc;
        this.abilities = TreeBotPlayerAbilities.get(mc);

        if (mc.player.isOnGround()) {
            start = new TreeBotPathPos(BlockPos.ofFloored(mc.player.getX(), mc.player.getY() + 0.5, mc.player.getZ()));
        } else {
            start = new TreeBotPathPos(BlockPos.ofFloored(mc.player.getX(), mc.player.getY(), mc.player.getZ()));
        }

        this.goal = goal.toImmutable();

        costMap.put(start, 0F);
        queue.add(start, getHeuristic(start));
    }

    public TreeBotPathFinder(TreeBotPathFinder pathFinder) {
        this(pathFinder.mc, pathFinder.goal);
        thinkSpeed = pathFinder.thinkSpeed;
        thinkTime = pathFinder.thinkTime;
    }

    public void think() {
        if (done) throw new IllegalStateException("Path was already found!");

        int i = 0;
        for (; i < thinkSpeed && !checkFailed(); i++) {
            current = queue.poll();

            if (checkDone()) return;

            for (TreeBotPathPos next : getNeighbors(current)) {
                float newCost = costMap.get(current) + getCost(current, next);
                if (costMap.containsKey(next) && costMap.get(next) <= newCost) continue;

                costMap.put(next, newCost);
                prevPosMap.put(next, current);
                queue.add(next, newCost + getHeuristic(next));
            }
        }

        iterations += i;
    }

    protected boolean checkDone() {
        return done = goal.equals(current);
    }

    private boolean checkFailed() {
        return failed = queue.isEmpty() || iterations >= thinkSpeed * thinkTime;
    }

    private ArrayList<TreeBotPathPos> getNeighbors(TreeBotPathPos pos) {
        ArrayList<TreeBotPathPos> neighbors = new ArrayList<>();

        if (Math.abs(start.getX() - pos.getX()) > 256 || Math.abs(start.getZ() - pos.getZ()) > 256) return neighbors;

        BlockPos north = pos.north();
        BlockPos east = pos.east();
        BlockPos south = pos.south();
        BlockPos west = pos.west();

        BlockPos northEast = north.east();
        BlockPos southEast = south.east();
        BlockPos southWest = south.west();
        BlockPos northWest = north.west();

        BlockPos up = pos.up();
        BlockPos down = pos.down();

        boolean flying = canFlyAt(pos);
        boolean onGround = canBeSolid(down);

        if (flying || onGround || pos.isJumping() || canMoveSidewaysInMidairAt(pos) || canClimbUpAt(pos.down())) {
            if (checkHorizontalMovement(pos, north)) neighbors.add(new TreeBotPathPos(north));
            if (checkHorizontalMovement(pos, east)) neighbors.add(new TreeBotPathPos(east));
            if (checkHorizontalMovement(pos, south)) neighbors.add(new TreeBotPathPos(south));
            if (checkHorizontalMovement(pos, west)) neighbors.add(new TreeBotPathPos(west));

            if (checkDiagonalMovement(pos, Direction.NORTH, Direction.EAST)) neighbors.add(new TreeBotPathPos(northEast));
            if (checkDiagonalMovement(pos, Direction.SOUTH, Direction.EAST)) neighbors.add(new TreeBotPathPos(southEast));
            if (checkDiagonalMovement(pos, Direction.SOUTH, Direction.WEST)) neighbors.add(new TreeBotPathPos(southWest));
            if (checkDiagonalMovement(pos, Direction.NORTH, Direction.WEST)) neighbors.add(new TreeBotPathPos(northWest));
        }

        if (pos.getY() < mc.world.getTopYInclusive()
            && canGoThrough(up.up())
            && (flying || onGround || canClimbUpAt(pos))
            && (flying
            || canClimbUpAt(pos)
            || goal.equals(up)
            || canSafelyStandOn(north)
            || canSafelyStandOn(east)
            || canSafelyStandOn(south)
            || canSafelyStandOn(west))
            && (divingAllowed || !mc.world.getBlockState(up.up()).isOf(Blocks.WATER))) {
            neighbors.add(new TreeBotPathPos(up, onGround));
        }

        if (pos.getY() > mc.world.getBottomY()
            && canGoThrough(down)
            && canGoAbove(down.down())
            && (flying || canFallBelow(pos))
            && (divingAllowed || !mc.world.getBlockState(pos).isOf(Blocks.WATER))) {
            neighbors.add(new TreeBotPathPos(down));
        }

        return neighbors;
    }

    private boolean checkHorizontalMovement(BlockPos current, BlockPos next) {
        return isPassable(next) && (canFlyAt(current) || canGoThrough(next.down()) || canSafelyStandOn(next.down()));
    }

    private boolean checkDiagonalMovement(BlockPos current, Direction direction1, Direction direction2) {
        BlockPos horizontal1 = current.offset(direction1);
        BlockPos horizontal2 = current.offset(direction2);
        BlockPos next = horizontal1.offset(direction2);

        return isPassableWithoutMining(horizontal1)
            && isPassableWithoutMining(horizontal2)
            && checkHorizontalMovement(current, next);
    }

    protected boolean isPassable(BlockPos pos) {
        if (!canGoThrough(pos) && !isMineable(pos)) return false;

        BlockPos up = pos.up();
        if (!canGoThrough(up) && !isMineable(up)) return false;

        if (!canGoAbove(pos.down())) return false;
        if (!divingAllowed && mc.world.getBlockState(up).isOf(Blocks.WATER)) return false;

        return true;
    }

    protected boolean isPassableWithoutMining(BlockPos pos) {
        if (!canGoThrough(pos)) return false;

        BlockPos up = pos.up();
        if (!canGoThrough(up)) return false;

        if (!canGoAbove(pos.down())) return false;
        if (!divingAllowed && mc.world.getBlockState(up).isOf(Blocks.WATER)) return false;

        return true;
    }

    protected boolean isMineable(BlockPos pos) {
        return false;
    }

    protected boolean canBeSolid(BlockPos pos) {
        var state = mc.world.getBlockState(pos);
        Block block = state.getBlock();

        return (state.blocksMovement() && !(block instanceof AbstractSignBlock))
            || block instanceof LadderBlock
            || (abilities.jesus() && (block == Blocks.WATER || block == Blocks.LAVA));
    }

    private boolean canGoThrough(BlockPos pos) {
        if (!mc.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) return false;

        var state = mc.world.getBlockState(pos);
        Block block = state.getBlock();

        if (state.blocksMovement() && !(block instanceof AbstractSignBlock)) return false;
        if (block instanceof TripwireBlock || block instanceof PressurePlateBlock) return false;

        if (!abilities.invulnerable() && (block == Blocks.LAVA || block instanceof AbstractFireBlock)) return false;
        return true;
    }

    private boolean canGoAbove(BlockPos pos) {
        Block block = mc.world.getBlockState(pos).getBlock();

        return !(block instanceof FenceBlock) && !(block instanceof WallBlock) && !(block instanceof FenceGateBlock);
    }

    private boolean canSafelyStandOn(BlockPos pos) {
        if (!canBeSolid(pos)) return false;

        var state = mc.world.getBlockState(pos);
        Fluid fluid = state.getFluidState().getFluid();

        if (!abilities.invulnerable()
            && (state.getBlock() instanceof CactusBlock
            || fluid == Fluids.LAVA
            || fluid == Fluids.FLOWING_LAVA)) {
            return false;
        }

        return true;
    }

    private boolean canFallBelow(TreeBotPathPos pos) {
        BlockPos down2 = pos.down(2);
        if (fallingAllowed && canGoThrough(down2)) return true;

        if (!canSafelyStandOn(down2)) return false;
        if (abilities.immuneToFallDamage() && fallingAllowed) return true;

        if (mc.world.getBlockState(down2).getBlock() instanceof SlimeBlock && fallingAllowed) return true;

        BlockPos prevPos = pos;
        for (int i = 0; i <= (fallingAllowed ? 3 : 1); i++) {
            if (prevPos == null) return true;
            if (!pos.up(i).equals(prevPos)) return true;

            Block prevBlock = mc.world.getBlockState(prevPos).getBlock();
            var prevState = mc.world.getBlockState(prevPos);
            Fluid prevFluid = prevState.getFluidState().getFluid();

            if (prevFluid == Fluids.WATER
                || prevFluid == Fluids.FLOWING_WATER
                || prevBlock instanceof LadderBlock
                || prevBlock instanceof VineBlock
                || prevBlock instanceof CobwebBlock) {
                return true;
            }

            prevPos = prevPosMap.get(prevPos);
        }

        return false;
    }

    private boolean canFlyAt(BlockPos pos) {
        return abilities.flying() || (!abilities.noWaterSlowdown() && mc.world.getBlockState(pos).isOf(Blocks.WATER));
    }

    private boolean canClimbUpAt(BlockPos pos) {
        Block block = mc.world.getBlockState(pos).getBlock();

        if (!abilities.spider() && !(block instanceof LadderBlock) && !(block instanceof VineBlock)) return false;

        BlockPos up = pos.up();
        if (!canBeSolid(pos.north())
            && !canBeSolid(pos.east())
            && !canBeSolid(pos.south())
            && !canBeSolid(pos.west())
            && !canBeSolid(up.north())
            && !canBeSolid(up.east())
            && !canBeSolid(up.south())
            && !canBeSolid(up.west())) {
            return false;
        }

        return true;
    }

    private boolean canMoveSidewaysInMidairAt(BlockPos pos) {
        Block blockFeet = mc.world.getBlockState(pos).getBlock();
        if (blockFeet instanceof FluidBlock || blockFeet instanceof LadderBlock || blockFeet instanceof VineBlock || blockFeet instanceof CobwebBlock) {
            return true;
        }

        Block blockHead = mc.world.getBlockState(pos.up()).getBlock();
        return blockHead instanceof FluidBlock || blockHead instanceof CobwebBlock;
    }

    private float getCost(BlockPos current, BlockPos next) {
        float[] costs = {0.5F, 0.5F};
        BlockPos[] positions = {current, next};

        for (int i = 0; i < positions.length; i++) {
            BlockPos pos = positions[i];
            Block block = mc.world.getBlockState(pos).getBlock();

            if (block == Blocks.WATER && !abilities.noWaterSlowdown()) costs[i] *= 1.3164437838225804F;
            else if (block == Blocks.LAVA) costs[i] *= 4.539515393656079F;

            if (!canFlyAt(pos) && mc.world.getBlockState(pos.down()).getBlock() instanceof SoulSandBlock) costs[i] *= 2.5F;

            if (isMineable(pos)) costs[i] *= 2F;
            if (isMineable(pos.up())) costs[i] *= 2F;
        }

        float cost = costs[0] + costs[1];
        if (current.getX() != next.getX() && current.getZ() != next.getZ()) cost *= 1.4142135623730951F;

        return cost;
    }

    private float getHeuristic(BlockPos pos) {
        float dx = Math.abs(pos.getX() - goal.getX());
        float dy = Math.abs(pos.getY() - goal.getY());
        float dz = Math.abs(pos.getZ() - goal.getZ());
        return 1.001F * (dx + dy + dz - 0.5857864376269049F * Math.min(dx, dz));
    }

    public TreeBotPathPos getCurrentPos() {
        return current;
    }

    public BlockPos getGoal() {
        return goal;
    }

    public int countProcessedBlocks() {
        return prevPosMap.size();
    }

    public int getQueueSize() {
        return queue.size();
    }

    public float getCost(BlockPos pos) {
        return costMap.get(pos);
    }

    public boolean isDone() {
        return done;
    }

    public boolean isFailed() {
        return failed;
    }

    public ArrayList<TreeBotPathPos> formatPath() {
        if (!done && !failed) throw new IllegalStateException("No path found!");
        if (!path.isEmpty()) throw new IllegalStateException("Path was already formatted!");

        TreeBotPathPos pos;
        if (!failed) {
            pos = current;
        } else {
            pos = start;

            for (TreeBotPathPos next : prevPosMap.keySet()) {
                if (getHeuristic(next) < getHeuristic(pos) && (canFlyAt(next) || canBeSolid(next.down()))) pos = next;
            }
        }

        while (pos != null) {
            path.add(pos);
            pos = prevPosMap.get(pos);
        }

        Collections.reverse(path);
        return path;
    }

    public boolean isPathStillValid(int index) {
        if (path.isEmpty()) throw new IllegalStateException("Path is not formatted!");
        if (!abilities.equals(TreeBotPlayerAbilities.get(mc))) return false;

        if (index == 0) {
            TreeBotPathPos pos = path.getFirst();
            if (!isPassable(pos) || (!canFlyAt(pos) && !canGoThrough(pos.down()) && !canSafelyStandOn(pos.down()))) return false;
        }

        for (int i = Math.max(1, index); i < path.size(); i++) {
            if (!getNeighbors(path.get(i - 1)).contains(path.get(i))) return false;
        }

        return true;
    }

    public TreeBotPathProcessor getProcessor() {
        if (abilities.flying()) return new TreeBotFlyPathProcessor(mc, path, abilities.creativeFlying());
        return new TreeBotWalkPathProcessor(mc, path);
    }

    public void setThinkSpeed(int thinkSpeed) {
        this.thinkSpeed = thinkSpeed;
    }

    public void setThinkTime(int thinkTime) {
        this.thinkTime = thinkTime;
    }

    public void setFallingAllowed(boolean fallingAllowed) {
        this.fallingAllowed = fallingAllowed;
    }

    public void setDivingAllowed(boolean divingAllowed) {
        this.divingAllowed = divingAllowed;
    }

    public List<TreeBotPathPos> getPath() {
        return Collections.unmodifiableList(path);
    }
}
