package de.njlent.wurstmeteor.mixin;

import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerStateData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;
import java.util.UUID;

@Mixin(TrialSpawnerStateData.class)
public interface TrialSpawnerStateDataAccessor {
    @Accessor("cooldownEndsAt")
    long wurstmeteor$getCooldownEndsAt();

    @Accessor("nextMobSpawnsAt")
    long wurstmeteor$getNextMobSpawnsAt();

    @Accessor("totalMobsSpawned")
    int wurstmeteor$getTotalMobsSpawned();

    @Accessor("currentMobs")
    Set<UUID> wurstmeteor$getCurrentMobs();
}
