package de.njlent.wurstmeteor.mixin;

import de.njlent.wurstmeteor.modules.render.HealthTagsModule;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {
    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void wurstmeteor$addHealthToDisplayName(T entity, S state, float tickProgress, CallbackInfo ci) {
        if (Modules.get() == null) return;
        if (state.displayName == null) return;
        if (!(entity instanceof LivingEntity living)) return;

        HealthTagsModule healthTags = Modules.get().get(HealthTagsModule.class);
        if (healthTags == null || !healthTags.isActive()) return;

        state.displayName = healthTags.addHealth(living, state.displayName.copy());
    }
}
