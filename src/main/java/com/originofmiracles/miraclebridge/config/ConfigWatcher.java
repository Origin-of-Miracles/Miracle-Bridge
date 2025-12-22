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
 * Config File Watcher
 * 
 * Uses Java WatchService to monitor config file changes for hot-reload.
 * Triggers config reload when file modifications are detected.
 */
public class ConfigWatcher {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * Debounce delay after file modification (milliseconds)
     * Prevents multiple triggers during file save process
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
     * Get singleton instance
     */
    public static ConfigWatcher getInstance() {
        if (instance == null) {
            instance = new ConfigWatcher(ModConfigs.getConfigDir());
        }
        return instance;
    }
    
    /**
     * Start config watching
     */
    public void start() {
        if (running.getAndSet(true)) {
            LOGGER.warn("Config watcher is already running");
            return;
        }
        
        try {
            watchService = FileSystems.getDefault().newWatchService();
            configDir.register(watchService, 
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE);
            
            LOGGER.info("Config watcher started, watching directory: {}", configDir);
            
            watcherThread.submit(this::watchLoop);
            
        } catch (IOException e) {
            LOGGER.error("Failed to start config watcher", e);
            running.set(false);
        }
    }
    
    /**
     * Stop config watching
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
            LOGGER.info("Config watcher stopped");
            
        } catch (Exception e) {
            LOGGER.error("Error stopping config watcher", e);
        }
    }
    
    /**
     * Watch loop
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
                // Watch service closed, normal exit
                break;
            } catch (Exception e) {
                LOGGER.error("Config watch error", e);
            }
        }
    }
    
    /**
     * Handle file change
     */
    private void handleFileChange(Path changedFile) {
        String fileName = changedFile.toString();
        long currentTime = System.currentTimeMillis();
        
        // Check if it's a config file we care about
        if (fileName.equals(ModConfigs.CLIENT_CONFIG_NAME)) {
            // Debounce
            if (currentTime - lastClientModTime < DEBOUNCE_DELAY_MS) {
                return;
            }
            lastClientModTime = currentTime;
            
            LOGGER.info("Client config file changed, attempting reload...");
            
            // Delay to ensure file write is complete
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            
            ConfigReloader.tryReloadClientConfig();
            
        } else if (fileName.equals(ModConfigs.SERVER_CONFIG_NAME)) {
            // Debounce
            if (currentTime - lastServerModTime < DEBOUNCE_DELAY_MS) {
                return;
            }
            lastServerModTime = currentTime;
            
            LOGGER.info("Server config file changed, attempting reload...");
            
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
     * Check if watcher is running
     */
    public boolean isRunning() {
        return running.get();
    }
}
