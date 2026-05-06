package de.njlent.wurstmeteor.mixin;

import net.minecraft.world.level.block.entity.vault.VaultSharedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;
import java.util.UUID;

@Mixin(VaultSharedData.class)
public interface VaultSharedDataAccessor {
    @Accessor("connectedPlayers")
    Set<UUID> wurstmeteor$getConnectedPlayers();
}
