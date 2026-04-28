package de.njlent.wurstmeteor.modules.world;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

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
        KeyMapping.setAll();
    }

    public boolean shouldCancelVanillaBlockBreaking() {
        return isActive() && !superFastMode.get();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        HitResult hitResult = mc.hitResult;
        if (!(hitResult instanceof BlockHitResult blockHitResult) || hitResult.getType() != HitResult.Type.BLOCK) {
            stopBreaking();
            return;
        }

        BlockPos pos = blockHitResult.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        Direction side = blockHitResult.getDirection();

        if (state.isAir()) {
            stopBreaking();
            return;
        }

        if (mc.player.isUsingItem()) {
            releaseAttackKey();
            return;
        }

        boolean updated = mc.gameMode.isDestroying()
            ? mc.gameMode.continueDestroyBlock(pos, side)
            : mc.gameMode.startDestroyBlock(pos, side);

        if (!updated && mc.gameMode.isDestroying()) {
            mc.gameMode.stopDestroyBlock();
            updated = mc.gameMode.startDestroyBlock(pos, side);
        }

        if (!updated) {
            releaseAttackKey();
            return;
        }

        mc.player.swing(InteractionHand.MAIN_HAND);

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

        if (mc.gameMode != null && mc.gameMode.isDestroying()) {
            mc.gameMode.stopDestroyBlock();
        }
    }

    private void pressAttackKey() {
        if (holdingAttackKey) return;

        mc.options.keyAttack.setDown(true);
        holdingAttackKey = true;
    }

    private void releaseAttackKey() {
        if (!holdingAttackKey) return;

        mc.options.keyAttack.setDown(false);
        holdingAttackKey = false;
    }
}
