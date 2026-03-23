package de.njlent.wurstmeteor.modules.world;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class AutoMineModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> superFastMode = sgGeneral.add(new BoolSetting.Builder()
        .name("super-fast-mode")
        .description("Breaks blocks faster than normal by also holding attack locally. May get detected by anti-cheat.")
        .defaultValue(false)
        .build()
    );

    private boolean holdingAttackKey;

    public AutoMineModule() {
        super(WurstMeteorAddon.CATEGORY, "auto-mine", "Automatically mines the block you're looking at.");
    }

    @Override
    public void onActivate() {
        holdingAttackKey = false;
        disableConflicts();
    }

    @Override
    public void onDeactivate() {
        stopBreaking();
        KeyBinding.updatePressedStates();
    }

    public boolean shouldCancelVanillaBlockBreaking() {
        return isActive() && !superFastMode.get();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        HitResult hitResult = mc.crosshairTarget;
        if (!(hitResult instanceof BlockHitResult blockHitResult) || hitResult.getType() != HitResult.Type.BLOCK) {
            stopBreaking();
            return;
        }

        BlockPos pos = blockHitResult.getBlockPos();
        BlockState state = mc.world.getBlockState(pos);
        Direction side = blockHitResult.getSide();

        if (state.isAir()) {
            stopBreaking();
            return;
        }

        if (mc.player.isUsingItem()) {
            releaseAttackKey();
            return;
        }

        boolean updated = mc.interactionManager.isBreakingBlock()
            ? mc.interactionManager.updateBlockBreakingProgress(pos, side)
            : mc.interactionManager.attackBlock(pos, side);

        if (!updated && mc.interactionManager.isBreakingBlock()) {
            mc.interactionManager.cancelBlockBreaking();
            updated = mc.interactionManager.attackBlock(pos, side);
        }

        if (!updated) {
            releaseAttackKey();
            return;
        }

        mc.player.swingHand(Hand.MAIN_HAND);

        if (superFastMode.get()) {
            pressAttackKey();
        } else {
            releaseAttackKey();
        }
    }

    private void disableConflicts() {
        Modules modules = Modules.get();

        AutoFarmModule autoFarm = modules.get(AutoFarmModule.class);
        if (autoFarm != null && autoFarm.isActive()) autoFarm.toggle();

        TreeBotModule treeBot = modules.get(TreeBotModule.class);
        if (treeBot != null && treeBot.isActive()) treeBot.toggle();
    }

    private void stopBreaking() {
        releaseAttackKey();

        if (mc.interactionManager != null && mc.interactionManager.isBreakingBlock()) {
            mc.interactionManager.cancelBlockBreaking();
        }
    }

    private void pressAttackKey() {
        if (holdingAttackKey) return;

        mc.options.attackKey.setPressed(true);
        holdingAttackKey = true;
    }

    private void releaseAttackKey() {
        if (!holdingAttackKey) return;

        mc.options.attackKey.setPressed(false);
        holdingAttackKey = false;
    }
}
