package fr.minestom.api.instance;

import fr.minestom.api.data.Data;
import fr.minestom.api.utils.Location;

public interface Instance extends BlockManipulation {

    Instance copy();

    void save(String path);

    Data getBlockData(Location location);

    BlockBatch createBlockBatch();


}
