package net.minestom.server.instance.batch;

import net.minestom.server.data.Data;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.ChunkGenerator;
import net.minestom.server.instance.InstanceContainer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Use chunk coordinate (0-16) instead of world's
 */
public class ChunkBatch implements InstanceBatch {

    private InstanceContainer instance;
    private Chunk chunk;

    // Give it the max capacity by default (avoid resizing)
    private List<BlockData> dataList =
            Collections.synchronizedList(new ArrayList<>(
                    Chunk.CHUNK_SIZE_X * Chunk.CHUNK_SIZE_Y * Chunk.CHUNK_SIZE_Z));

    public ChunkBatch(InstanceContainer instance, Chunk chunk) {
        this.instance = instance;
        this.chunk = chunk;
    }

    @Override
    public void setBlock(int x, int y, int z, short blockId, Data data) {
        addBlockData((byte) x, y, (byte) z, false, blockId, data);
    }

    @Override
    public void setCustomBlock(int x, int y, int z, short blockId, Data data) {
        addBlockData((byte) x, y, (byte) z, true, blockId, data);
    }

    private void addBlockData(byte x, int y, byte z, boolean customBlock, short blockId, Data data) {
        BlockData blockData = new BlockData();
        blockData.x = x;
        blockData.y = y;
        blockData.z = z;
        blockData.isCustomBlock = customBlock;
        blockData.blockId = blockId;
        blockData.data = data;

        this.dataList.add(blockData);
    }

    public void flushChunkGenerator(ChunkGenerator chunkGenerator, Consumer<Chunk> callback) {
        batchesPool.execute(() -> {
            chunkGenerator.generateChunkData(this, chunk.getChunkX(), chunk.getChunkZ());
            singleThreadFlush(callback);
        });
    }

    public void flush(Consumer<Chunk> callback) {
        batchesPool.execute(() -> {
            singleThreadFlush(callback);
        });
    }

    private void singleThreadFlush(Consumer<Chunk> callback) {
        synchronized (chunk) {
            for (BlockData data : dataList) {
                data.apply(chunk);
            }

            chunk.refreshDataPacket(() -> {
                instance.sendChunkUpdate(chunk);
            });

            if (callback != null)
                callback.accept(chunk);
        }
    }

    private class BlockData {

        private int x, y, z;
        private boolean isCustomBlock;
        private short blockId;
        private Data data;

        public void apply(Chunk chunk) {
            if (!isCustomBlock) {
                chunk.UNSAFE_setBlock(x, y, z, blockId, data);
            } else {
                chunk.UNSAFE_setCustomBlock(x, y, z, blockId, data);
            }
        }

    }

}
