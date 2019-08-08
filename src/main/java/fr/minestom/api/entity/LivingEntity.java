package fr.minestom.api.entity;

import fr.minestom.api.data.DataContainer;
import fr.minestom.api.entity.callback.EntityDamageCallback;
import fr.minestom.api.entity.callback.EntityDeathCallback;
import fr.minestom.api.entity.callback.EntityHitCallback;
import fr.minestom.api.instance.Instance;
import fr.minestom.api.utils.Location;

public interface LivingEntity extends DataContainer {

    Instance getInstance();

    void setInstance(Instance instance);

    Location getLocation();

    void setLocation(Location location);

    // Callbacks
    void onDeathCallback(EntityDeathCallback deathCallback);

    void onHitCallback(EntityHitCallback hitCallback);

    void onDamageCallback(EntityDamageCallback damageCallback);

}
