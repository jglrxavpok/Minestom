package net.minestom.server.entity.damage;

import club.thectm.minecraft.text.TextBuilder;
import club.thectm.minecraft.text.TextObject;
import net.minestom.server.data.Data;
import net.minestom.server.data.DataContainer;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;

/**
 * Represents a type of damage
 */
public class DamageType implements DataContainer  {

    public static final DamageType VOID = new DamageType("void");
    public static final DamageType GRAVITY = new DamageType("gravity");
    private final String identifier;
    private Data data;

    public DamageType(String identifier) {
        this.data = new Data();
        this.identifier = identifier;
    }

    public String getIdentifier() {
        return identifier;
    }

    public TextObject buildChatMessage(Player killed) {
        return TextBuilder.of(killed.getUsername()+" was killed by damage of type "+identifier).build();
    }

    public TextObject buildDeathMessage() {
        return TextBuilder.of("Killed by damage of type "+identifier).build();
    }

    public static DamageType fromPlayer(Player player) {
        return new EntityDamage(player);
    }

    public static DamageType fromProjectile(Entity shooter, Entity projectile) {
        return new EntityProjectileDamage(shooter,  projectile);
    }

    @Override
    public Data getData() {
        return data;
    }

    @Override
    public void setData(Data data) {
        this.data = data;
    }
}
