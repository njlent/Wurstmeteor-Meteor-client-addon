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
import net.minecraft.block.*;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

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
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

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
        BlockPos center = BlockPos.ofFloored(mc.player.getEyePos());

        for (BlockPos pos : BlockPos.iterateOutwards(center, blockRange, blockRange, blockRange)) {
            Vec3d posCenter = pos.toCenterPos();
            if (mc.player.getEyePos().squaredDistanceTo(posCenter) > rangeSq) continue;

            BlockState state = mc.world.getBlockState(pos);
            if (shouldHarvestByMining(pos, state)) {
                mineList.add(pos.toImmutable());
                Item seed = getReplantSeed(state);
                if (seed != null) replantingSpots.put(pos.toImmutable(), seed);
                continue;
            }

            if (shouldHarvestByInteracting(state)) {
                interactList.add(pos.toImmutable());
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

            if (!mc.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                iterator.remove();
                continue;
            }

            BlockState state = mc.world.getBlockState(pos);
            if (!state.isReplaceable()) {
                if (!shouldHarvestByMining(pos, state)) iterator.remove();
                continue;
            }

            if (!canReplantAt(pos, seed)) {
                iterator.remove();
                continue;
            }

            double distanceSq = mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos());
            if (distanceSq > rangeSq) continue;

            replantList.add(new ReplantTarget(pos.toImmutable(), seed));
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

        mineList.sort(Comparator.comparingDouble(pos -> mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos())));

        for (BlockPos pos : mineList) {
            if (checkLos.get() && !hasLineOfSight(pos)) continue;
            if (BlockUtils.breakBlock(pos, swing.get())) return true;
        }

        return false;
    }

    private boolean tryInteract(List<BlockPos> interactList) {
        if (interactList.isEmpty()) return false;

        interactList.sort(Comparator.comparingDouble(pos -> mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos())));

        for (BlockPos pos : interactList) {
            if (checkLos.get() && !hasLineOfSight(pos)) continue;

            Vec3d hitPos = pos.toCenterPos();
            if (rotate.get()) RotationPackets.face(hitPos);

            BlockHitResult hitResult = new BlockHitResult(hitPos, Direction.UP, pos, false);
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);

            if (result.isAccepted()) {
                if (swing.get()) mc.player.swingHand(Hand.MAIN_HAND);
                actionDelay = 4;
                return true;
            }
        }

        return false;
    }

    private boolean tryReplant(List<ReplantTarget> replantList) {
        if (replantList.isEmpty()) return false;

        replantList.sort(Comparator.comparingDouble(target -> mc.player.getEyePos().squaredDistanceTo(target.pos().toCenterPos())));

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

            Hand hand = findItem.getHand() == null ? Hand.MAIN_HAND : findItem.getHand();
            BlockHitResult hitResult = getReplantHitResult(pos, seed);
            if (hitResult == null) {
                replantingSpots.remove(pos);
                continue;
            }

            if (rotate.get()) RotationPackets.face(hitResult.getPos());
            ActionResult result = mc.interactionManager.interactBlock(mc.player, hand, hitResult);

            if (result.isAccepted()) {
                if (swing.get()) mc.player.swingHand(hand);
                actionDelay = 4;
                replantingSpots.remove(pos);
                return true;
            }
        }

        return false;
    }

    private BlockHitResult getReplantHitResult(BlockPos pos, Item seed) {
        if (seed == Items.COCOA_BEANS) {
            for (Direction direction : Direction.Type.HORIZONTAL) {
                BlockPos logPos = pos.offset(direction);
                if (mc.world.getBlockState(logPos).isIn(BlockTags.JUNGLE_LOGS)) {
                    Direction side = direction.getOpposite();
                    Vec3d hitPos = logPos.toCenterPos().add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
                    return new BlockHitResult(hitPos, side, logPos, false);
                }
            }
            return null;
        }

        BlockPos soilPos = pos.down();
        Vec3d hitPos = soilPos.toCenterPos().add(0.0, 0.5, 0.0);
        return new BlockHitResult(hitPos, Direction.UP, soilPos, false);
    }

    private boolean canReplantAt(BlockPos pos, Item seed) {
        BlockState soil = mc.world.getBlockState(pos.down());

        if (seed == Items.NETHER_WART) {
            return soil.isOf(Blocks.SOUL_SAND);
        }

        if (seed == Items.COCOA_BEANS) {
            for (Direction direction : Direction.Type.HORIZONTAL) {
                if (mc.world.getBlockState(pos.offset(direction)).isIn(BlockTags.JUNGLE_LOGS)) return true;
            }
            return false;
        }

        return isFarmlandSeed(seed) && soil.isOf(Blocks.FARMLAND);
    }

    private boolean shouldHarvestByMining(BlockPos pos, BlockState state) {
        Block block = state.getBlock();

        if (block instanceof CropBlock crop && crop.isMature(state)) return true;

        if (block == Blocks.NETHER_WART && state.contains(NetherWartBlock.AGE)) {
            return state.get(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
        }

        if (block == Blocks.COCOA && state.contains(CocoaBlock.AGE)) {
            return state.get(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE;
        }

        if (block == Blocks.MELON || block == Blocks.PUMPKIN) return true;
        if (block == Blocks.SUGAR_CANE) return mc.world.getBlockState(pos.down()).isOf(Blocks.SUGAR_CANE);
        if (block == Blocks.CACTUS) return mc.world.getBlockState(pos.down()).isOf(Blocks.CACTUS);
        if (block == Blocks.BAMBOO) return mc.world.getBlockState(pos.down()).isOf(Blocks.BAMBOO);
        if (block == Blocks.KELP_PLANT) return mc.world.getBlockState(pos.down()).isOf(Blocks.KELP_PLANT) || mc.world.getBlockState(pos.down()).isOf(Blocks.KELP);
        if (block == Blocks.KELP) return mc.world.getBlockState(pos.down()).isOf(Blocks.KELP_PLANT);

        return false;
    }

    private boolean shouldHarvestByInteracting(BlockState state) {
        Block block = state.getBlock();

        if (block == Blocks.SWEET_BERRY_BUSH && state.contains(SweetBerryBushBlock.AGE)) {
            return state.get(SweetBerryBushBlock.AGE) >= 3;
        }

        return CaveVines.hasBerries(state);
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
        Vec3d eyes = mc.player.getEyePos();
        Vec3d target = pos.toCenterPos();

        HitResult hit = mc.world.raycast(new RaycastContext(
            eyes,
            target,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
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
