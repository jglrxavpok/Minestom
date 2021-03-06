package net.minestom.server.instance;

import io.netty.buffer.ByteBuf;
import net.minestom.server.data.Data;
import net.minestom.server.entity.Player;
import net.minestom.server.event.PlayerBlockBreakEvent;
import net.minestom.server.instance.batch.BlockBatch;
import net.minestom.server.instance.batch.ChunkBatch;
import net.minestom.server.instance.block.CustomBlock;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import net.minestom.server.network.PacketWriterUtils;
import net.minestom.server.network.packet.server.play.BlockChangePacket;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.network.packet.server.play.UnloadChunkPacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.particle.ParticleCreator;
import net.minestom.server.utils.BlockPosition;
import net.minestom.server.utils.ChunkUtils;
import net.minestom.server.utils.Position;
import net.minestom.server.utils.SerializerUtils;
import net.minestom.server.world.Dimension;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * InstanceContainer is an instance that contains chunks in contrary to SharedInstance.
 */
public class InstanceContainer extends Instance {

    private File folder;

    private List<SharedInstance> sharedInstances = new CopyOnWriteArrayList<>();

    private ChunkGenerator chunkGenerator;
    private Map<Long, Chunk> chunks = new ConcurrentHashMap<>();

    private boolean autoChunkLoad;

    protected InstanceContainer(UUID uniqueId, Dimension dimension, File folder) {
        super(uniqueId, dimension);
        this.folder = folder;
    }

    @Override
    public void setBlock(int x, int y, int z, short blockId, Data data) {
        setBlock(x, y, z, blockId, (short) 0, data);
    }

    @Override
    public void setCustomBlock(int x, int y, int z, short customBlockId, Data data) {
        short blockId = BLOCK_MANAGER.getCustomBlock(customBlockId).getBlockId();
        setBlock(x, y, z, blockId, customBlockId, data);
    }

    private synchronized void setBlock(int x, int y, int z, short blockId, short customBlockId, Data data) {
        Chunk chunk = getChunkAt(x, z);
        synchronized (chunk) {

            boolean isCustomBlock = customBlockId != 0;

            int index = SerializerUtils.coordToChunkIndex(x, y, z);

            BlockPosition blockPosition = new BlockPosition(x, y, z);

            // Call the destroy listener if previous block was a custom block
            callBlockDestroy(chunk, index, blockPosition);

            // Change id based on neighbors
            blockId = executeBlockPlacementRule(blockId, blockPosition);

            // Set the block
            if (isCustomBlock) {
                chunk.UNSAFE_setCustomBlock(x, y, z, customBlockId, data);
            } else {
                chunk.UNSAFE_setBlock(x, y, z, blockId, data);
            }

            // Refresh neighbors since a new block has been placed
            executeNeighboursBlockPlacementRule(blockPosition);

            // Call the place listener for custom block
            if (isCustomBlock)
                callBlockPlace(chunk, index, blockPosition);

            // Refresh player chunk block
            sendBlockChange(chunk, blockPosition, blockId);
        }
    }

    @Override
    public void refreshBlockId(int x, int y, int z, short blockId) {
        Chunk chunk = getChunkAt(x, z);
        synchronized (chunk) {
            chunk.refreshBlockValue(x, y, z, blockId);

            BlockPosition blockPosition = new BlockPosition(x, y, z);
            sendBlockChange(chunk, blockPosition, blockId);
        }
    }

    private void callBlockDestroy(Chunk chunk, int index, BlockPosition blockPosition) {
        CustomBlock previousBlock = chunk.getCustomBlock(index);
        if (previousBlock != null) {
            Data previousData = chunk.getData(index);
            previousBlock.onDestroy(this, blockPosition, previousData);
        }
    }

    private void callBlockPlace(Chunk chunk, int index, BlockPosition blockPosition) {
        CustomBlock actualBlock = chunk.getCustomBlock(index);
        Data previousData = chunk.getData(index);
        actualBlock.onPlace(this, blockPosition, previousData);
    }

    private short executeBlockPlacementRule(short blockId, BlockPosition blockPosition) {

        BlockPlacementRule blockPlacementRule = BLOCK_MANAGER.getBlockPlacementRule(blockId);
        if (blockPlacementRule != null) {
            return blockPlacementRule.blockRefresh(this, blockPosition);
        }
        return blockId;
    }

