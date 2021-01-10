package net.minestom.server.entity.damage;

import net.minestom.server.entity.Entity;
import net.minestom.server.lock.Acquirable;
import org.jetbrains.annotations.NotNull;

/**
 * Represents damage inflicted by an {@link Entity}.
 */
public class EntityDamage extends DamageType {

    private final Entity source;

    public EntityDamage(@NotNull Entity source) {
        super("entity_source");
        this.source = source;
    }

    /**
     * Gets the source of the damage.
     *
     * @return the source
     */
    @NotNull
    public Acquirable<Entity> getSource() {
        return source.getAcquiredElement();
    }
}