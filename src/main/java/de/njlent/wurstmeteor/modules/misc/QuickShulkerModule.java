package de.njlent.wurstmeteor.modules.misc;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.List;

public class QuickShulkerModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> autoRun = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-run")
        .description("Starts filling a shulker as soon as its screen opens.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Keybind> triggerKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("trigger-key")
        .description("Starts filling the open shulker when pressed.")
        .defaultValue(Keybind.none())
        .build()
    );

    private final Setting<TransferMode> mode = sgGeneral.add(new EnumSetting.Builder<TransferMode>()
        .name("items-to-move")
        .description("Which item stacks should be moved into the shulker.")
        .defaultValue(TransferMode.All)
        .build()
    );

    private final Setting<Boolean> skipHotbar = sgGeneral.add(new BoolSetting.Builder()
        .name("skip-hotbar")
        .description("Does not move hotbar items.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> useWhitelist = sgGeneral.add(new BoolSetting.Builder()
        .name("use-whitelist")
        .description("Only moves items in the whitelist when the list is not empty.")
        .defaultValue(false)
        .build()
    );

    private final Setting<List<Item>> whitelist = sgGeneral.add(new ItemListSetting.Builder()
        .name("whitelist")
        .description("Items QuickShulker may move when whitelist mode is enabled.")
        .build()
    );

    private final Setting<Boolean> useBlacklist = sgGeneral.add(new BoolSetting.Builder()
        .name("use-blacklist")
        .description("Does not move items in the blacklist.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<Item>> blacklist = sgGeneral.add(new ItemListSetting.Builder()
        .name("blacklist")
        .description("Items QuickShulker should not move.")
        .build()
    );

    private final Setting<Integer> stacksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("stacks-per-tick")
        .description("Maximum stacks moved per tick.")
        .defaultValue(3)
        .range(1, 36)
        .sliderRange(1, 18)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between transfer bursts.")
        .defaultValue(1)
        .range(0, 20)
        .sliderRange(0, 10)
        .build()
    );

    private AbstractContainerScreen<?> lastScreen;
    private boolean running;
    private boolean triggerWasPressed;
    private int cooldown;

    public QuickShulkerModule() {
        super(WurstMeteorAddon.CATEGORY, "quick-shulker", "Quickly fills an open shulker box from your inventory.");
    }

    @Override
    public void onActivate() {
        reset();
    }

    @Override
    public void onDeactivate() {
        reset();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.gameMode == null || !(mc.screen instanceof ShulkerBoxScreen screen) || !(screen.getMenu() instanceof ShulkerBoxMenu menu)) {
            resetScreenState();
            return;
        }

        if (screen != lastScreen) {
            lastScreen = screen;
            running = autoRun.get();
            cooldown = 0;
        }

        boolean pressed = triggerKey.get().isSet() && triggerKey.get().isPressed();
        if (pressed && !triggerWasPressed) running = true;
        triggerWasPressed = pressed;

        if (!running) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        int moved = moveStacks(menu);
        if (moved == 0 || !hasContainerSpace(menu)) {
            running = false;
            return;
        }

        cooldown = delay.get();
    }

    private int moveStacks(ShulkerBoxMenu menu) {
        Inventory inventory = mc.player.getInventory();
        int moved = 0;

        for (Slot slot : menu.slots) {
            if (moved >= stacksPerTick.get()) break;
            if (slot.container != inventory) continue;
            if (!slot.hasItem()) continue;

            int playerSlot = slot.getContainerSlot();
            if (skipHotbar.get() && playerSlot >= 0 && playerSlot < 9) continue;

            ItemStack stack = slot.getItem();
            if (!shouldMove(stack)) continue;

            mc.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, mc.player);
            moved++;
        }

        return moved;
    }

    private boolean shouldMove(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (isShulker(stack)) return false;
        if (mode.get() == TransferMode.Stackable && !stack.isStackable()) return false;
        if (mode.get() == TransferMode.NonStackable && stack.isStackable()) return false;
        if (useBlacklist.get() && blacklist.get().contains(stack.getItem())) return false;
        return !useWhitelist.get() || whitelist.get().isEmpty() || whitelist.get().contains(stack.getItem());
    }

    private boolean hasContainerSpace(ShulkerBoxMenu menu) {
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory) continue;
            if (!slot.hasItem()) return true;
        }

        return false;
    }

    private boolean isShulker(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    private void resetScreenState() {
        lastScreen = null;
        running = false;
        triggerWasPressed = false;
        cooldown = 0;
    }

    private void reset() {
        resetScreenState();
    }

    public enum TransferMode {
        All,
        Stackable,
        NonStackable
    }
}
