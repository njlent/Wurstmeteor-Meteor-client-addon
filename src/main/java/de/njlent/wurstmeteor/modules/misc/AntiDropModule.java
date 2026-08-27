package de.njlent.wurstmeteor.modules.misc;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class AntiDropModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> protectEnchantedGear = sgGeneral.add(new BoolSetting.Builder()
        .name("protect-enchanted-gear")
        .description("Also blocks dropping enchanted damageable gear and tools, even if it is not in the item list.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<Item>> protectedItems = sgGeneral.add(new ItemListSetting.Builder()
        .name("protected-items")
        .description("Items that cannot be dropped while AntiDrop is enabled.")
        .defaultValue(defaultProtectedItems())
        .build()
    );

    private static List<Item> defaultProtectedItems() {
        List<Item> items = new ArrayList<>(List.of(
            Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD,
            Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.GOLDEN_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE,
            Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.GOLDEN_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE,
            Items.WOODEN_SHOVEL, Items.STONE_SHOVEL, Items.IRON_SHOVEL, Items.GOLDEN_SHOVEL, Items.DIAMOND_SHOVEL, Items.NETHERITE_SHOVEL,
            Items.WOODEN_HOE, Items.STONE_HOE, Items.IRON_HOE, Items.GOLDEN_HOE, Items.DIAMOND_HOE, Items.NETHERITE_HOE,
            Items.BOW, Items.CROSSBOW, Items.TRIDENT, Items.MACE, Items.SHIELD, Items.ELYTRA,
            Items.SHULKER_BOX
        ));
        items.addAll(Items.DYED_SHULKER_BOX.asList());
        return items;
    }

    public AntiDropModule() {
        super(WurstMeteorAddon.CATEGORY, "anti-drop", "Prevents protected items from being dropped.");
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null) return;
        if (!(event.packet instanceof ServerboundPlayerActionPacket packet)) return;
        if (packet.getAction() != ServerboundPlayerActionPacket.Action.DROP_ITEM
            && packet.getAction() != ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS) return;

        if (shouldBlock(mc.player.getMainHandItem())) {
            event.cancel();
            warnBlocked(mc.player.getMainHandItem());
        }
    }

    public boolean shouldBlock(ItemStack stack) {
        if (!isActive() || stack == null || stack.isEmpty()) return false;
        if (protectedItems.get().contains(stack.getItem())) return true;
        return protectEnchantedGear.get() && stack.isEnchanted() && stack.isDamageableItem();
    }

    public void warnBlocked(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        warning("Blocked dropping %s.", stack.getHoverName().getString());
    }
}
