package fr.minestom.api;

import fr.minestom.api.instance.InstanceManager;
import fr.minestom.api.instance.block.BlockManager;

public class MinestomServer {

    private int port;

    public MinestomServer(int port) {
        this.port = port;

        Minestom.setInstanceManager(new InstanceManager());
        Minestom.setBlockManager(new BlockManager());
    }

    public void start(int port) {

    }

    public int getPort() {
        return port;
    }
}
