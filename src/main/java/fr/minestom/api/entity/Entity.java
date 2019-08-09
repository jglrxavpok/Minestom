package fr.minestom.api.entity;

import fr.minestom.api.data.DataContainer;
import fr.minestom.api.instance.Instance;
import fr.minestom.api.utils.Location;

public interface Entity extends DataContainer {

    Instance getInstance();

    void setInstance(Instance instance);

    Location getLocation();

    void setLocation(Location location);

}
