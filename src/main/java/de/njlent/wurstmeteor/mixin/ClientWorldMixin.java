package de.njlent.wurstmeteor.mixin;

import de.njlent.wurstmeteor.modules.render.BarrierEspModule;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Items;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.class)
public class ClientWorldMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "getBlockParticle", at = @At("HEAD"), cancellable = true)
    private void wurstmeteor$onGetBlockParticle(CallbackInfoReturnable<Block> cir) {
        if (Modules.get() == null) return;

        BarrierEspModule barrierEsp = Modules.get().get(BarrierEspModule.class);
        if (barrierEsp == null || !barrierEsp.isActive()) return;

        if (client.interactionManager != null
            && client.player != null
            && client.interactionManager.getCurrentGameMode() == GameMode.CREATIVE
            && client.player.getMainHandStack().isOf(Items.LIGHT)) {
            return;
        }

        cir.setReturnValue(Blocks.BARRIER);
    }
}
