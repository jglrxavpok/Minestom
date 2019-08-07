package fr.minestom.api.entity.callback;

import fr.minestom.api.entity.LivingEntity;

public interface EntityDeathCallback {

    void process(LivingEntity lastDamager);

}
