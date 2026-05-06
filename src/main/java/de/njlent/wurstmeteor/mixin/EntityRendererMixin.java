package de.njlent.wurstmeteor.mixin;

import de.njlent.wurstmeteor.modules.render.HealthTagsModule;
import de.njlent.wurstmeteor.modules.render.MobHealthModule;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void wurstmeteor$addHealthToDisplayName(T entity, S state, float tickProgress, CallbackInfo ci) {
        if (Modules.get() == null) return;
        if (!(entity instanceof LivingEntity living)) return;

        if (entity instanceof Mob mob) {
            MobHealthModule mobHealth = Modules.get().get(MobHealthModule.class);
            if (mobHealth != null && mobHealth.shouldDisplayFor(mob) && mobHealth.shouldForceNameTag()) {
                state.nameTag = mobHealth.getDisplayText(mob, state.nameTag == null ? null : state.nameTag.copy());
                state.nameTagAttachment = mob.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, mob.getYRot(tickProgress));
                return;
            }
        }

        if (state.nameTag == null) return;

        HealthTagsModule healthTags = Modules.get().get(HealthTagsModule.class);
        if (healthTags == null || !healthTags.isActive()) return;
        state.nameTag = healthTags.addHealth(living, state.nameTag.copy());
    }
}
