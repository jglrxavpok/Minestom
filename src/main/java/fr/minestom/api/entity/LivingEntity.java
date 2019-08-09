package fr.minestom.api.entity;

import fr.minestom.api.entity.callback.EntityDamageCallback;
import fr.minestom.api.entity.callback.EntityDeathCallback;
import fr.minestom.api.entity.callback.EntityHitCallback;
import fr.minestom.api.instance.Instance;

public interface LivingEntity extends Entity {

    Instance getInstance();

    void setInstance(Instance instance);

    // Callbacks
    void onDeathCallback(EntityDeathCallback deathCallback);

    void onHitCallback(EntityHitCallback hitCallback);

    void onDamageCallback(EntityDamageCallback damageCallback);

}
