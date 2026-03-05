package de.njlent.wurstmeteor.modules.movement;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import de.njlent.wurstmeteor.mixin.CreativeInventoryScreenAccessor;
import de.njlent.wurstmeteor.util.KeyBindingUtils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemGroups;
import net.minecraft.client.option.KeyBinding;

import java.util.ArrayList;
import java.util.Arrays;

public class InvWalkModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> allowClickGui = sgGeneral.add(new BoolSetting.Builder()
        .name("allow-click-gui")
        .description("Allows movement while Meteor GUI screens are open.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> allowOther = sgGeneral.add(new BoolSetting.Builder()
        .name("allow-other-screens")
        .description("Allows movement in other container screens without text boxes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> allowSneak = sgGeneral.add(new BoolSetting.Builder()
        .name("allow-sneak-key")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> allowSprint = sgGeneral.add(new BoolSetting.Builder()
        .name("allow-sprint-key")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> allowJump = sgGeneral.add(new BoolSetting.Builder()
        .name("allow-jump-key")
        .defaultValue(true)
        .build()
    );

    public InvWalkModule() {
        super(WurstMeteorAddon.CATEGORY, "inv-walk", "Lets you walk while inventory and GUI screens are open.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        Screen screen = mc.currentScreen;
        if (screen == null) return;
        if (!isAllowedScreen(screen)) return;

        ArrayList<KeyBinding> keys = new ArrayList<>(Arrays.asList(
            mc.options.forwardKey,
            mc.options.backKey,
            mc.options.leftKey,
            mc.options.rightKey
        ));

        if (allowSneak.get()) keys.add(mc.options.sneakKey);
        if (allowSprint.get()) keys.add(mc.options.sprintKey);
        if (allowJump.get()) keys.add(mc.options.jumpKey);

        for (KeyBinding key : keys) KeyBindingUtils.resetPressedState(key);
    }

    private boolean isAllowedScreen(Screen screen) {
        if ((screen instanceof InventoryScreen || screen instanceof CreativeInventoryScreen) && !isCreativeSearchBarOpen(screen)) {
            return true;
        }

        if (allowClickGui.get() && screen instanceof WidgetScreen) return true;

        return allowOther.get() && screen instanceof HandledScreen<?> && !hasTextBox(screen);
    }

    private boolean isCreativeSearchBarOpen(Screen screen) {
        if (!(screen instanceof CreativeInventoryScreen)) return false;
        return CreativeInventoryScreenAccessor.wurstmeteor$getSelectedTab() == ItemGroups.getSearchGroup();
    }

    private boolean hasTextBox(Screen screen) {
        for (Element child : screen.children()) {
            if (child instanceof TextFieldWidget) return true;
        }

        return false;
    }
}
