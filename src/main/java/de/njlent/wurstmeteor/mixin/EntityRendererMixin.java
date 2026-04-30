package de.njlent.wurstmeteor.mixin;

import de.njlent.wurstmeteor.modules.render.HealthTagsModule;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {
    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void wurstmeteor$addHealthToDisplayName(T entity, S state, float tickProgress, CallbackInfo ci) {
        if (Modules.get() == null) return;
        if (state.nameTag == null) return;
        if (!(entity instanceof LivingEntity living)) return;

        HealthTagsModule healthTags = Modules.get().get(HealthTagsModule.class);
        if (healthTags == null || !healthTags.isActive()) return;

        state.nameTag = healthTags.addHealth(living, state.nameTag.copy());
    }
}
