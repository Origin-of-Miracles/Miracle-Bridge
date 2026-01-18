package com.originofmiracles.miraclebridge.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.originofmiracles.miraclebridge.MiracleBridge;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Thread Scheduler Utility Class
 * 
 * Provides unified thread switching API to ensure code runs on the correct thread.
 * 
 * Thread Model:
 * - Render thread: Webview texture operations, GUI rendering
 * - Client main thread: Game logic (player, items, blocks)
 * - Server main thread: Server logic (entities, world)
 * - Async thread pool: Network requests, AI inference, file I/O
 * 
 * WARNING: Never execute blocking operations on main threads!
 */
public class ThreadScheduler {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * Async task thread pool
     */
    private static final ExecutorService ASYNC_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, MiracleBridge.MOD_ID + "-async-" + System.currentTimeMillis());
        t.setDaemon(true);
        return t;
    });
    
    /**
     * Delayed task scheduler
     */
    private static final ScheduledExecutorService SCHEDULED_EXECUTOR = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, MiracleBridge.MOD_ID + "-scheduled-" + System.currentTimeMillis());
        t.setDaemon(true);
        return t;
    });
    
    // ==================== Client Thread Operations ====================
    
    /**
     * Execute task on client main thread
     * 
     * @param task task to execute
     */
    @OnlyIn(Dist.CLIENT)
    public static void runOnClientThread(Runnable task) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isSameThread()) {
            task.run();
        } else {
            mc.execute(task);
        }
    }
    
    /**
     * Execute task on client main thread and return result
     * 
     * @param supplier task to execute
     * @return CompletableFuture containing the result
     */
    @OnlyIn(Dist.CLIENT)
    public static <T> CompletableFuture<T> supplyOnClientThread(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        
        runOnClientThread(() -> {
            try {
                T result = supplier.get();
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        
        return future;
    }
    
    /**
     * Check if currently on client main thread
     */
    @OnlyIn(Dist.CLIENT)
    public static boolean isOnClientThread() {
        return Minecraft.getInstance().isSameThread();
    }
    
    // ==================== Server Thread Operations ====================
    
    /**
     * Execute task on server main thread
     * 
     * @param server server instance
     * @param task task to execute
     */
    public static void runOnServerThread(MinecraftServer server, Runnable task) {
        if (server.isSameThread()) {
            task.run();
        } else {
            server.execute(task);
        }
    }
    
    /**
     * Execute task on server main thread and return result
     * 
     * @param server server instance
     * @param supplier task to execute
     * @return CompletableFuture containing the result
     */
    public static <T> CompletableFuture<T> supplyOnServerThread(MinecraftServer server, Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        
        runOnServerThread(server, () -> {
            try {
                T result = supplier.get();
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        
        return future;
    }
    
    /**
     * Check if currently on server main thread
     */
    public static boolean isOnServerThread(@Nullable MinecraftServer server) {
        return server != null && server.isSameThread();
    }
    
    // ==================== Async Operations ====================
    
    /**
     * Execute task asynchronously
     * 
     * @param task task to execute
     * @return CompletableFuture representing task completion
     */
    public static CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(task, ASYNC_EXECUTOR);
    }
    
    /**
     * Execute task asynchronously and return result
     * 
     * @param supplier task to execute
     * @return CompletableFuture containing the result
     */
    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, ASYNC_EXECUTOR);
    }
    
    /**
     * Execute task after delay
     * 
     * @param task task to execute
     * @param delay delay time
     * @param unit time unit
     * @return cancelable ScheduledFuture
     */
    public static ScheduledFuture<?> runLater(Runnable task, long delay, TimeUnit unit) {
        return SCHEDULED_EXECUTOR.schedule(task, delay, unit);
    }
    
    /**
     * Execute task on client main thread after delay
     * 
     * @param task task to execute
     * @param delayMs delay time (milliseconds)
     */
    @OnlyIn(Dist.CLIENT)
    public static ScheduledFuture<?> runOnClientThreadLater(Runnable task, long delayMs) {
        return runLater(() -> runOnClientThread(task), delayMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Execute task periodically
     * 
     * @param task task to execute
     * @param initialDelay initial delay
     * @param period execution period
     * @param unit time unit
     * @return cancelable ScheduledFuture
     */
    public static ScheduledFuture<?> runPeriodically(Runnable task, long initialDelay, long period, TimeUnit unit) {
        return SCHEDULED_EXECUTOR.scheduleAtFixedRate(task, initialDelay, period, unit);
    }
    
    /**
     * Execute task repeatedly until it returns false
     * 
     * @param task task to execute, returns true to continue, false to stop
     * @param periodTicks execution period (in ticks, 1 tick = 50ms)
     * @return cancelable ScheduledFuture
     */
    public static ScheduledFuture<?> runRepeating(Supplier<Boolean> task, int periodTicks) {
        long periodMs = periodTicks * 50L; // 1 tick = 50ms
        
        final java.util.concurrent.atomic.AtomicReference<ScheduledFuture<?>> futureRef = 
            new java.util.concurrent.atomic.AtomicReference<>();
        
        ScheduledFuture<?> future = SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            try {
                boolean shouldContinue = task.get();
                if (!shouldContinue) {
                    ScheduledFuture<?> f = futureRef.get();
                    if (f != null) {
                        f.cancel(false);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error in repeating task", e);
                ScheduledFuture<?> f = futureRef.get();
                if (f != null) {
                    f.cancel(false);
                }
            }
        }, periodMs, periodMs, TimeUnit.MILLISECONDS); // 使用 initialDelay = periodMs 避免立即执行
        
        futureRef.set(future);
        return future;
    }
    
    /**
     * Execute task repeatedly on server main thread
     * 
     * @param server server instance
     * @param task task to execute, returns true to continue, false to stop
     * @param periodTicks execution period (in ticks)
     * @return cancelable ScheduledFuture
     */
    public static ScheduledFuture<?> runRepeatingOnServer(MinecraftServer server, Supplier<Boolean> task, int periodTicks) {
        return runRepeating(() -> {
            CompletableFuture<Boolean> result = supplyOnServerThread(server, task);
            try {
                return result.get(1, TimeUnit.SECONDS);
            } catch (Exception e) {
                LOGGER.error("Error in server repeating task", e);
                return false;
            }
        }, periodTicks);
    }
    
    // ==================== Thread-Safe Execution ====================
    
    /**
     * Ensure task runs on client main thread (runs directly if already on main thread)
     * This is a blocking method that waits for task completion
     * 
     * @param task task to execute
     * @param timeoutMs timeout (milliseconds)
     * @throws TimeoutException if timeout
     * @throws ExecutionException if task execution fails
     */
    @OnlyIn(Dist.CLIENT)
    public static void ensureOnClientThread(Runnable task, long timeoutMs) 
            throws TimeoutException, ExecutionException, InterruptedException {
        if (isOnClientThread()) {
            task.run();
        } else {
            CompletableFuture<Void> future = new CompletableFuture<>();
            runOnClientThread(() -> {
                try {
                    task.run();
                    future.complete(null);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            future.get(timeoutMs, TimeUnit.MILLISECONDS);
        }
    }
    
    // ==================== Lifecycle Management ====================
    
    /**
     * Shutdown all thread pools
     * Should be called when mod unloads
     */
    public static void shutdown() {
        LOGGER.info("Shutting down thread scheduler...");
        
        ASYNC_EXECUTOR.shutdown();
        SCHEDULED_EXECUTOR.shutdown();
        
        try {
            if (!ASYNC_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                ASYNC_EXECUTOR.shutdownNow();
            }
            if (!SCHEDULED_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                SCHEDULED_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            ASYNC_EXECUTOR.shutdownNow();
            SCHEDULED_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        LOGGER.info("Thread scheduler shut down");
    }
    
    private ThreadScheduler() {}
}
