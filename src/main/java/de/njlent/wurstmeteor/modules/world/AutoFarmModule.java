package de.njlent.wurstmeteor.modules.world;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import de.njlent.wurstmeteor.util.RotationPackets;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

import java.util.*;

public class AutoFarmModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum farming range.")
        .defaultValue(5.0)
        .min(1.0)
        .sliderRange(1.0, 6.0)
        .build()
    );

    private final Setting<Boolean> checkLos = sgGeneral.add(new BoolSetting.Builder()
        .name("check-line-of-sight")
        .description("Only interacts with blocks in direct line-of-sight.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> prioritizeReplant = sgGeneral.add(new BoolSetting.Builder()
        .name("prioritize-replant")
        .description("Replants before harvesting new crops.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Faces targets before interacting.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swings hand when interacting.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Renders harvest and replant targets.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> mineColor = sgRender.add(new ColorSetting.Builder()
        .name("mine-color")
        .defaultValue(new SettingColor(235, 65, 65, 55))
        .build()
    );

    private final Setting<SettingColor> interactColor = sgRender.add(new ColorSetting.Builder()
        .name("interact-color")
        .defaultValue(new SettingColor(250, 190, 60, 55))
        .build()
    );

    private final Setting<SettingColor> replantColor = sgRender.add(new ColorSetting.Builder()
        .name("replant-color")
        .defaultValue(new SettingColor(65, 220, 90, 55))
        .build()
    );

    private final Map<BlockPos, Item> replantingSpots = new HashMap<>();

    private final Set<BlockPos> mineTargets = new HashSet<>();
    private final Set<BlockPos> interactTargets = new HashSet<>();
    private final Set<BlockPos> replantTargets = new HashSet<>();

    private boolean busy;
    private int actionDelay;

    public AutoFarmModule() {
        super(WurstMeteorAddon.CATEGORY, "auto-farm", "Harvests and replants nearby crops.");
    }

    @Override
    public void onActivate() {
        replantingSpots.clear();
        mineTargets.clear();
        interactTargets.clear();
        replantTargets.clear();
        busy = false;
        actionDelay = 0;
    }

    @Override
    public void onDeactivate() {
        replantingSpots.clear();
        mineTargets.clear();
        interactTargets.clear();
        replantTargets.clear();
        busy = false;
    }

    public boolean isBusy() {
        return busy;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        if (actionDelay > 0) actionDelay--;

        List<BlockPos> mineList = new ArrayList<>();
        List<BlockPos> interactList = new ArrayList<>();
        List<ReplantTarget> replantList = new ArrayList<>();

        scanHarvestTargets(mineList, interactList);
        collectReplantTargets(replantList);

        updatePreviewSets(mineList, interactList, replantList);

        boolean didAction = false;

        if (prioritizeReplant.get()) {
            if (actionDelay <= 0) didAction = tryReplant(replantList);
            if (!didAction && actionDelay <= 0) didAction = tryInteract(interactList);
            if (!didAction) didAction = tryMine(mineList);
        } else {
            if (actionDelay <= 0) didAction = tryInteract(interactList);
            if (!didAction) didAction = tryMine(mineList);
            if (!didAction && actionDelay <= 0) didAction = tryReplant(replantList);
        }

        busy = didAction || BlockUtils.breaking;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get()) return;

        Color mineSide = new Color(mineColor.get());
        Color mineLine = new Color(mineColor.get()).a(170);

        Color interactSide = new Color(interactColor.get());
        Color interactLine = new Color(interactColor.get()).a(170);

        Color replantSide = new Color(replantColor.get());
        Color replantLine = new Color(replantColor.get()).a(170);

        for (BlockPos pos : mineTargets) {
            event.renderer.box(pos, mineSide, mineLine, shapeMode.get(), 0);
        }

        for (BlockPos pos : interactTargets) {
            event.renderer.box(pos, interactSide, interactLine, shapeMode.get(), 0);
        }

        for (BlockPos pos : replantTargets) {
            event.renderer.box(pos, replantSide, replantLine, shapeMode.get(), 0);
        }
    }

    private void scanHarvestTargets(List<BlockPos> mineList, List<BlockPos> interactList) {
        double rangeSq = range.get() * range.get();
        int blockRange = (int) Math.ceil(range.get());
        BlockPos center = BlockPos.containing(mc.player.getEyePosition());

        for (BlockPos pos : BlockPos.betweenClosed(
            center.offset(-blockRange, -blockRange, -blockRange),
            center.offset(blockRange, blockRange, blockRange)
        )) {
            Vec3 posCenter = pos.getCenter();
            if (mc.player.getEyePosition().distanceToSqr(posCenter) > rangeSq) continue;

            BlockState state = mc.level.getBlockState(pos);
            if (shouldHarvestByMining(pos, state)) {
                mineList.add(pos.immutable());
                Item seed = getReplantSeed(state);
                if (seed != null) replantingSpots.put(pos.immutable(), seed);
                continue;
            }

            if (shouldHarvestByInteracting(state)) {
                interactList.add(pos.immutable());
            }
        }
    }

    private void collectReplantTargets(List<ReplantTarget> replantList) {
        double rangeSq = range.get() * range.get();

        Iterator<Map.Entry<BlockPos, Item>> iterator = replantingSpots.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Item> entry = iterator.next();
            BlockPos pos = entry.getKey();
            Item seed = entry.getValue();

            if (!mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                iterator.remove();
                continue;
            }

            BlockState state = mc.level.getBlockState(pos);
            if (!state.canBeReplaced()) {
                if (!shouldHarvestByMining(pos, state)) iterator.remove();
                continue;
            }

            if (!canReplantAt(pos, seed)) {
                iterator.remove();
                continue;
            }

            double distanceSq = mc.player.getEyePosition().distanceToSqr(pos.getCenter());
            if (distanceSq > rangeSq) continue;

            replantList.add(new ReplantTarget(pos.immutable(), seed));
        }
    }

    private void updatePreviewSets(List<BlockPos> mineList, List<BlockPos> interactList, List<ReplantTarget> replantList) {
        mineTargets.clear();
        mineTargets.addAll(mineList);

        interactTargets.clear();
        interactTargets.addAll(interactList);

        replantTargets.clear();
        for (ReplantTarget target : replantList) replantTargets.add(target.pos());
    }

    private boolean tryMine(List<BlockPos> mineList) {
        if (mineList.isEmpty()) return false;

        mineList.sort(Comparator.comparingDouble(pos -> mc.player.getEyePosition().distanceToSqr(pos.getCenter())));

        for (BlockPos pos : mineList) {
            if (checkLos.get() && !hasLineOfSight(pos)) continue;
            if (BlockUtils.breakBlock(pos, swing.get())) return true;
        }

        return false;
    }

    private boolean tryInteract(List<BlockPos> interactList) {
        if (interactList.isEmpty()) return false;

        interactList.sort(Comparator.comparingDouble(pos -> mc.player.getEyePosition().distanceToSqr(pos.getCenter())));

        for (BlockPos pos : interactList) {
            if (checkLos.get() && !hasLineOfSight(pos)) continue;

            Vec3 hitPos = pos.getCenter();
            if (rotate.get()) RotationPackets.face(hitPos);

            BlockHitResult hitResult = new BlockHitResult(hitPos, Direction.UP, pos, false);
            InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);

            if (result.consumesAction()) {
                if (swing.get()) mc.player.swing(InteractionHand.MAIN_HAND);
                actionDelay = 4;
                return true;
            }
        }

        return false;
    }

    private boolean tryReplant(List<ReplantTarget> replantList) {
        if (replantList.isEmpty()) return false;

        replantList.sort(Comparator.comparingDouble(target -> mc.player.getEyePosition().distanceToSqr(target.pos().getCenter())));

        for (ReplantTarget target : replantList) {
            BlockPos pos = target.pos();
            Item seed = target.seed();

            if (checkLos.get() && !hasLineOfSight(pos)) continue;

            FindItemResult findItem = InvUtils.findInHotbar(seed);
            if (!findItem.found()) continue;

            if (!findItem.isMainHand() && !findItem.isOffhand()) {
                InvUtils.swap(findItem.slot(), false);
                return true;
            }

            InteractionHand hand = findItem.getHand() == null ? InteractionHand.MAIN_HAND : findItem.getHand();
            BlockHitResult hitResult = getReplantHitResult(pos, seed);
            if (hitResult == null) {
                replantingSpots.remove(pos);
                continue;
            }

            if (rotate.get()) RotationPackets.face(hitResult.getLocation());
            InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, hitResult);

            if (result.consumesAction()) {
                if (swing.get()) mc.player.swing(hand);
                actionDelay = 4;
                replantingSpots.remove(pos);
                return true;
            }
        }

        return false;
    }

    private BlockHitResult getReplantHitResult(BlockPos pos, Item seed) {
        if (seed == Items.COCOA_BEANS) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos logPos = pos.relative(direction);
                if (mc.level.getBlockState(logPos).is(BlockTags.JUNGLE_LOGS)) {
                    Direction side = direction.getOpposite();
                    Vec3 hitPos = logPos.getCenter().add(side.getStepX() * 0.5, side.getStepY() * 0.5, side.getStepZ() * 0.5);
                    return new BlockHitResult(hitPos, side, logPos, false);
                }
            }
            return null;
        }

        BlockPos soilPos = pos.below();
        Vec3 hitPos = soilPos.getCenter().add(0.0, 0.5, 0.0);
        return new BlockHitResult(hitPos, Direction.UP, soilPos, false);
    }

    private boolean canReplantAt(BlockPos pos, Item seed) {
        BlockState soil = mc.level.getBlockState(pos.below());

        if (seed == Items.NETHER_WART) {
            return soil.is(Blocks.SOUL_SAND);
        }

        if (seed == Items.COCOA_BEANS) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (mc.level.getBlockState(pos.relative(direction)).is(BlockTags.JUNGLE_LOGS)) return true;
            }
            return false;
        }

        return isFarmlandSeed(seed) && soil.is(Blocks.FARMLAND);
    }

    private boolean shouldHarvestByMining(BlockPos pos, BlockState state) {
        Block block = state.getBlock();

        if (block instanceof CropBlock crop && crop.isMaxAge(state)) return true;

        if (block == Blocks.NETHER_WART && state.hasProperty(NetherWartBlock.AGE)) {
            return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
        }

        if (block == Blocks.COCOA && state.hasProperty(CocoaBlock.AGE)) {
            return state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE;
        }

        if (block == Blocks.MELON || block == Blocks.PUMPKIN) return true;
        if (block == Blocks.SUGAR_CANE) return mc.level.getBlockState(pos.below()).is(Blocks.SUGAR_CANE);
        if (block == Blocks.CACTUS) return mc.level.getBlockState(pos.below()).is(Blocks.CACTUS);
        if (block == Blocks.BAMBOO) return mc.level.getBlockState(pos.below()).is(Blocks.BAMBOO);
        if (block == Blocks.KELP_PLANT) return mc.level.getBlockState(pos.below()).is(Blocks.KELP_PLANT) || mc.level.getBlockState(pos.below()).is(Blocks.KELP);
        if (block == Blocks.KELP) return mc.level.getBlockState(pos.below()).is(Blocks.KELP_PLANT);

        return false;
    }

    private boolean shouldHarvestByInteracting(BlockState state) {
        Block block = state.getBlock();

        if (block == Blocks.SWEET_BERRY_BUSH && state.hasProperty(SweetBerryBushBlock.AGE)) {
            return state.getValue(SweetBerryBushBlock.AGE) >= 3;
        }

        return CaveVines.hasGlowBerries(state);
    }

    private Item getReplantSeed(BlockState state) {
        Block block = state.getBlock();

        if (block == Blocks.WHEAT) return Items.WHEAT_SEEDS;
        if (block == Blocks.CARROTS) return Items.CARROT;
        if (block == Blocks.POTATOES) return Items.POTATO;
        if (block == Blocks.BEETROOTS) return Items.BEETROOT_SEEDS;
        if (block == Blocks.NETHER_WART) return Items.NETHER_WART;
        if (block == Blocks.COCOA) return Items.COCOA_BEANS;

        return null;
    }

    private boolean isFarmlandSeed(Item item) {
        return item == Items.WHEAT_SEEDS
            || item == Items.CARROT
            || item == Items.POTATO
            || item == Items.BEETROOT_SEEDS
            || item == Items.TORCHFLOWER_SEEDS
            || item == Items.PITCHER_POD;
    }

    private boolean hasLineOfSight(BlockPos pos) {
        Vec3 eyes = mc.player.getEyePosition();
        Vec3 target = pos.getCenter();

        HitResult hit = mc.level.clip(new ClipContext(
            eyes,
            target,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            mc.player
        ));

        if (hit.getType() == HitResult.Type.MISS) return true;
        if (hit instanceof BlockHitResult blockHitResult) {
            return blockHitResult.getBlockPos().equals(pos);
        }

        return false;
    }

    private record ReplantTarget(BlockPos pos, Item seed) {
    }
}
