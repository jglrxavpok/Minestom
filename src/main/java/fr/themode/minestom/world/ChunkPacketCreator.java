package fr.themode.minestom.world;

import fr.adamaq01.ozao.net.Buffer;
import fr.adamaq01.ozao.net.packet.Packet;
import fr.themode.minestom.utils.Utils;

public class ChunkPacketCreator {

    public static Packet create(int chunkX, int chunkZ, CustomChunk customChunk, int start, int end) {
        Packet packet = Packet.create();
        packet.put("id", 0x21);
        Buffer payload = packet.getPayload();

        payload.putInt(chunkX);
        payload.putInt(chunkZ);
        payload.putBoolean(true); // Send biome data (loading chunk, not modifying it)
        int mask = 0;
        Buffer blocks = Buffer.create();
        for (int i = 0; i < 16; i++) {
            mask |= 1 << i;
            // TODO Write section blocks data
            CustomBlock[] section = getSection(customChunk, i);
            Utils.writeBlocks(blocks, section);
        }
        // Biome data
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                blocks.putInt(customChunk.getBiome().getId());
            }
        }
        Utils.writeVarInt(payload, mask);
        payload.putBytes((byte) 0); // Heightmaps
        Utils.writeVarInt(payload, blocks.length());
        payload.putBuffer(blocks);
        Utils.writeVarInt(payload, 0);

        return packet;
    }

    public static CustomBlock[] getSection(CustomChunk customChunk, int section) {
        CustomBlock[] blocks = new CustomBlock[16 * 16 * 16];
        for (int y = 16 * section; y < 16 * (section + 1); y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int index = ((((y - (16 * section)) * 16) + x) * 16) + z;
                    blocks[index] = customChunk.getBlock(x, y, z);
                }
            }
        }
        return blocks;
    }
}
