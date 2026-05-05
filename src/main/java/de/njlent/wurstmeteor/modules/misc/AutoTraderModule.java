package de.njlent.wurstmeteor.modules.misc;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.List;

public class AutoTraderModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<Item>> sellItems = sgGeneral.add(new ItemListSetting.Builder()
        .name("sell-items")
        .description("Items to sell when a villager offers emeralds for them.")
        .defaultValue(Items.PAPER)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks to wait between trades.")
        .defaultValue(4)
        .range(1, 20)
        .sliderRange(1, 20)
        .build()
    );

    private int cooldown;

    public AutoTraderModule() {
        super(WurstMeteorAddon.CATEGORY, "auto-trader", "Automatically sells selected items to villagers for emeralds.");
    }

    @Override
    public void onActivate() {
        cooldown = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.gameMode == null || mc.getConnection() == null) return;
        if (!(mc.screen instanceof MerchantScreen merchantScreen)) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        MerchantMenu menu = merchantScreen.getMenu();
        List<Item> wanted = sellItems.get();
        if (wanted.isEmpty()) return;

        for (int i = 0; i < menu.getOffers().size(); i++) {
            MerchantOffer offer = menu.getOffers().get(i);
            ItemStack result = offer.getResult();
            ItemStack cost = offer.getCostA();

            if (!result.is(Items.EMERALD)) continue;
            if (cost.isEmpty() || !wanted.contains(cost.getItem())) continue;
            if (count(cost.getItem()) < cost.getCount()) continue;

            menu.setSelectionHint(i);
            menu.tryMoveItems(i);
            mc.getConnection().send(new ServerboundSelectTradePacket(i));
            mc.gameMode.handleContainerInput(menu.containerId, 2, 0, ContainerInput.PICKUP, mc.player);

            cooldown = delay.get();
            return;
        }
    }

    private int count(Item item) {
        int total = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }
}
