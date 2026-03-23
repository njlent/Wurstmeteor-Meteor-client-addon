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
import net.minecraft.block.*;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

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
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (useDelay > 0) useDelay--;
        if (!fastPlace.get() && useDelay > 0) return;

        if (!useWhileBreaking.get() && mc.interactionManager.isBreakingBlock()) return;
        if (!useWhileRiding.get() && mc.player.hasVehicle()) return;

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

        Hand hand = bonemeal.getHand() != null ? bonemeal.getHand() : Hand.MAIN_HAND;

        if (multiMeal.get()) {
            boolean used = false;
            for (TargetBlock target : validBlocks) {
                used |= useBonemeal(target, hand, false);
            }
            if (used) {
                mc.player.swingHand(hand);
                if (!fastPlace.get()) useDelay = 4;
            }
            return;
        }

        if (useBonemeal(validBlocks.getFirst(), hand, true) && !fastPlace.get()) {
            useDelay = 4;
        }
    }

    private boolean useBonemeal(TargetBlock target, Hand hand, boolean swingOnSuccess) {
        if (rotate.get()) RotationPackets.face(target.hitPos());

        BlockHitResult hitResult = new BlockHitResult(target.hitPos(), Direction.UP, target.pos(), false);

        ActionResult result = mc.interactionManager.interactBlock(mc.player, hand, hitResult);
        if (!result.isAccepted()) return false;

        if (swingOnSuccess) mc.player.swingHand(hand);
        return true;
    }

    private List<TargetBlock> getValidBlocks() {
        BlockPos center = BlockPos.ofFloored(mc.player.getEyePos());
        int radius = (int) Math.ceil(range.get());
        double rangeSq = range.get() * range.get();

        List<TargetBlock> blocks = new ArrayList<>();
        for (BlockPos pos : BlockPos.iterateOutwards(center, radius, radius, radius)) {
            double distanceSq = pos.toCenterPos().squaredDistanceTo(mc.player.getEyePos());
            if (distanceSq > rangeSq) continue;
            if (!isCorrectBlock(pos)) continue;
            if (checkLos.get() && !hasLineOfSight(pos)) continue;

            blocks.add(new TargetBlock(pos.toImmutable(), pos.toCenterPos(), distanceSq));
        }

        blocks.sort(Comparator.comparingDouble(TargetBlock::distanceSq));
        return blocks;
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
            return blockHitResult.getBlockPos().equals(pos) || blockHitResult.getBlockPos().equals(pos.down());
        }

        return false;
    }

    private boolean isCorrectBlock(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        Block block = state.getBlock();

        if (!(block instanceof Fertilizable fertilizable)) return false;
        if (!fertilizable.isFertilizable(mc.world, pos, state)) return false;
        if (block instanceof GrassBlock) return false;

        if (block instanceof SaplingBlock) return saplings.get();
        if (block instanceof CropBlock) return crops.get();
        if (block instanceof StemBlock || block instanceof AttachedStemBlock) return stems.get();
        if (block instanceof CocoaBlock) return cocoa.get();
        if (block instanceof SeaPickleBlock) return seaPickles.get();

        return other.get();
    }

    private record TargetBlock(BlockPos pos, Vec3d hitPos, double distanceSq) {
    }
}
