package fr.minestom.api.entity;

import fr.minestom.api.entity.callback.EntityDamageCallback;
import fr.minestom.api.entity.callback.EntityDeathCallback;
import fr.minestom.api.entity.callback.EntityHitCallback;

public interface LivingEntity extends Entity {

    // Callbacks
    void onDeathCallback(EntityDeathCallback deathCallback);

    void onHitCallback(EntityHitCallback hitCallback);

    void onDamageCallback(EntityDamageCallback damageCallback);

}
