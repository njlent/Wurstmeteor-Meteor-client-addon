package de.njlent.wurstmeteor.mixin;

import de.njlent.wurstmeteor.modules.misc.EnchantmentHelperModule;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void wurstmeteor$renderEnchantmentHelper(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (Modules.get() == null) return;

        EnchantmentHelperModule module = Modules.get().get(EnchantmentHelperModule.class);
        if (module == null || !module.isActive()) return;

        module.renderPanel((AbstractContainerScreen<?>) (Object) this, context, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void wurstmeteor$clickEnchantmentHelper(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (Modules.get() == null) return;

        EnchantmentHelperModule module = Modules.get().get(EnchantmentHelperModule.class);
        if (module == null || !module.isActive()) return;

        if (module.handlePanelClick((AbstractContainerScreen<?>) (Object) this, event.x(), event.y(), event.button())) {
            cir.setReturnValue(true);
        }
    }
}
