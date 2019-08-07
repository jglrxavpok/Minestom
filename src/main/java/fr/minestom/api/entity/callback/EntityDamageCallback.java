package fr.minestom.api.entity.callback;

import fr.minestom.api.entity.LivingEntity;

public interface EntityDamageCallback {

    void process(LivingEntity damager);

}
