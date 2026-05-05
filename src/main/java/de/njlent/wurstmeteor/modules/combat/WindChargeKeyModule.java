package de.njlent.wurstmeteor.modules.combat;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class WindChargeKeyModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Keybind> activationKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("activation-key")
        .description("Throws a wind charge while this module is enabled.")
        .defaultValue(Keybind.none())
        .build()
    );

    private final Setting<Integer> switchDelayMs = sgGeneral.add(new IntSetting.Builder()
        .name("switch-delay-ms")
        .description("Delay before switching back to the original slot.")
        .defaultValue(50)
        .range(0, 500)
        .sliderRange(0, 250)
        .build()
    );

    private final Setting<Integer> throwDelayMs = sgGeneral.add(new IntSetting.Builder()
        .name("throw-delay-ms")
        .description("Minimum delay between throws.")
        .defaultValue(200)
        .range(0, 1000)
        .sliderRange(0, 1000)
        .build()
    );

    private final Setting<Boolean> silent = sgGeneral.add(new BoolSetting.Builder()
        .name("silent")
        .description("Restores the original hotbar slot immediately after throwing.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoJump = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-jump")
        .description("Jumps before throwing when on the ground.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> lookDown = sgGeneral.add(new BoolSetting.Builder()
        .name("look-down")
        .description("Looks straight down before throwing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> jumpDelayMs = sgGeneral.add(new IntSetting.Builder()
        .name("jump-delay-ms")
        .description("Delay between jump and wind charge throw.")
        .defaultValue(10)
        .range(0, 200)
        .sliderRange(0, 200)
        .build()
    );

    private boolean keyWasPressed;
    private boolean pendingThrow;
    private boolean pendingRestore;
    private int pendingSlot = -1;
    private int originalSlot = -1;
    private long pendingThrowAt;
    private long restoreAt;
    private long lastThrowAt;
    private boolean restoringPitch;
    private float savedPitch;
    private long restorePitchAt;

    public WindChargeKeyModule() {
        super(WurstMeteorAddon.CATEGORY, "wind-charge-key", "Throws wind charges from a Meteor keybind with jump and look-down helpers.");
    }

    @Override
    public void onDeactivate() {
        restoreSlotNow();
        restorePitchNow();
        reset();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            reset();
            return;
        }

        if (mc.screen != null) return;

        boolean pressed = activationKey.get().isSet() && activationKey.get().isPressed();
        if (pressed && !keyWasPressed) scheduleThrow();
        keyWasPressed = pressed;

        long now = System.currentTimeMillis();
        if (pendingThrow && now >= pendingThrowAt) {
            int slot = pendingSlot;
            pendingThrow = false;
            pendingSlot = -1;
            throwWindCharge(slot);
        }

        if (restoringPitch && now >= restorePitchAt) restorePitchNow();
        if (pendingRestore && now >= restoreAt) restoreSlotNow();
    }

    private void scheduleThrow() {
        long now = System.currentTimeMillis();
        if (now - lastThrowAt < throwDelayMs.get()) return;

        int slot = findWindCharge();
        if (slot == -1) return;

        if (autoJump.get() && mc.player.onGround()) {
            mc.player.jumpFromGround();
            pendingThrow = true;
            pendingSlot = slot;
            pendingThrowAt = now + jumpDelayMs.get();
            return;
        }

        throwWindCharge(slot);
    }

    private void throwWindCharge(int slot) {
        if (slot < 0 || slot > 8 || !mc.player.getInventory().getItem(slot).is(Items.WIND_CHARGE)) return;

        originalSlot = mc.player.getInventory().getSelectedSlot();
        float oldPitch = mc.player.getXRot();
        if (lookDown.get()) lookDown();

        mc.player.getInventory().setSelectedSlot(slot);
        boolean success = useSelected();

        long now = System.currentTimeMillis();
        if (success) {
            lastThrowAt = now;
            if (silent.get()) {
                mc.player.getInventory().setSelectedSlot(originalSlot);
                originalSlot = -1;
            } else {
                pendingRestore = true;
                restoreAt = now + switchDelayMs.get();
            }
        } else {
            mc.player.getInventory().setSelectedSlot(originalSlot);
            originalSlot = -1;
        }

        if (lookDown.get()) {
            savedPitch = oldPitch;
            restoringPitch = true;
            restorePitchAt = now + 150L;
        }
    }

    private boolean useSelected() {
        ItemStack stack = mc.player.getMainHandItem();
        if (!stack.is(Items.WIND_CHARGE)) return false;

        InteractionResult result = mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        if (result.consumesAction()) mc.player.swing(InteractionHand.MAIN_HAND);
        return result.consumesAction();
    }

    private void lookDown() {
        mc.player.setXRot(90.0F);
        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(mc.player.getYRot(), 90.0F, mc.player.onGround(), mc.player.horizontalCollision));
        }
    }

    private void restorePitchNow() {
        if (!restoringPitch || mc.player == null) return;

        mc.player.setXRot(savedPitch);
        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(mc.player.getYRot(), savedPitch, mc.player.onGround(), mc.player.horizontalCollision));
        }

        restoringPitch = false;
        restorePitchAt = 0;
    }

    private void restoreSlotNow() {
        if (pendingRestore && mc.player != null && originalSlot >= 0 && originalSlot < 9) mc.player.getInventory().setSelectedSlot(originalSlot);
        pendingRestore = false;
        originalSlot = -1;
    }

    private int findWindCharge() {
        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getItem(slot).is(Items.WIND_CHARGE)) return slot;
        }

        return -1;
    }

    private void reset() {
        keyWasPressed = false;
        pendingThrow = false;
        pendingRestore = false;
        pendingSlot = -1;
        originalSlot = -1;
        pendingThrowAt = 0;
        restoreAt = 0;
        restoringPitch = false;
        restorePitchAt = 0;
        savedPitch = 0.0F;
    }
}
