package fr.minestom.api.instance;

import fr.minestom.api.data.Data;
import fr.minestom.api.utils.Location;

public interface BlockManipulation {

    void setBlock(Location location, String blockId);

    void setBlock(Location location, String blockId, Data data);

}
