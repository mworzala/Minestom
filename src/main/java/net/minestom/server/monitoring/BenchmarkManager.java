package net.minestom.server.monitoring;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minestom.server.MinecraftServer;
import net.minestom.server.chat.ChatColor;
import net.minestom.server.utils.MathUtils;
import net.minestom.server.utils.time.UpdateOption;
import net.minestom.server.utils.validate.Check;
import org.jetbrains.annotations.NotNull;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static net.minestom.server.MinecraftServer.*;

/**
 * Small monitoring tools that can be used to check the current memory usage and Minestom threads CPU usage.
 * <p>
 * Needs to be enabled with {@link #enable(UpdateOption)}. Memory can then be accessed with {@link #getUsedMemory()}
 * and the CPUs usage with {@link #getResultMap()} or {@link #getCpuMonitoringMessage()}.
 * <p>
 * Be aware that this is not the most accurate method, you should use a proper java profiler depending on your needs.
 */
public final class BenchmarkManager {

    public static final ThreadMXBean THREAD_MX_BEAN = ManagementFactory.getThreadMXBean();
    private static final List<String> THREADS = new ArrayList<>();

    static {
        THREAD_MX_BEAN.setThreadContentionMonitoringEnabled(true);
        THREAD_MX_BEAN.setThreadCpuTimeEnabled(true);

        THREADS.add(THREAD_NAME_BLOCK_BATCH);
        THREADS.add(THREAD_NAME_SCHEDULER);
        THREADS.add(THREAD_NAME_TICK);
        THREADS.add(THREAD_NAME_UPDATE);
    }

    private final Long2LongMap lastCpuTimeMap = new Long2LongOpenHashMap();
    private final Long2LongMap lastUserTimeMap = new Long2LongOpenHashMap();
    private final Long2LongMap lastWaitedMap = new Long2LongOpenHashMap();
    private final Long2LongMap lastBlockedMap = new Long2LongOpenHashMap();

    private final Map<String, ThreadResult> resultMap = new ConcurrentHashMap<>();

    private boolean enabled = false;
    private volatile boolean stop = false;

    private long time;

    public void enable(@NotNull UpdateOption updateOption) {
        Check.stateCondition(enabled, "A benchmark is already running, please disable it first.");

        time = updateOption.getTimeUnit().toMilliseconds(updateOption.getValue());

        final Thread thread = new Thread(null, () -> {

            while (!stop) {
                refreshData();

                try {
                    Thread.sleep(time);
                } catch (InterruptedException e) {
                    MinecraftServer.getExceptionManager().handleException(e);
                }
            }

            stop = false;

        }, MinecraftServer.THREAD_NAME_BENCHMARK, 0L);

        thread.start();

        this.enabled = true;
    }

    public void disable() {
        this.stop = true;
        this.enabled = false;
    }

    public void addThreadMonitor(@NotNull String threadName) {
        THREADS.add(threadName);
    }

    /**
     * Gets the heap memory used by the server in bytes.
     *
     * @return the memory used by the server
     */
    public long getUsedMemory() {
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }

    @NotNull
    public Map<String, ThreadResult> getResultMap() {
        return Collections.unmodifiableMap(resultMap);
    }

    @NotNull
    public String getCpuMonitoringMessage() {
        Check.stateCondition(!enabled, "CPU monitoring is only possible when the benchmark manager is enabled.");
        StringBuilder benchmarkMessage = new StringBuilder();
        for (Map.Entry<String, ThreadResult> resultEntry : resultMap.entrySet()) {
            final String name = resultEntry.getKey();
            final ThreadResult result = resultEntry.getValue();

            benchmarkMessage.append(ChatColor.GRAY).append(name);
            benchmarkMessage.append(": ");
            benchmarkMessage.append(ChatColor.YELLOW.toString()).append(MathUtils.round(result.getCpuPercentage(), 2)).append("% CPU ");
            benchmarkMessage.append(ChatColor.RED.toString()).append(MathUtils.round(result.getUserPercentage(), 2)).append("% USER ");
            benchmarkMessage.append(ChatColor.PINK.toString()).append(MathUtils.round(result.getBlockedPercentage(), 2)).append("% BLOCKED ");
            benchmarkMessage.append(ChatColor.BRIGHT_GREEN.toString()).append(MathUtils.round(result.getWaitedPercentage(), 2)).append("% WAITED ");
            benchmarkMessage.append("\n");
        }
        return benchmarkMessage.toString();
    }

    private void refreshData() {
        ThreadInfo[] threadInfo = THREAD_MX_BEAN.getThreadInfo(THREAD_MX_BEAN.getAllThreadIds());
        for (ThreadInfo threadInfo2 : threadInfo) {
            final String name = threadInfo2.getThreadName();
            boolean shouldBenchmark = false;
            for (String thread : THREADS) {
                if (name.startsWith(thread)) {
                    shouldBenchmark = true;
                    break;
                }
            }
            if (!shouldBenchmark)
                continue;

            final long id = threadInfo2.getThreadId();

            final long lastCpuTime = lastCpuTimeMap.getOrDefault(id, 0L);
            final long lastUserTime = lastUserTimeMap.getOrDefault(id, 0L);
            final long lastWaitedTime = lastWaitedMap.getOrDefault(id, 0L);
            final long lastBlockedTime = lastBlockedMap.getOrDefault(id, 0L);

            final long blockedTime = threadInfo2.getBlockedTime();
            final long waitedTime = threadInfo2.getWaitedTime();
            final long cpuTime = THREAD_MX_BEAN.getThreadCpuTime(id);
            final long userTime = THREAD_MX_BEAN.getThreadUserTime(id);

            lastCpuTimeMap.put(id, cpuTime);
            lastUserTimeMap.put(id, userTime);
            lastWaitedMap.put(id, waitedTime);
            lastBlockedMap.put(id, blockedTime);

            final double totalCpuTime = (double) (cpuTime - lastCpuTime) / 1000000D;
            final double totalUserTime = (double) (userTime - lastUserTime) / 1000000D;
            final long totalBlocked = blockedTime - lastBlockedTime;
            final long totalWaited = waitedTime - lastWaitedTime;

            final double cpuPercentage = totalCpuTime / (double) time * 100L;
            final double userPercentage = totalUserTime / (double) time * 100L;
            final double waitedPercentage = totalWaited / (double) time * 100L;
            final double blockedPercentage = totalBlocked / (double) time * 100L;

            ThreadResult threadResult = new ThreadResult(cpuPercentage, userPercentage, waitedPercentage, blockedPercentage);
            resultMap.put(name, threadResult);
        }
    }
}
