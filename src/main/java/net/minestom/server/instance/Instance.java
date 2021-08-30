package net.minestom.server.instance;

import net.minestom.server.Tickable;
import net.minestom.server.adventure.audience.PacketGroupingAudience;
import net.minestom.server.coordinate.Point;
import net.minestom.server.data.Data;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityCreature;
import net.minestom.server.entity.ExperienceOrb;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.pathfinding.PFInstanceSpace;
import net.minestom.server.instance.block.BlockGetter;
import net.minestom.server.instance.block.BlockHandler;
import net.minestom.server.instance.block.BlockSetter;
import net.minestom.server.network.packet.server.play.BlockActionPacket;
import net.minestom.server.tag.TagHandler;
import net.minestom.server.thread.ThreadProvider;
import net.minestom.server.utils.chunk.ChunkUtils;
import net.minestom.server.utils.validate.Check;
import net.minestom.server.world.DimensionType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface Instance extends BlockGetter, BlockSetter, Tickable, TagHandler, PacketGroupingAudience {

    /**
     * Schedules a task to be run during the next instance tick.
     * It ensures that the task will be executed in the same thread as the instance
     * and its chunks/entities (depending of the {@link ThreadProvider}).
     *
     * @param callback the task to execute during the next instance tick
     */
    void scheduleNextTick(@NotNull Consumer<Instance> callback);

    @ApiStatus.Internal
    boolean placeBlock(@NotNull BlockHandler.Placement placement);

    /**
     * Does call {@link net.minestom.server.event.player.PlayerBlockBreakEvent}
     * and send particle packets
     *
     * @param player        the {@link Player} who break the block
     * @param blockPosition the position of the broken block
     * @return true if the block has been broken, false if it has been cancelled
     */
    @ApiStatus.Internal
    boolean breakBlock(@NotNull Player player, @NotNull Point blockPosition);

    /**
     * Forces the generation of a {@link Chunk}, even if no file and {@link ChunkGenerator} are defined.
     *
     * @param chunkX the chunk X
     * @param chunkZ the chunk Z
     * @return a {@link CompletableFuture} completed once the chunk has been loaded
     */
    @NotNull CompletableFuture<@NotNull Chunk> loadChunk(int chunkX, int chunkZ);

    /**
     * Loads the chunk at the given {@link Point} with a callback.
     *
     * @param point the chunk position
     */
    default @NotNull CompletableFuture<@NotNull Chunk> loadChunk(@NotNull Point point) {
        return loadChunk(ChunkUtils.getChunkCoordinate(point.x()),
                ChunkUtils.getChunkCoordinate(point.z()));
    }

    /**
     * Loads the chunk if the chunk is already loaded or if
     * {@link #hasEnabledAutoChunkLoad()} returns true.
     *
     * @param chunkX the chunk X
     * @param chunkZ the chunk Z
     * @return a {@link CompletableFuture} completed once the chunk has been processed, can be null if not loaded
     */
    @NotNull CompletableFuture<@Nullable Chunk> loadOptionalChunk(int chunkX, int chunkZ);

    /**
     * Loads a {@link Chunk} (if {@link #hasEnabledAutoChunkLoad()} returns true)
     * at the given {@link Point} with a callback.
     *
     * @param point the chunk position
     * @return a {@link CompletableFuture} completed once the chunk has been processed, null if not loaded
     */
    default @NotNull CompletableFuture<@Nullable Chunk> loadOptionalChunk(@NotNull Point point) {
        return loadOptionalChunk(ChunkUtils.getChunkCoordinate(point.x()),
                ChunkUtils.getChunkCoordinate(point.z()));
    }

    /**
     * Schedules the removal of a {@link Chunk}, this method does not promise when it will be done.
     * <p>
     * WARNING: during unloading, all entities other than {@link Player} will be removed.
     *
     * @param chunk the chunk to unload
     */
    void unloadChunk(@NotNull Chunk chunk);

    /**
     * Unloads the chunk at the given position.
     *
     * @param chunkX the chunk X
     * @param chunkZ the chunk Z
     */
    default void unloadChunk(int chunkX, int chunkZ) {
        final Chunk chunk = getChunk(chunkX, chunkZ);
        Check.notNull(chunk, "The chunk at {0}:{1} is already unloaded", chunkX, chunkZ);
        unloadChunk(chunk);
    }

    /**
     * Gets the loaded {@link Chunk} at a position.
     * <p>
     * WARNING: this should only return already-loaded chunk, use {@link #loadChunk(int, int)} or similar to load one instead.
     *
     * @param chunkX the chunk X
     * @param chunkZ the chunk Z
     * @return the chunk at the specified position, null if not loaded
     */
    @Nullable Chunk getChunk(int chunkX, int chunkZ);

    /**
     * Saves the current instance tags.
     * <p>
     * Warning: only the global instance data will be saved, not chunks.
     * You would need to call {@link #saveChunksToStorage()} too.
     *
     * @return the future called once the instance data has been saved
     */
    @ApiStatus.Experimental
    @NotNull CompletableFuture<Void> saveInstance();

    /**
     * Saves a {@link Chunk} to permanent storage.
     *
     * @param chunk the {@link Chunk} to save
     * @return future called when the chunk is done saving
     */
    @NotNull CompletableFuture<Void> saveChunkToStorage(@NotNull Chunk chunk);

    /**
     * Saves multiple chunks to permanent storage.
     *
     * @return future called when the chunks are done saving
     */
    @NotNull CompletableFuture<Void> saveChunksToStorage();

    /**
     * Gets the instance {@link ChunkGenerator}.
     *
     * @return the {@link ChunkGenerator} of the instance
     */
    @Nullable ChunkGenerator getChunkGenerator();

    /**
     * Changes the instance {@link ChunkGenerator}.
     *
     * @param chunkGenerator the new {@link ChunkGenerator} of the instance
     */
    void setChunkGenerator(@Nullable ChunkGenerator chunkGenerator);

    /**
     * Gets all the instance's loaded chunks.
     *
     * @return an unmodifiable containing all the instance chunks
     */
    @NotNull Collection<@NotNull Chunk> getChunks();

    /**
     * When set to true, chunks will load automatically when requested.
     * Otherwise using {@link #loadChunk(int, int)} will be required to even spawn a player
     *
     * @param enable enable the auto chunk load
     */
    void enableAutoChunkLoad(boolean enable);

    /**
     * Gets if the instance should auto load chunks.
     *
     * @return true if auto chunk load is enabled, false otherwise
     */
    boolean hasEnabledAutoChunkLoad();

    /**
     * Determines whether a position in the void. If true, entities should take damage and die.
     * <p>
     * Always returning false allow entities to survive in the void.
     *
     * @param point the point in the world
     * @return true if the point is inside the void
     */
    boolean isInVoid(@NotNull Point point);

    /**
     * Gets if the instance has been registered in {@link InstanceManager}.
     *
     * @return true if the instance has been registered
     */
    boolean isRegistered();

    /**
     * Changes the registered field.
     * <p>
     * WARNING: should only be used by {@link InstanceManager}.
     *
     * @param registered true to mark the instance as registered
     */
    void setRegistered(boolean registered);

    /**
     * Gets the instance {@link DimensionType}.
     *
     * @return the dimension of the instance
     */
    DimensionType getDimensionType();

    /**
     * Gets the age of this instance in tick.
     *
     * @return the age of this instance in tick
     */
    long getWorldAge();

    /**
     * Gets the current time in the instance (sun/moon).
     *
     * @return the time in the instance
     */
    long getTime();

    /**
     * Changes the current time in the instance, from 0 to 24000.
     * <p>
     * If the time is negative, the vanilla client will not move the sun.
     * <p>
     * 0 = sunrise
     * 6000 = noon
     * 12000 = sunset
     * 18000 = midnight
     * <p>
     * This method is unaffected by {@link #getTimeRate()}
     * <p>
     * It does send the new time to all players in the instance, unaffected by {@link #getTimeUpdate()}
     *
     * @param time the new time of the instance
     */
    void setTime(long time);

    /**
     * Gets the rate of the time passing, it is 1 by default
     *
     * @return the time rate of the instance
     */
    int getTimeRate();

    /**
     * Changes the time rate of the instance
     * <p>
     * 1 is the default value and can be set to 0 to be completely disabled (constant time)
     *
     * @param timeRate the new time rate of the instance
     * @throws IllegalStateException if {@code timeRate} is lower than 0
     */
    void setTimeRate(int timeRate);

    /**
     * Gets the rate at which the client is updated with the current instance time
     *
     * @return the client update rate for time related packet
     */
    @Nullable Duration getTimeUpdate();

    /**
     * Changes the rate at which the client is updated about the time
     * <p>
     * Setting it to null means that the client will never know about time change
     * (but will still change server-side)
     *
     * @param timeUpdate the new update rate concerning time
     */
    void setTimeUpdate(@Nullable Duration timeUpdate);

    /**
     * Gets the instance {@link WorldBorder};
     *
     * @return the {@link WorldBorder} linked to the instance
     */
    @NotNull WorldBorder getWorldBorder();

    /**
     * Gets the entities in the instance;
     *
     * @return an unmodifiable {@link Set} containing all the entities in the instance
     */
    @NotNull Set<@NotNull Entity> getEntities();

    /**
     * Gets the creatures in the instance;
     *
     * @return an unmodifiable {@link Set} containing all the creatures in the instance
     */
    @NotNull Set<@NotNull EntityCreature> getCreatures();

    /**
     * Gets the experience orbs in the instance.
     *
     * @return an unmodifiable {@link Set} containing all the experience orbs in the instance
     */
    @NotNull Set<@NotNull ExperienceOrb> getExperienceOrbs();

    /**
     * Gets the entities located in the chunk.
     *
     * @param chunk the chunk to get the entities from
     * @return an unmodifiable {@link Set} containing all the entities in a chunk,
     * if {@code chunk} is unloaded, return an empty {@link HashSet}
     */
    @NotNull Set<@NotNull Entity> getChunkEntities(Chunk chunk);

    /**
     * Gets nearby entities to the given position.
     *
     * @param point position to look at
     * @param range max range from the given point to collect entities at
     * @return entities that are not further than the specified distance from the transmitted position.
     */
    @NotNull Collection<Entity> getNearbyEntities(@NotNull Point point, double range);

    /**
     * Sends a {@link BlockActionPacket} for all the viewers of the specific position.
     *
     * @param blockPosition the block position
     * @param actionId      the action id, depends on the block
     * @param actionParam   the action parameter, depends on the block
     * @see <a href="https://wiki.vg/Protocol#Block_Action">BlockActionPacket</a> for the action id &amp; param
     */
    void sendBlockAction(@NotNull Point blockPosition, byte actionId, byte actionParam);

    /**
     * Gets the {@link Chunk} at the given block position, null if not loaded.
     *
     * @param x the X position
     * @param z the Z position
     * @return the chunk at the given position, null if not loaded
     */
    @Nullable Chunk getChunkAt(double x, double z);

    /**
     * Gets the {@link Chunk} at the given {@link Point}, null if not loaded.
     *
     * @param point the chunk position
     * @return the chunk at the given position, null if not loaded
     */
    @Nullable Chunk getChunkAt(@NotNull Point point);

    /**
     * Gets the instance unique id.
     *
     * @return the instance unique id
     */
    @NotNull UUID getUniqueId();

    /**
     * Used when called {@link Entity#setInstance(Instance)}, it is used to refresh viewable chunks
     * and add viewers if {@code entity} is a {@link Player}.
     * <p>
     * Warning: unsafe, you probably want to use {@link Entity#setInstance(Instance)} instead.
     *
     * @param entity the entity to add
     */
    @ApiStatus.Internal
    void UNSAFE_addEntity(@NotNull Entity entity);

    /**
     * Used when an {@link Entity} is removed from the instance, it removes all of his viewers.
     * <p>
     * Warning: unsafe, you probably want to set the entity to another instance.
     *
     * @param entity the entity to remove
     */
    @ApiStatus.Internal
    void UNSAFE_removeEntity(@NotNull Entity entity);

    /**
     * Changes an entity chunk.
     *
     * @param entity    the entity to change its chunk
     * @param lastChunk the last entity chunk
     * @param newChunk  the new entity chunk
     */
    @ApiStatus.Internal
    void UNSAFE_switchEntityChunk(@NotNull Entity entity, @NotNull Chunk lastChunk, @NotNull Chunk newChunk);

    /**
     * Creates an explosion at the given position with the given strength.
     * The algorithm used to compute damages is provided by {@link #getExplosionSupplier()}.
     *
     * @param centerX  the center X
     * @param centerY  the center Y
     * @param centerZ  the center Z
     * @param strength the strength of the explosion
     * @throws IllegalStateException If no {@link ExplosionSupplier} was supplied
     */
    default void explode(float centerX, float centerY, float centerZ, float strength) {
        explode(centerX, centerY, centerZ, strength, null);
    }

    /**
     * Creates an explosion at the given position with the given strength.
     * The algorithm used to compute damages is provided by {@link #getExplosionSupplier()}.
     *
     * @param centerX        center X of the explosion
     * @param centerY        center Y of the explosion
     * @param centerZ        center Z of the explosion
     * @param strength       the strength of the explosion
     * @param additionalData data to pass to the explosion supplier
     * @throws IllegalStateException If no {@link ExplosionSupplier} was supplied
     */
    void explode(float centerX, float centerY, float centerZ, float strength, @Nullable Data additionalData);

    /**
     * Gets the registered {@link ExplosionSupplier}, or null if none was provided.
     *
     * @return the instance explosion supplier, null if none was provided
     */
    @Nullable ExplosionSupplier getExplosionSupplier();

    /**
     * Registers the {@link ExplosionSupplier} to use in this instance.
     *
     * @param supplier the explosion supplier
     */
    void setExplosionSupplier(@Nullable ExplosionSupplier supplier);

    /**
     * Gets the instance space.
     * <p>
     * Used by the pathfinder for entities.
     *
     * @return the instance space
     */
    @ApiStatus.Internal
    @NotNull PFInstanceSpace getInstanceSpace();
}
