package de.njlent.wurstmeteor.util;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.List;

public final class BlockEntityUtils {
    private BlockEntityUtils() {
    }

    public static List<BlockEntity> getLoadedBlockEntities(ClientLevel level, BlockPos center, int chunkRadius) {
        List<BlockEntity> blockEntities = new ArrayList<>();
        if (level == null || center == null) return blockEntities;

        ChunkPos centerChunk = ChunkPos.containing(center);
        for (int x = centerChunk.x() - chunkRadius; x <= centerChunk.x() + chunkRadius; x++) {
            for (int z = centerChunk.z() - chunkRadius; z <= centerChunk.z() + chunkRadius; z++) {
                LevelChunk chunk = level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                blockEntities.addAll(chunk.getBlockEntities().values());
            }
        }

        return blockEntities;
    }
}
