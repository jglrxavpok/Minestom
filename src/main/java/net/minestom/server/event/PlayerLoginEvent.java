package net.minestom.server.event;

import net.minestom.server.instance.Instance;

public class PlayerLoginEvent extends Event {

    private Instance spawningInstance;

    public Instance getSpawningInstance() {
        return spawningInstance;
    }

    public void setSpawningInstance(Instance instance) {
        this.spawningInstance = instance;
    }

}
