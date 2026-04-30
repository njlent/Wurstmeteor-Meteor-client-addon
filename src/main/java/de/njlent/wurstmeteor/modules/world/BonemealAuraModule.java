package de.njlent.wurstmeteor.modules.world;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import de.njlent.wurstmeteor.util.RotationPackets;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BonemealAuraModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFilters = settings.createGroup("Filters");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum range to apply bonemeal.")
        .defaultValue(5.0)
        .min(1.0)
        .sliderRange(1.0, 6.0)
        .build()
    );

    private final Setting<Boolean> multiMeal = sgGeneral.add(new BoolSetting.Builder()
        .name("multi-meal")
        .description("Applies bonemeal to every valid target in one tick.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> checkLos = sgGeneral.add(new BoolSetting.Builder()
        .name("check-line-of-sight")
        .description("Only targets blocks with direct line-of-sight.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Faces target blocks before using bonemeal.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> fastPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("always-fastplace")
        .description("Ignores the local delay between right-click actions.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> useWhileBreaking = sgGeneral.add(new BoolSetting.Builder()
        .name("use-while-breaking")
        .description("Allows using bonemeal while mining.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> useWhileRiding = sgGeneral.add(new BoolSetting.Builder()
        .name("use-while-riding")
        .description("Allows using bonemeal while mounted.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> saplings = sgFilters.add(new BoolSetting.Builder()
        .name("saplings")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> crops = sgFilters.add(new BoolSetting.Builder()
        .name("crops")
        .description("Wheat, carrots, potatoes, and beetroots.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> stems = sgFilters.add(new BoolSetting.Builder()
        .name("stems")
        .description("Pumpkin and melon stems.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cocoa = sgFilters.add(new BoolSetting.Builder()
        .name("cocoa")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> seaPickles = sgFilters.add(new BoolSetting.Builder()
        .name("sea-pickles")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> other = sgFilters.add(new BoolSetting.Builder()
        .name("other")
        .description("Other bonemealable blocks.")
        .defaultValue(false)
        .build()
    );

    private int useDelay;

    public BonemealAuraModule() {
        super(WurstMeteorAddon.CATEGORY, "bonemeal-aura", "Uses bonemeal on nearby growable blocks.");
    }

    @Override
    public void onActivate() {
        useDelay = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        if (useDelay > 0) useDelay--;
        if (!fastPlace.get() && useDelay > 0) return;

        if (!useWhileBreaking.get() && mc.gameMode.isDestroying()) return;
        if (!useWhileRiding.get() && mc.player.isPassenger()) return;

        AutoFarmModule autoFarm = Modules.get().get(AutoFarmModule.class);
        if (autoFarm != null && autoFarm.isActive() && autoFarm.isBusy()) return;

        FindItemResult bonemeal = InvUtils.findInHotbar(Items.BONE_MEAL);
        if (!bonemeal.found()) return;

        List<TargetBlock> validBlocks = getValidBlocks();
        if (validBlocks.isEmpty()) return;

        if (!bonemeal.isMainHand() && !bonemeal.isOffhand()) {
            InvUtils.swap(bonemeal.slot(), false);
            return;
        }

        InteractionHand hand = bonemeal.getHand() != null ? bonemeal.getHand() : InteractionHand.MAIN_HAND;

        if (multiMeal.get()) {
            boolean used = false;
            for (TargetBlock target : validBlocks) {
                used |= useBonemeal(target, hand, false);
            }
            if (used) {
                mc.player.swing(hand);
                if (!fastPlace.get()) useDelay = 4;
            }
            return;
        }

        if (useBonemeal(validBlocks.getFirst(), hand, true) && !fastPlace.get()) {
            useDelay = 4;
        }
    }

    private boolean useBonemeal(TargetBlock target, InteractionHand hand, boolean swingOnSuccess) {
        if (rotate.get()) RotationPackets.face(target.hitPos());

        BlockHitResult hitResult = new BlockHitResult(target.hitPos(), Direction.UP, target.pos(), false);

        InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, hitResult);
        if (!result.consumesAction()) return false;

        if (swingOnSuccess) mc.player.swing(hand);
        return true;
    }

    private List<TargetBlock> getValidBlocks() {
        BlockPos center = BlockPos.containing(mc.player.getEyePosition());
        int radius = (int) Math.ceil(range.get());
        double rangeSq = range.get() * range.get();

        List<TargetBlock> blocks = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
            center.offset(-radius, -radius, -radius),
            center.offset(radius, radius, radius)
        )) {
            double distanceSq = pos.getCenter().distanceToSqr(mc.player.getEyePosition());
            if (distanceSq > rangeSq) continue;
            if (!isCorrectBlock(pos)) continue;
            if (checkLos.get() && !hasLineOfSight(pos)) continue;

            blocks.add(new TargetBlock(pos.immutable(), pos.getCenter(), distanceSq));
        }

        blocks.sort(Comparator.comparingDouble(TargetBlock::distanceSq));
        return blocks;
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
            return blockHitResult.getBlockPos().equals(pos) || blockHitResult.getBlockPos().equals(pos.below());
        }

        return false;
    }

    private boolean isCorrectBlock(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        Block block = state.getBlock();

        if (!(block instanceof BonemealableBlock fertilizable)) return false;
        if (!fertilizable.isValidBonemealTarget(mc.level, pos, state)) return false;
        if (block instanceof GrassBlock) return false;

        if (block instanceof SaplingBlock) return saplings.get();
        if (block instanceof CropBlock) return crops.get();
        if (block instanceof StemBlock || block instanceof AttachedStemBlock) return stems.get();
        if (block instanceof CocoaBlock) return cocoa.get();
        if (block instanceof SeaPickleBlock) return seaPickles.get();

        return other.get();
    }

    private record TargetBlock(BlockPos pos, Vec3 hitPos, double distanceSq) {
    }
}