    private void executeNeighboursBlockPlacementRule(BlockPosition blockPosition) {
        for (int offsetX = -1; offsetX < 2; offsetX++) {
            for (int offsetY = -1; offsetY < 2; offsetY++) {
                for (int offsetZ = -1; offsetZ < 2; offsetZ++) {
                    if (offsetX == 0 && offsetY == 0 && offsetZ == 0)
                        continue;
                    int neighborX = blockPosition.getX() + offsetX;
                    int neighborY = blockPosition.getY() + offsetY;
                    int neighborZ = blockPosition.getZ() + offsetZ;
                    short neighborId = getBlockId(neighborX, neighborY, neighborZ);
                    BlockPlacementRule neighborBlockPlacementRule = BLOCK_MANAGER.getBlockPlacementRule(neighborId);
                    if (neighborBlockPlacementRule != null) {
                        short newNeighborId = neighborBlockPlacementRule.blockRefresh(this,
                                new BlockPosition(neighborX, neighborY, neighborZ));
                        if (neighborId != newNeighborId) {
                            refreshBlockId(neighborX, neighborY, neighborZ, newNeighborId);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void breakBlock(Player player, BlockPosition blockPosition) {
        Chunk chunk = getChunkAt(blockPosition);

        int x = blockPosition.getX();
        int y = blockPosition.getY();
        int z = blockPosition.getZ();

        short blockId = chunk.getBlockId(x, y, z);
        if (blockId == 0) {
            sendChunkSectionUpdate(chunk, ChunkUtils.getSectionAt(y), player);
            return;
        }

        PlayerBlockBreakEvent blockBreakEvent = new PlayerBlockBreakEvent(blockPosition);
        player.callEvent(PlayerBlockBreakEvent.class, blockBreakEvent);
        if (!blockBreakEvent.isCancelled()) {

            // Break or change the broken block based on event result
            short resultBlockId = blockBreakEvent.getResultBlock();
            boolean custom = blockBreakEvent.isResultCustomBlock();
            if (custom) {
                setCustomBlock(x, y, z, resultBlockId);
            } else {
                setBlock(x, y, z, resultBlockId);
            }

            ParticlePacket particlePacket = ParticleCreator.createParticlePacket(Particle.BLOCK, false,
                    x + 0.5f, y, z + 0.5f,
                    0.4f, 0.5f, 0.4f,
                    0.3f, 125, writer -> {
                        writer.writeVarInt(blockId);
                    });

            chunk.sendPacketToViewers(particlePacket);
        } else {
            sendChunkSectionUpdate(chunk, ChunkUtils.getSectionAt(blockPosition.getY()), player);
        }
    }

    @Override
    public void loadChunk(int chunkX, int chunkZ, Consumer<Chunk> callback) {
        Chunk chunk = getChunk(chunkX, chunkZ);
        if (chunk != null) {
            if (callback != null)
                callback.accept(chunk);
        } else {
            retrieveChunk(chunkX, chunkZ, callback);
        }
    }

    @Override
    public void loadOptionalChunk(int chunkX, int chunkZ, Consumer<Chunk> callback) {
        Chunk chunk = getChunk(chunkX, chunkZ);
        if (chunk != null) {
            if (callback != null)
                callback.accept(chunk);
        } else {
            if (hasEnabledAutoChunkLoad()) {
                retrieveChunk(chunkX, chunkZ, callback);
            } else {
                callback.accept(null);
            }
        }
    }

    @Override
    public void unloadChunk(int chunkX, int chunkZ) {
        Chunk chunk = getChunk(chunkX, chunkZ);
        long index = ChunkUtils.getChunkIndex(chunkX, chunkZ);

        UnloadChunkPacket unloadChunkPacket = new UnloadChunkPacket();
        unloadChunkPacket.chunkX = chunkX;
        unloadChunkPacket.chunkZ = chunkZ;
        chunk.sendPacketToViewers(unloadChunkPacket);

        for (Player viewer : chunk.getViewers()) {
            chunk.removeViewer(viewer);
        }

        this.chunks.remove(index);
    }

    @Override
    public Chunk getChunk(int chunkX, int chunkZ) {
        return chunks.get(ChunkUtils.getChunkIndex(chunkX, chunkZ));
    }

    @Override
    public void saveChunkToFolder(Chunk chunk, Runnable callback) {
        CHUNK_LOADER_IO.saveChunk(chunk, getFolder(), callback);
    }

    @Override
    public void saveChunksToFolder(Runnable callback) {
        if (folder == null)
            throw new UnsupportedOperationException("You cannot save an instance without setting a folder.");

        Iterator<Chunk> chunks = getChunks().iterator();
        while (chunks.hasNext()) {
            Chunk chunk = chunks.next();
            boolean isLast = !chunks.hasNext();
            saveChunkToFolder(chunk, isLast ? callback : null);
        }
    }

    @Override
    public BlockBatch createBlockBatch() {
        return new BlockBatch(this);
    }

    @Override
    public ChunkBatch createChunkBatch(Chunk chunk) {
        return new ChunkBatch(this, chunk);
    }

    @Override
    public void sendChunkUpdate(Player player, Chunk chunk) {
        player.getPlayerConnection().sendPacket(chunk.getFullDataPacket());
    }

    @Override
    protected void retrieveChunk(int chunkX, int chunkZ, Consumer<Chunk> callback) {
        if (folder != null) {
            // Load from file if possible
            CHUNK_LOADER_IO.loadChunk(chunkX, chunkZ, this, chunk -> {
                cacheChunk(chunk);
                if (callback != null)
                    callback.accept(chunk);
            });
        } else {
            // Folder isn't defined, create new chunk
            createChunk(chunkX, chunkZ, callback);
        }
    }

    @Override
    public void createChunk(int chunkX, int chunkZ, Consumer<Chunk> callback) {
        Biome biome = chunkGenerator != null ? chunkGenerator.getBiome(chunkX, chunkZ) : Biome.VOID;
        Chunk chunk = new Chunk(biome, chunkX, chunkZ);
        cacheChunk(chunk);
        if (chunkGenerator != null) {
            ChunkBatch chunkBatch = createChunkBatch(chunk);
            chunkBatch.flushChunkGenerator(chunkGenerator, callback);
        }
    }

    public void sendChunkUpdate(Chunk chunk) {
        Set<Player> chunkViewers = chunk.getViewers();
        if (!chunkViewers.isEmpty()) {
            sendChunkUpdate(chunkViewers, chunk);
        }
    }

    @Override
    public void sendChunks(Player player) {
        for (Chunk chunk : getChunks()) {
            sendChunk(player, chunk);
        }
    }

    @Override
    public void sendChunk(Player player, Chunk chunk) {
        ByteBuf data = chunk.getFullDataPacket();
        if (data == null || !chunk.packetUpdated) {
            PacketWriterUtils.writeCallbackPacket(chunk.getFreshFullDataPacket(), packet -> {
                chunk.setFullDataPacket(packet);
                sendChunkUpdate(player, chunk);
            });
        } else {
            sendChunkUpdate(player, chunk);
        }
    }

    @Override
    public void enableAutoChunkLoad(boolean enable) {
        this.autoChunkLoad = enable;
    }

    @Override
    public boolean hasEnabledAutoChunkLoad() {
        return autoChunkLoad;
    }

    @Override
    public boolean isInVoid(Position position) {
        // TODO: customizable
        return position.getY() < -64;
    }

    protected void addSharedInstance(SharedInstance sharedInstance) {
        this.sharedInstances.add(sharedInstance);
    }

    private void cacheChunk(Chunk chunk) {
        this.chunks.put(ChunkUtils.getChunkIndex(chunk.getChunkX(), chunk.getChunkZ()), chunk);
    }

    public void setChunkGenerator(ChunkGenerator chunkGenerator) {
        this.chunkGenerator = chunkGenerator;
    }

    public Collection<Chunk> getChunks() {
        return Collections.unmodifiableCollection(chunks.values());
    }

    public File getFolder() {
        return folder;
    }

    public void setFolder(File folder) {
        this.folder = folder;
    }

    private void sendBlockChange(Chunk chunk, BlockPosition blockPosition, short blockId) {
        BlockChangePacket blockChangePacket = new BlockChangePacket();
        blockChangePacket.blockPosition = blockPosition;
        blockChangePacket.blockId = blockId;
        chunk.sendPacketToViewers(blockChangePacket);
    }

}