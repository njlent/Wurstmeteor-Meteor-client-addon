package de.njlent.wurstmeteor.mixin;

import de.njlent.wurstmeteor.modules.render.BarrierEspModule;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public class ClientWorldMixin {
    @Inject(method = "getMarkerParticleTarget", at = @At("HEAD"), cancellable = true)
    private void wurstmeteor$onGetBlockParticle(CallbackInfoReturnable<Block> cir) {
        if (Modules.get() == null) return;

        BarrierEspModule barrierEsp = Modules.get().get(BarrierEspModule.class);
        if (barrierEsp == null || !barrierEsp.isActive()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null
            && minecraft.player != null
            && minecraft.gameMode.getPlayerMode() == GameType.CREATIVE
            && minecraft.player.getMainHandItem().is(Items.LIGHT)) {
            return;
        }

        cir.setReturnValue(Blocks.BARRIER);
    }
}
