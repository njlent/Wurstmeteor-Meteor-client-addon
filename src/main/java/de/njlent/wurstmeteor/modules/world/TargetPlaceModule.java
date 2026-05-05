package de.njlent.wurstmeteor.modules.world;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import de.njlent.wurstmeteor.util.RotationPackets;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class TargetPlaceModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Keybind> activationKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("activation-key")
        .description("Selects or clears the targeted block while TargetPlace is active.")
        .defaultValue(Keybind.none())
        .build()
    );

    private final Setting<Boolean> useModuleBind = sgGeneral.add(new BoolSetting.Builder()
        .name("use-module-bind")
        .description("Uses activation on module enable if no activation key is set.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> attemptsPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("attempts-per-tick")
        .description("Placement attempts sent each tick while a target is armed.")
        .defaultValue(3)
        .range(1, 8)
        .sliderRange(1, 8)
        .build()
    );

    private final Setting<SettingColor> highlightColor = sgGeneral.add(new ColorSetting.Builder()
        .name("highlight-color")
        .description("Color used to render the selected target.")
        .defaultValue(new SettingColor(0, 255, 255, 150))
        .build()
    );

    private BlockPos targetBlock;
    private boolean activationWasPressed;

    public TargetPlaceModule() {
        super(WurstMeteorAddon.CATEGORY, "target-place", "Places blocks the instant a selected target block becomes air.");
    }

    @Override
    public void onActivate() {
        activationWasPressed = false;
        if (useModuleBind.get() && !activationKey.get().isSet()) handleActivation(false);
    }

    @Override
    public void onDeactivate() {
        targetBlock = null;
        activationWasPressed = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        if (mc.screen != null) {
            activationWasPressed = false;
            return;
        }

        boolean pressed = activationKey.get().isSet() && activationKey.get().isPressed();
        if (pressed && !activationWasPressed) handleActivation(mc.options.keyShift.isDown());
        activationWasPressed = pressed;

        if (targetBlock == null || mc.player.getMainHandItem().isEmpty()) return;
        if (mc.level.getBlockState(targetBlock).isAir()) attemptPlace(targetBlock);
        for (int i = 0; i < attemptsPerTick.get(); i++) attemptPlace(targetBlock);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (targetBlock == null || mc.level == null) return;

        BlockState state = mc.level.getBlockState(targetBlock);
        if (state.isAir()) return;

        Color side = new Color(highlightColor.get());
        Color line = new Color(highlightColor.get()).a(220);
        event.renderer.box(new AABB(targetBlock), side, line, ShapeMode.Lines, 0);
    }

    private void handleActivation(boolean shiftDown) {
        BlockPos lookedPos = mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;

        if (lookedPos != null && lookedPos.equals(targetBlock)) {
            if (shiftDown) {
                targetBlock = null;
                info("TargetPlace selection cleared.");
            }
            return;
        }

        if (lookedPos == null || !isTargetValid(lookedPos)) return;

        targetBlock = lookedPos.immutable();
        faceUnderside(targetBlock);
        info("Selected %s at %s.", BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(targetBlock).getBlock()).toString(), targetBlock.toShortString());
    }

    private boolean isTargetValid(BlockPos blockPos) {
        if (blockPos == null || mc.level == null) return false;
        BlockState state = mc.level.getBlockState(blockPos);
        return !state.isAir() && state.getDestroySpeed(mc.level, blockPos) >= 0;
    }

    private void attemptPlace(BlockPos target) {
        Placement placement = findPlacement(target);
        if (placement == null) return;

        ItemStack stack = mc.player.getMainHandItem();
        if (stack.isEmpty()) return;

        faceUnderside(target);
        RotationPackets.face(placement.hitVec());

        InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(placement.hitVec(), placement.side(), placement.neighbor(), false));
        if (result.consumesAction()) mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private void faceUnderside(BlockPos target) {
        BlockPos above = target.relative(Direction.UP);
        Vec3 hitVec = Vec3.atLowerCornerOf(above).add(0.5, -0.5, 0.5);
        RotationPackets.face(hitVec);
    }

    private Placement findPlacement(BlockPos target) {
        BlockPos above = target.relative(Direction.UP);
        if (mc.level.getBlockState(above).isAir()) return null;

        Vec3 center = Vec3.atLowerCornerOf(above).add(0.5, 0.5, 0.5);
        Vec3[] offsets = new Vec3[] {
            new Vec3(0.2, -0.5, 0.2), new Vec3(0.2, -0.5, -0.2),
            new Vec3(-0.2, -0.5, 0.2), new Vec3(-0.2, -0.5, -0.2),
            new Vec3(0.0, -0.5, 0.2), new Vec3(0.2, -0.5, 0.0),
            new Vec3(0.0, -0.5, -0.2), new Vec3(-0.2, -0.5, 0.0)
        };

        for (Vec3 offset : offsets) {
            Vec3 hitVec = center.add(offset);
            return new Placement(above, Direction.DOWN, hitVec);
        }

        return new Placement(above, Direction.DOWN, center.add(0, -0.5, 0));
    }

    private record Placement(BlockPos neighbor, Direction side, Vec3 hitVec) {}
}
