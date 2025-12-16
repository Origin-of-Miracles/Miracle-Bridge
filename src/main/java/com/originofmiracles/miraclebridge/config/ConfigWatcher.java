package com.originofmiracles.miraclebridge.config;

import com.mojang.logging.LogUtils;
import com.originofmiracles.miraclebridge.MiracleBridge;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 配置文件监听器
 * 
 * 使用 Java WatchService 监听配置文件变化，实现热更新。
 * 检测到文件修改后，会触发配置重载流程。
 */
public class ConfigWatcher {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * 文件修改后的防抖延迟（毫秒）
     * 防止文件保存过程中的多次触发
     */
    private static final long DEBOUNCE_DELAY_MS = 500;
    
    private static ConfigWatcher instance;
    
    private final Path configDir;
    private final ExecutorService watcherThread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    private WatchService watchService;
    private long lastClientModTime = 0;
    private long lastServerModTime = 0;
    
    private ConfigWatcher(Path configDir) {
        this.configDir = configDir;
        this.watcherThread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, MiracleBridge.MOD_ID + "-config-watcher");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * 获取单例实例
     */
    public static ConfigWatcher getInstance() {
        if (instance == null) {
            instance = new ConfigWatcher(ModConfigs.getConfigDir());
        }
        return instance;
    }
    
    /**
     * 启动配置监听
     */
    public void start() {
        if (running.getAndSet(true)) {
            LOGGER.warn("配置监听器已在运行");
            return;
        }
        
        try {
            watchService = FileSystems.getDefault().newWatchService();
            configDir.register(watchService, 
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE);
            
            LOGGER.info("配置监听器已启动，监听目录: {}", configDir);
            
            watcherThread.submit(this::watchLoop);
            
        } catch (IOException e) {
            LOGGER.error("启动配置监听器失败", e);
            running.set(false);
        }
    }
    
    /**
     * 停止配置监听
     */
    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }
        
        try {
            if (watchService != null) {
                watchService.close();
            }
            watcherThread.shutdown();
            if (!watcherThread.awaitTermination(5, TimeUnit.SECONDS)) {
                watcherThread.shutdownNow();
            }
            LOGGER.info("配置监听器已停止");
            
        } catch (Exception e) {
            LOGGER.error("停止配置监听器时出错", e);
        }
    }
    
    /**
     * 监听循环
     */
    private void watchLoop() {
        while (running.get()) {
            try {
                WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                if (key == null) {
                    continue;
                }
                
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    
                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path changedFile = pathEvent.context();
                    
                    handleFileChange(changedFile);
                }
                
                key.reset();
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                // 监听服务已关闭，正常退出
                break;
            } catch (Exception e) {
                LOGGER.error("配置监听出错", e);
            }
        }
    }
    
    /**
     * 处理文件变化
     */
    private void handleFileChange(Path changedFile) {
        String fileName = changedFile.toString();
        long currentTime = System.currentTimeMillis();
        
        // 检查是否是我们关心的配置文件
        if (fileName.equals(ModConfigs.CLIENT_CONFIG_NAME)) {
            // 防抖处理
            if (currentTime - lastClientModTime < DEBOUNCE_DELAY_MS) {
                return;
            }
            lastClientModTime = currentTime;
            
            LOGGER.info("检测到客户端配置文件变化，尝试重载...");
            
            // 延迟执行以确保文件写入完成
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            
            ConfigReloader.tryReloadClientConfig();
            
        } else if (fileName.equals(ModConfigs.SERVER_CONFIG_NAME)) {
            // 防抖处理
            if (currentTime - lastServerModTime < DEBOUNCE_DELAY_MS) {
                return;
            }
            lastServerModTime = currentTime;
            
            LOGGER.info("检测到服务端配置文件变化，尝试重载...");
            
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            
            ConfigReloader.tryReloadServerConfig();
        }
    }
    
    /**
     * 检查监听器是否在运行
     */
    public boolean isRunning() {
        return running.get();
    }
}
