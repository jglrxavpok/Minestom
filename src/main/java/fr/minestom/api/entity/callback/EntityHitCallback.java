package fr.minestom.api.entity.callback;

import fr.minestom.api.entity.LivingEntity;

public interface EntityHitCallback {

    void process(LivingEntity hitEntity);

}
