package net.minestom.server.thread.batch;

import net.minestom.server.entity.Entity;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.lock.AcquirableElement;
import net.minestom.server.thread.BatchThread;
import net.minestom.server.utils.callback.validator.EntityValidator;
import net.minestom.server.utils.validate.Check;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Set;

public class BatchSetupHandler implements BatchHandler {

    private static final int INSTANCE_COST = 5;
    private static final int CHUNK_COST = 5;
    private static final int ENTITY_COST = 5;

    private final ArrayList<AcquirableElement<?>> elements = new ArrayList<>();
    private int cost;

    @Override
    public void updateInstance(@NotNull Instance instance, long time) {
        this.elements.add(instance.getAcquiredElement());
        this.cost += INSTANCE_COST;
    }

    @Override
    public void updateChunk(@NotNull Instance instance, @NotNull Chunk chunk, long time) {
        this.elements.add(chunk.getAcquiredElement());
        this.cost += CHUNK_COST;
    }

    @Override
    public void conditionalEntityUpdate(@NotNull Instance instance, @NotNull Chunk chunk, long time, @Nullable EntityValidator condition) {
        final Set<Entity> entities = instance.getChunkEntities(chunk);

        for (Entity entity : entities) {
            if (shouldTick(entity, condition)) {
                this.elements.add(entity.getAcquiredElement());
                this.cost += ENTITY_COST;
            }
        }
    }

    public void pushTask(@NotNull Set<BatchThread> threads, long time) {
        BatchThread fitThread = null;
        int minCost = Integer.MAX_VALUE;

        for (BatchThread thread : threads) {
            final boolean switchThread = fitThread == null || thread.getCost() < minCost;
            if (switchThread) {
                fitThread = thread;
                minCost = thread.getCost();
            }
        }

        Check.notNull(fitThread, "The task thread returned null, something went terribly wrong.");

        // The thread has been decided, all elements need to be have its identifier
        {
            for (AcquirableElement<?> element : elements) {
                element.getHandler().refreshThread(fitThread);
            }
        }

        final Runnable runnable = createRunnable(time);

        fitThread.addRunnable(runnable, cost);
    }

    @NotNull
    private Runnable createRunnable(long time) {
        return () -> {
            for (AcquirableElement<?> element : elements) {
                final Object unwrapElement = element.unsafeUnwrap();

                if (unwrapElement instanceof Instance) {
                    ((Instance) unwrapElement).tick(time);
                    // FIXME: shared instance
                } else if (unwrapElement instanceof Chunk) {
                    // FIXME: instance null
                    ((Chunk) unwrapElement).tick(time, null);
                } else if (unwrapElement instanceof Entity) {
                    ((Entity) unwrapElement).tick(time);
                }
            }
        };
    }

}
