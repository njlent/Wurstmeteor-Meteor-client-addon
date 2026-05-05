package de.njlent.wurstmeteor.modules.misc;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public class AutoDropModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<Item>> items = sgGeneral.add(new ItemListSetting.Builder()
        .name("items")
        .description("Items that will be automatically dropped.")
        .defaultValue(
            Items.ALLIUM, Items.AZURE_BLUET, Items.BLUE_ORCHID, Items.CORNFLOWER, Items.DANDELION,
            Items.LILAC, Items.LILY_OF_THE_VALLEY, Items.ORANGE_TULIP, Items.OXEYE_DAISY,
            Items.PEONY, Items.PINK_TULIP, Items.POISONOUS_POTATO, Items.POPPY, Items.RED_TULIP,
            Items.ROSE_BUSH, Items.ROTTEN_FLESH, Items.SUNFLOWER, Items.WHEAT_SEEDS, Items.WHITE_TULIP
        )
        .build()
    );

    private final Setting<Integer> dropsPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("drops-per-tick")
        .description("Maximum matching stacks to drop each tick.")
        .defaultValue(3)
        .range(1, 36)
        .sliderRange(1, 36)
        .build()
    );

    public AutoDropModule() {
        super(WurstMeteorAddon.CATEGORY, "auto-drop", "Automatically drops unwanted inventory items.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.gameMode == null) return;
        if (mc.screen instanceof AbstractContainerScreen<?> && !(mc.screen instanceof InventoryScreen)) return;

        int dropped = 0;
        for (int invSlot = 0; invSlot < 36 && dropped < dropsPerTick.get(); invSlot++) {
            ItemStack stack = mc.player.getInventory().getItem(invSlot);
            if (stack.isEmpty() || !items.get().contains(stack.getItem())) continue;

            int menuSlot = findMenuSlot(invSlot);
            if (menuSlot < 0) continue;

            mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, menuSlot, 1, ContainerInput.THROW, mc.player);
            dropped++;
        }
    }

    private int findMenuSlot(int inventorySlot) {
        for (int i = 0; i < mc.player.inventoryMenu.slots.size(); i++) {
            Slot slot = mc.player.inventoryMenu.slots.get(i);
            if (slot.container == mc.player.getInventory() && slot.getContainerSlot() == inventorySlot) return i;
        }
        return -1;
    }
}
