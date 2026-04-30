package de.njlent.wurstmeteor.util;

import net.minecraft.client.KeyMapping;

public final class KeyBindingUtils {
    private KeyBindingUtils() {
    }

    public static void resetPressedState(KeyMapping keyBinding) {
        KeyMapping.setAll();
    }
}
