package de.njlent.wurstmeteor.mixin;

import de.njlent.wurstmeteor.modules.misc.AntiDropModule;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
    @Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true)
    private void wurstmeteor$blockProtectedThrow(int containerId, int slotId, int button, ContainerInput input, Player player, CallbackInfo ci) {
        if (input != ContainerInput.THROW || player == null || Modules.get() == null) return;

        AntiDropModule antiDrop = Modules.get().get(AntiDropModule.class);
        if (antiDrop == null || !antiDrop.isActive()) return;

        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu.containerId != containerId) return;
        if (slotId < 0 || slotId >= menu.slots.size()) return;

        Slot slot = menu.slots.get(slotId);
        if (antiDrop.shouldBlock(slot.getItem())) ci.cancel();
    }
}
