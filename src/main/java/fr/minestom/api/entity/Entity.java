package fr.minestom.api.entity;

import fr.minestom.api.data.DataContainer;
import fr.minestom.api.utils.Location;

public interface Entity extends DataContainer {

    Location getLocation();

    void setLocation(Location location);

}
