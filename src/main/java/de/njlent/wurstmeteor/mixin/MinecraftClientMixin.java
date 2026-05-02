package de.njlent.wurstmeteor.mixin;

import de.njlent.wurstmeteor.modules.world.AutoMineModule;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftClientMixin {
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void wurstmeteor$cancelVanillaBlockBreaking(boolean breaking, CallbackInfo ci) {
        if (Modules.get() == null) return;

        AutoMineModule autoMine = Modules.get().get(AutoMineModule.class);
        if (autoMine == null || !autoMine.shouldCancelVanillaBlockBreaking()) return;

        ci.cancel();
    }
}
