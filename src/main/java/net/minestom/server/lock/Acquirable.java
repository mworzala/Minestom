package net.minestom.server.lock;

import net.minestom.server.thread.BatchThread;
import net.minestom.server.thread.batch.BatchSetupHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.Phaser;
import java.util.function.Consumer;

/**
 * Represents an element which can be acquired.
 * Used for synchronization purpose.
 * <p>
 * Implementations of this class are recommended to be immutable (or at least thread-safe).
 * The default one is {@link AcquirableImpl}.
 *
 * @param <T> the acquirable object type
 */
public interface Acquirable<T> {

    /**
     * Blocks the current thread until 'this' can be acquired,
     * and execute {@code consumer} as a callback with the acquired object.
     *
     * @param consumer the consumer of the acquired object
     */
    default void acquire(@NotNull Consumer<T> consumer) {
        final Thread currentThread = Thread.currentThread();
        Acquisition.AcquisitionData data = new Acquisition.AcquisitionData();

        final boolean sameThread = Acquisition.acquire(currentThread, getHandler().getBatchThread(), data);
        final T unwrap = unsafeUnwrap();
        if (sameThread) {
            consumer.accept(unwrap);
        } else {
            synchronized (unwrap) {
                consumer.accept(unwrap);
            }
            // Notify the end of the task if required
            Phaser phaser = data.getPhaser();
            if (phaser != null) {
                phaser.arriveAndDeregister();
            }
        }
    }

    /**
     * Signals the acquisition manager to acquire 'this' at the end of the thread tick.
     * <p>
     * Thread-safety is guaranteed but not the order.
     *
     * @param consumer the consumer of the acquired object
     */
    default void scheduledAcquire(@NotNull Consumer<T> consumer) {
        Acquisition.scheduledAcquireRequest(this, consumer);
    }

    @NotNull
    T unsafeUnwrap();

    @NotNull
    Handler getHandler();

    class Handler {

        private volatile BatchThread batchThread;

        @Nullable
        public BatchThread getBatchThread() {
            return batchThread;
        }

        /**
         * Specifies in which thread this element will be updated.
         * Currently defined before every tick for all game elements in {@link BatchSetupHandler#pushTask(Set, long)}.
         *
         * @param batchThread the thread where this element will be updated
         */
        public void refreshThread(@NotNull BatchThread batchThread) {
            this.batchThread = batchThread;
        }

        /**
         * Executed during this element tick to empty the current thread acquisition queue.
         */
        public void acquisitionTick() {
            Acquisition.processQueue(batchThread.getQueue());
        }
    }

}