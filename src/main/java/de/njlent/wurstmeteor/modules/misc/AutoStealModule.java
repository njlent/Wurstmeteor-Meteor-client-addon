package de.njlent.wurstmeteor.modules.misc;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AutoStealModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between moved stacks.")
        .defaultValue(2)
        .range(0, 20)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Integer> stacksPerBurst = sgGeneral.add(new IntSetting.Builder()
        .name("stacks-per-burst")
        .description("Stacks to move when delay reaches zero.")
        .defaultValue(1)
        .range(1, 54)
        .sliderRange(1, 27)
        .build()
    );

    private final Setting<Boolean> reverse = sgGeneral.add(new BoolSetting.Builder()
        .name("reverse")
        .description("Steals from the end of the container first.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> listOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("list-only")
        .description("Only steals items from the item list.")
        .defaultValue(false)
        .build()
    );

    private final Setting<List<Item>> itemList = sgGeneral.add(new ItemListSetting.Builder()
        .name("item-list")
        .description("Items AutoSteal is allowed to move when list-only is enabled.")
        .build()
    );

    private AbstractContainerScreen<?> lastScreen;
    private int cooldown;

    public AutoStealModule() {
        super(WurstMeteorAddon.CATEGORY, "auto-steal", "Automatically shift-clicks items out of chest and shulker inventories.");
    }

    @Override
    public void onActivate() {
        lastScreen = null;
        cooldown = 0;
    }

    @Override
    public void onDeactivate() {
        lastScreen = null;
        cooldown = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.gameMode == null) return;
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen) || screen instanceof CreativeModeInventoryScreen) {
            lastScreen = null;
            cooldown = 0;
            return;
        }

        if (!isSupported(screen)) return;

        if (screen != lastScreen) {
            lastScreen = screen;
            cooldown = 0;
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        int moved = steal(screen);
        if (moved > 0) cooldown = delay.get();
    }

    private int steal(AbstractContainerScreen<?> screen) {
        List<Slot> slots = new ArrayList<>();
        int containerSlots = Math.max(0, screen.getMenu().slots.size() - 36);
        for (int i = 0; i < containerSlots; i++) slots.add(screen.getMenu().slots.get(i));
        if (reverse.get()) Collections.reverse(slots);

        int moved = 0;
        for (Slot slot : slots) {
            if (moved >= stacksPerBurst.get()) break;
            if (slot.getItem().isEmpty()) continue;
            if (listOnly.get() && !itemList.get().contains(slot.getItem().getItem())) continue;

            mc.gameMode.handleContainerInput(screen.getMenu().containerId, slot.index, 0, ContainerInput.QUICK_MOVE, mc.player);
            moved++;
        }

        return moved;
    }

    private boolean isSupported(AbstractContainerScreen<?> screen) {
        return screen.getMenu() instanceof ChestMenu || screen.getMenu() instanceof ShulkerBoxMenu;
    }
}
