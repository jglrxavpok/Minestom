package fr.minestom.api;

import fr.minestom.api.entity.Player;
import fr.minestom.api.instance.InstanceManager;
import fr.minestom.api.instance.block.BlockManager;

import java.util.List;

public class Minestom {

    private static InstanceManager instanceManager;
    private static BlockManager blockManager;

    public static InstanceManager getInstanceManager() {
        return instanceManager;
    }

    protected static void setInstanceManager(InstanceManager instanceManager) {
        Minestom.instanceManager = instanceManager;
    }

    public static BlockManager getBlockManager() {
        return blockManager;
    }

    protected static void setBlockManager(BlockManager blockManager) {
        Minestom.blockManager = blockManager;
    }

    public static List<Player> getOnlinePlayers() {
        return null;
    }
}
