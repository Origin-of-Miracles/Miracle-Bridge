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
 * 线程调度工具类
 * 
 * 提供统一的线程切换 API，确保代码在正确的线程上执行。
 * 
 * 线程模型：
 * - 渲染线程：Webview 纹理操作、GUI 渲染
 * - 客户端主线程：游戏逻辑（玩家、物品、方块）
 * - 服务端主线程：服务器逻辑（实体、世界）
 * - 异步线程池：网络请求、AI 推理、文件 I/O
 * 
 *  警告：严禁在主线程上执行阻塞操作！
 */
public class ThreadScheduler {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * 异步任务线程池
     */
    private static final ExecutorService ASYNC_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, MiracleBridge.MOD_ID + "-async-" + System.currentTimeMillis());
        t.setDaemon(true);
        return t;
    });
    
    /**
     * 延迟任务调度器
     */
    private static final ScheduledExecutorService SCHEDULED_EXECUTOR = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, MiracleBridge.MOD_ID + "-scheduled-" + System.currentTimeMillis());
        t.setDaemon(true);
        return t;
    });
    
    // ==================== 客户端线程操作 ====================
    
    /**
     * 在客户端主线程上执行任务
     * 
     * @param task 要执行的任务
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
     * 在客户端主线程上执行任务并返回结果
     * 
     * @param supplier 要执行的任务
     * @return 包含结果的 CompletableFuture
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
     * 检查当前是否在客户端主线程上
     */
    @OnlyIn(Dist.CLIENT)
    public static boolean isOnClientThread() {
        return Minecraft.getInstance().isSameThread();
    }
    
    // ==================== 服务端线程操作 ====================
    
    /**
     * 在服务端主线程上执行任务
     * 
     * @param server 服务器实例
     * @param task 要执行的任务
     */
    public static void runOnServerThread(MinecraftServer server, Runnable task) {
        if (server.isSameThread()) {
            task.run();
        } else {
            server.execute(task);
        }
    }
    
    /**
     * 在服务端主线程上执行任务并返回结果
     * 
     * @param server 服务器实例
     * @param supplier 要执行的任务
     * @return 包含结果的 CompletableFuture
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
     * 检查当前是否在服务端主线程上
     */
    public static boolean isOnServerThread(@Nullable MinecraftServer server) {
        return server != null && server.isSameThread();
    }
    
    // ==================== 异步操作 ====================
    
    /**
     * 异步执行任务
     * 
     * @param task 要执行的任务
     * @return 表示任务完成的 CompletableFuture
     */
    public static CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(task, ASYNC_EXECUTOR);
    }
    
    /**
     * 异步执行任务并返回结果
     * 
     * @param supplier 要执行的任务
     * @return 包含结果的 CompletableFuture
     */
    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, ASYNC_EXECUTOR);
    }
    
    /**
     * 延迟执行任务
     * 
     * @param task 要执行的任务
     * @param delay 延迟时间
     * @param unit 时间单位
     * @return 可取消的 ScheduledFuture
     */
    public static ScheduledFuture<?> runLater(Runnable task, long delay, TimeUnit unit) {
        return SCHEDULED_EXECUTOR.schedule(task, delay, unit);
    }
    
    /**
     * 延迟在客户端主线程上执行任务
     * 
     * @param task 要执行的任务
     * @param delayMs 延迟时间（毫秒）
     */
    @OnlyIn(Dist.CLIENT)
    public static ScheduledFuture<?> runOnClientThreadLater(Runnable task, long delayMs) {
        return runLater(() -> runOnClientThread(task), delayMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 周期性执行任务
     * 
     * @param task 要执行的任务
     * @param initialDelay 初始延迟
     * @param period 执行周期
     * @param unit 时间单位
     * @return 可取消的 ScheduledFuture
     */
    public static ScheduledFuture<?> runPeriodically(Runnable task, long initialDelay, long period, TimeUnit unit) {
        return SCHEDULED_EXECUTOR.scheduleAtFixedRate(task, initialDelay, period, unit);
    }
    
    /**
     * 重复执行任务直到返回 false
     * 
     * @param task 要执行的任务，返回 true 继续执行，返回 false 停止
     * @param periodTicks 执行周期（以 tick 为单位，1 tick = 50ms）
     * @return 可取消的 ScheduledFuture
     */
    public static ScheduledFuture<?> runRepeating(Supplier<Boolean> task, int periodTicks) {
        long periodMs = periodTicks * 50L; // 1 tick = 50ms
        
        final ScheduledFuture<?>[] futureHolder = new ScheduledFuture<?>[1];
        
        futureHolder[0] = SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            try {
                boolean shouldContinue = task.get();
                if (!shouldContinue && futureHolder[0] != null) {
                    futureHolder[0].cancel(false);
                }
            } catch (Exception e) {
                LOGGER.error("重复任务执行出错", e);
                if (futureHolder[0] != null) {
                    futureHolder[0].cancel(false);
                }
            }
        }, 0, periodMs, TimeUnit.MILLISECONDS);
        
        return futureHolder[0];
    }
    
    /**
     * 在服务端主线程上重复执行任务
     * 
     * @param server 服务器实例
     * @param task 要执行的任务，返回 true 继续执行，返回 false 停止
     * @param periodTicks 执行周期（以 tick 为单位）
     * @return 可取消的 ScheduledFuture
     */
    public static ScheduledFuture<?> runRepeatingOnServer(MinecraftServer server, Supplier<Boolean> task, int periodTicks) {
        return runRepeating(() -> {
            CompletableFuture<Boolean> result = supplyOnServerThread(server, task);
            try {
                return result.get(1, TimeUnit.SECONDS);
            } catch (Exception e) {
                LOGGER.error("服务端重复任务执行出错", e);
                return false;
            }
        }, periodTicks);
    }
    
    // ==================== 线程安全执行 ====================
    
    /**
     * 确保任务在客户端主线程上执行（如果已在主线程则直接执行）
     * 这是一个阻塞方法，会等待任务完成
     * 
     * @param task 要执行的任务
     * @param timeoutMs 超时时间（毫秒）
     * @throws TimeoutException 如果超时
     * @throws ExecutionException 如果任务执行出错
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
    
    // ==================== 生命周期管理 ====================
    
    /**
     * 关闭所有线程池
     * 应在模组卸载时调用
     */
    public static void shutdown() {
        LOGGER.info("正在关闭线程调度器...");
        
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
        
        LOGGER.info("线程调度器已关闭");
    }
    
    private ThreadScheduler() {}
}
