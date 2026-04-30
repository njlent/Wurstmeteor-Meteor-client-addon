package de.njlent.wurstmeteor.modules.world;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import de.njlent.wurstmeteor.modules.world.treebot.BlockBreakingHelper;
import de.njlent.wurstmeteor.modules.world.treebot.TreeBotUtils;
import de.njlent.wurstmeteor.modules.world.treebot.TreeTarget;
import de.njlent.wurstmeteor.modules.world.treebot.pathing.TreeBotPathFinder;
import de.njlent.wurstmeteor.modules.world.treebot.pathing.TreeBotPathPos;
import de.njlent.wurstmeteor.modules.world.treebot.pathing.TreeBotPathProcessor;
import de.njlent.wurstmeteor.util.RotationPackets;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.MultiPlayerGameModeAccessor;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TreeBotModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far TreeBot will reach to break blocks.")
        .defaultValue(4.5)
        .range(1.0, 6.0)
        .sliderRange(1.0, 6.0)
        .decimalPlaces(2)
        .build()
    );

    private final Setting<FaceTargetMode> faceTarget = sgGeneral.add(new EnumSetting.Builder<FaceTargetMode>()
        .name("face-target")
        .description("How TreeBot rotates to each target block.")
        .defaultValue(FaceTargetMode.Server)
        .build()
    );

    private final Setting<SwingHandMode> swingHand = sgGeneral.add(new EnumSetting.Builder<SwingHandMode>()
        .name("swing-hand")
        .description("How TreeBot swings after block-break updates.")
        .defaultValue(SwingHandMode.Server)
        .build()
    );

    private TreeFinder treeFinder;
    private AngleFinder angleFinder;
    private TreeBotPathRunner processor;
    private TreeTarget tree;

    private BlockPos currentBlock;

    private float progress;
    private float prevProgress;
    private BlockPos prevPos;

    public TreeBotModule() {
        super(WurstMeteorAddon.CATEGORY, "treebot", "Automatically finds and chops nearby small trees. As alternative to Baritone.");
    }

    @Override
    public void onActivate() {
        treeFinder = new TreeFinder();
        angleFinder = null;
        processor = null;
        tree = null;
        currentBlock = null;
        resetProgress();
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null && mc.level != null && mc.gameMode != null) {
            TreeBotPathProcessor.releaseControls(mc);

            if (currentBlock != null) {
                mc.gameMode.stopDestroyBlock();
                currentBlock = null;
            }
        }

        treeFinder = null;
        angleFinder = null;
        processor = null;
        tree = null;
        currentBlock = null;
        resetProgress();
    }

    @Override
    public String getInfoString() {
        if (treeFinder != null && !treeFinder.isDone() && !treeFinder.isFailed()) return "Searching";
        if (processor != null && !processor.isDone()) return "Going";
        if (tree != null && !tree.logs().isEmpty()) return "Chopping";

        return null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        if (treeFinder != null) {
            goToTree();
            return;
        }

        if (tree == null) {
            treeFinder = new TreeFinder();
            return;
        }

        tree.logs().removeIf(Predicate.not(pos -> TreeBotUtils.isLog(mc.level.getBlockState(pos))));

        if (tree.logs().isEmpty()) {
            tree = null;
            return;
        }

        if (angleFinder != null) {
            goToAngle();
            return;
        }

        if (breakBlocks(tree.logs())) return;
        if (angleFinder == null) angleFinder = new AngleFinder();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;

        if (tree != null) tree.render(event);
        renderProgress(event);
    }

    private void goToTree() {
        if (!treeFinder.isDoneOrFailed()) {
            TreeBotPathProcessor.lockControls(mc);
            treeFinder.findPath();
            return;
        }

        if (processor != null && !processor.isDone()) {
            processor.goToGoal();
            return;
        }

        TreeBotPathProcessor.releaseControls(mc);
        treeFinder = null;
    }

    private void goToAngle() {
        if (!angleFinder.isDone() && !angleFinder.isFailed()) {
            TreeBotPathProcessor.lockControls(mc);
            angleFinder.findPath();
            return;
        }

        if (processor != null && !processor.isDone()) {
            processor.goToGoal();
            return;
        }

        TreeBotPathProcessor.releaseControls(mc);
        angleFinder = null;
    }

    private boolean breakBlocks(ArrayList<BlockPos> blocks) {
        for (BlockPos pos : blocks) {
            if (!breakBlock(pos)) continue;

            currentBlock = pos;
            return true;
        }

        return false;
    }

    private boolean breakBlock(BlockPos pos) {
        BlockBreakingHelper.BlockBreakingParams params = BlockBreakingHelper.getBlockBreakingParams(mc, pos);
        if (params == null || !params.lineOfSight() || params.distanceSq() > range.get() * range.get()) return false;

        equipBestTool(pos);
        faceTarget.get().face(mc, params.hitPos());

        boolean updated;
        if (mc.gameMode.isDestroying()) {
            updated = mc.gameMode.continueDestroyBlock(pos, params.side());
        } else {
            updated = mc.gameMode.startDestroyBlock(pos, params.side());
        }

        if (updated) swingHand.get().swing(mc, InteractionHand.MAIN_HAND);
        updateProgress();

        return true;
    }

    private void equipBestTool(BlockPos pos) {
        if (mc.player.getAbilities().instabuild) return;

        FindItemResult bestTool = InvUtils.findFastestTool(mc.level.getBlockState(pos));
        if (!bestTool.found()) return;
        if (bestTool.isMainHand() || bestTool.isOffhand()) return;

        InvUtils.swap(bestTool.slot(), false);
    }

    private void updateProgress() {
        if (!(mc.gameMode instanceof MultiPlayerGameModeAccessor accessor)) return;

        prevProgress = progress;
        progress = accessor.meteor$getBreakingProgress();

        if (progress < prevProgress) prevProgress = progress;
    }

    private void renderProgress(Render3DEvent event) {
        if (currentBlock == null) return;

        if (prevPos != null && !currentBlock.equals(prevPos)) resetProgress();
        prevPos = currentBlock;

        BlockState state = mc.level.getBlockState(currentBlock);
        boolean breaksInstantly = mc.player.getAbilities().instabuild || state.getDestroyProgress(mc.player, mc.level, currentBlock) >= 1;

        float p = breaksInstantly ? 1 : Mth.lerp(event.tickDelta, prevProgress, progress);
        p = Mth.clamp(p, 0, 1);

        float redFloat = Mth.clamp(p * 2F, 0, 1);
        float greenFloat = Mth.clamp(2 - p * 2F, 0, 1);

        Color side = new Color((int) (redFloat * 255), (int) (greenFloat * 255), 0, 64);
        Color line = new Color((int) (redFloat * 255), (int) (greenFloat * 255), 0, 128);

        AABB box = new AABB(currentBlock);
        if (p < 1) {
            double shrink = (1 - p) * 0.5;
            box = box.deflate(shrink, shrink, shrink);
        }

        event.renderer.box(box, side, line, ShapeMode.Both, 0);
    }

    private void resetProgress() {
        progress = 0;
        prevProgress = 0;
        prevPos = null;
    }

    private ArrayList<BlockPos> getNeighbors(BlockPos pos) {
        ArrayList<BlockPos> neighbors = new ArrayList<>();

        for (BlockPos blockPos : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            if (!TreeBotUtils.isLog(mc.level.getBlockState(blockPos))) continue;
            neighbors.add(blockPos.immutable());
        }

        return neighbors;
    }

    private abstract class TreeBotCustomPathFinder extends TreeBotPathFinder {
        public TreeBotCustomPathFinder(BlockPos goal) {
            super(TreeBotModule.this.mc, goal);
        }

        public TreeBotCustomPathFinder(TreeBotCustomPathFinder pathFinder) {
            super(pathFinder);
        }

        public void findPath() {
            think();

            if (isDoneOrFailed()) {
                formatPath();
                processor = new TreeBotPathRunner(this);
            }
        }

        public boolean isDoneOrFailed() {
            return isDone() || isFailed();
        }

        public abstract void reset();
    }

    private class TreeBotPathRunner {
        private final TreeBotCustomPathFinder pathFinder;
        private final TreeBotPathProcessor processor;

        public TreeBotPathRunner(TreeBotCustomPathFinder pathFinder) {
            this.pathFinder = pathFinder;
            processor = pathFinder.getProcessor();
        }

        public void goToGoal() {
            if (!pathFinder.isPathStillValid(processor.getIndex()) || processor.getTicksOffPath() > 20) {
                pathFinder.reset();
                return;
            }

            if (processor.canBreakBlocks() && breakBlocks(getLeavesOnPath())) return;
            processor.process();
        }

        private ArrayList<BlockPos> getLeavesOnPath() {
            List<TreeBotPathPos> path = pathFinder.getPath();
            path = path.subList(processor.getIndex(), path.size());

            return path.stream()
                .flatMap(pos -> Stream.of(pos, pos.above()))
                .distinct()
                .filter(pos -> TreeBotUtils.isLeaves(mc.level.getBlockState(pos)))
                .collect(Collectors.toCollection(ArrayList::new));
        }

        public boolean isDone() {
            return processor.isDone();
        }
    }

    private class TreeFinder extends TreeBotCustomPathFinder {
        public TreeFinder() {
            super(BlockPos.containing(TreeBotModule.this.mc.player.getX(), TreeBotModule.this.mc.player.getY(), TreeBotModule.this.mc.player.getZ()));
        }

        public TreeFinder(TreeBotCustomPathFinder pathFinder) {
            super(pathFinder);
        }

        @Override
        protected boolean isMineable(BlockPos pos) {
            return TreeBotUtils.isLeaves(mc.level.getBlockState(pos));
        }

        @Override
        protected boolean checkDone() {
            return done = isNextToTreeStump(current);
        }

        private boolean isNextToTreeStump(TreeBotPathPos pos) {
            return isTreeStump(pos.north()) || isTreeStump(pos.east()) || isTreeStump(pos.south()) || isTreeStump(pos.west());
        }

        private boolean isTreeStump(BlockPos pos) {
            if (!TreeBotUtils.isLog(mc.level.getBlockState(pos))) return false;
            if (TreeBotUtils.isLog(mc.level.getBlockState(pos.below()))) return false;

            analyzeTree(pos);

            if (tree.logs().size() > 6) return false;
            return true;
        }

        private void analyzeTree(BlockPos stump) {
            ArrayList<BlockPos> logs = new ArrayList<>(Arrays.asList(stump));
            ArrayDeque<BlockPos> queue = new ArrayDeque<>(Arrays.asList(stump));

            for (int i = 0; i < 1024; i++) {
                if (queue.isEmpty()) break;
                BlockPos current = queue.pollFirst();

                for (BlockPos next : getNeighbors(current)) {
                    if (logs.contains(next)) continue;

                    logs.add(next);
                    queue.add(next);
                }
            }

            tree = new TreeTarget(stump, logs);
        }

        @Override
        public void reset() {
            treeFinder = new TreeFinder(treeFinder);
        }
    }

    private class AngleFinder extends TreeBotCustomPathFinder {
        public AngleFinder() {
            super(BlockPos.containing(TreeBotModule.this.mc.player.getX(), TreeBotModule.this.mc.player.getY(), TreeBotModule.this.mc.player.getZ()));
            setThinkSpeed(512);
            setThinkTime(1);
        }

        public AngleFinder(TreeBotCustomPathFinder pathFinder) {
            super(pathFinder);
        }

        @Override
        protected boolean isMineable(BlockPos pos) {
            return TreeBotUtils.isLeaves(mc.level.getBlockState(pos));
        }

        @Override
        protected boolean checkDone() {
            return done = hasAngle(current);
        }

        private boolean hasAngle(TreeBotPathPos pos) {
            double rangeSq = range.get() * range.get();
            Vec3 eyes = Vec3.atBottomCenterOf(pos).add(0, mc.player.getEyeHeight(mc.player.getPose()), 0);

            for (BlockPos log : tree.logs()) {
                BlockBreakingHelper.BlockBreakingParams params = BlockBreakingHelper.getBlockBreakingParams(mc, eyes, log);

                if (params != null && params.lineOfSight() && params.distanceSq() <= rangeSq) return true;
            }

            return false;
        }

        @Override
        public void reset() {
            angleFinder = new AngleFinder(angleFinder);
        }
    }

    private enum FaceTargetMode {
        Off,
        Server,
        Client;

        public void face(net.minecraft.client.Minecraft mc, Vec3 target) {
            switch (this) {
                case Off -> {
                }
                case Server -> RotationPackets.face(target);
                case Client -> {
                    float[] angle = PlayerUtils.calculateAngle(target);
                    mc.player.setYRot(angle[0]);
                    mc.player.setYHeadRot(angle[0]);
                    mc.player.setYBodyRot(angle[0]);
                    mc.player.setXRot(angle[1]);
                }
            }
        }

        @Override
        public String toString() {
            return switch (this) {
                case Off -> "Off";
                case Server -> "Server-side";
                case Client -> "Client-side";
            };
        }
    }

    private enum SwingHandMode {
        Off,
        Server,
        Client;

        public void swing(net.minecraft.client.Minecraft mc, InteractionHand hand) {
            switch (this) {
                case Off -> {
                }
                case Server -> {
                    if (mc.getConnection() == null) return;
                    mc.getConnection().send(new ServerboundSwingPacket(hand));
                }
                case Client -> mc.player.swing(hand);
            }
        }

        @Override
        public String toString() {
            return switch (this) {
                case Off -> "Off";
                case Server -> "Server-side";
                case Client -> "Client-side";
            };
        }
    }

}
