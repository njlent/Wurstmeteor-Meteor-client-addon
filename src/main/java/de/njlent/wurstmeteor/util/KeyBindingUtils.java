package de.njlent.wurstmeteor.util;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public final class KeyBindingUtils {
    private KeyBindingUtils() {
    }

    public static void resetPressedState(KeyBinding keyBinding) {
        MinecraftClient mc = MeteorClient.mc;
        if (mc == null) return;

        InputUtil.Key key = InputUtil.fromTranslationKey(keyBinding.getBoundKeyTranslationKey());
        keyBinding.setPressed(InputUtil.isKeyPressed(mc.getWindow().getHandle(), key.getCode()));
    }
}
