package com.originofmiracles.miraclebridge.config;

import com.mojang.logging.LogUtils;
import com.originofmiracles.miraclebridge.util.MessageHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

/**
 * Config Reloader
 * 
 * Handles config file reload logic, including:
 * - Distinguishing hot-reload vs restart-required config items
 * - Rollback mechanism on validation failure
 * - Admin notification
 */
public class ConfigReloader {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private static final DateTimeFormatter BACKUP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    
    /**
     * Client config items that require restart to take effect
     */
    private static final Set<String> CLIENT_RESTART_REQUIRED = Set.of(
            "browser.defaultWidth",
            "browser.defaultHeight",
            "browser.transparentBackground"
    );
    
    /**
     * Server config items that require restart to take effect
     * TODO: Use when implementing server config hot-reload
     */
    @SuppressWarnings("unused")
    private static final Set<String> SERVER_RESTART_REQUIRED = Set.of(
            "entity.ysm.enabled"
    );
    
    /**
     * Config item snapshot for detecting changes
     */
    private static class ConfigSnapshot {
        final int browserWidth;
        final int browserHeight;
        final boolean browserTransparent;
        final String devServerUrl;
        final boolean debugEnabled;
        final boolean logBridgeRequests;
        final boolean logJsExecution;
        
        ConfigSnapshot() {
            this.browserWidth = ClientConfig.getBrowserWidth();
            this.browserHeight = ClientConfig.getBrowserHeight();
            this.browserTransparent = ClientConfig.BROWSER_TRANSPARENT_BACKGROUND.get();
            this.devServerUrl = ClientConfig.getDevServerUrl();
            this.debugEnabled = ClientConfig.isDebugEnabled();
            this.logBridgeRequests = ClientConfig.shouldLogBridgeRequests();
            this.logJsExecution = ClientConfig.shouldLogJsExecution();
        }
        
        /**
         * Get changed config paths compared to another snapshot
         */
        Set<String> getChangedPaths(ConfigSnapshot other) {
            Set<String> changed = new HashSet<>();
            if (browserWidth != other.browserWidth) changed.add("browser.defaultWidth");
            if (browserHeight != other.browserHeight) changed.add("browser.defaultHeight");
            if (browserTransparent != other.browserTransparent) changed.add("browser.transparentBackground");
            if (!devServerUrl.equals(other.devServerUrl)) changed.add("browser.devServerUrl");
            if (debugEnabled != other.debugEnabled) changed.add("debug.enabled");
            if (logBridgeRequests != other.logBridgeRequests) changed.add("debug.logBridgeRequests");
            if (logJsExecution != other.logJsExecution) changed.add("debug.logJsExecution");
            return changed;
        }
    }
    
    /**
     * Last valid config snapshot
     * Used for config rollback and change detection
     */
    @SuppressWarnings("unused") // TODO: Use when implementing config rollback
    private static ConfigSnapshot lastValidClientSnapshot;
    
    /**
     * Initialize config snapshot (call after config is loaded)
     */
    public static void initializeSnapshot() {
        lastValidClientSnapshot = new ConfigSnapshot();
        LOGGER.info("Config snapshot initialized");
    }
    
    /**
     * Try to reload client config
     * 
     * @return true if reload succeeded, false if rollback needed
     */
    public static boolean tryReloadClientConfig() {
        Path configPath = ModConfigs.getClientConfigPath();
        
        if (!Files.exists(configPath)) {
            LOGGER.warn("Client config file not found: {}", configPath);
            return false;
        }
        
        // Save current snapshot for comparison
        ConfigSnapshot beforeReload = new ConfigSnapshot();
        
        // Validate config
        ConfigValidator.ValidationResult result = ConfigValidator.validateClientConfig(configPath);
        
        if (!result.isValid()) {
            LOGGER.error("Client config validation failed: {} - {}", result.getFieldPath(), result.getErrorMessage());
            handleConfigError(configPath, result, true);
            return false;
        }
        
        // Config valid, check changes
        ConfigSnapshot afterReload = new ConfigSnapshot();
        Set<String> changedPaths = beforeReload.getChangedPaths(afterReload);
        
        if (changedPaths.isEmpty()) {
            LOGGER.debug("Client config unchanged");
            return true;
        }
        
        // Check if any restart-required items changed
        Set<String> restartRequired = new HashSet<>(changedPaths);
        restartRequired.retainAll(CLIENT_RESTART_REQUIRED);
        
        if (!restartRequired.isEmpty()) {
            notifyRestartRequired(restartRequired);
        }
        
        // Update snapshot
        lastValidClientSnapshot = afterReload;
        
        LOGGER.info("Client config reloaded, changed items: {}", changedPaths);
        return true;
    }
    
    /**
     * Try to reload server config
     */
    public static boolean tryReloadServerConfig() {
        Path configPath = ModConfigs.getServerConfigPath();
        
        if (!Files.exists(configPath)) {
            LOGGER.warn("Server config file not found: {}", configPath);
            return false;
        }
        
        ConfigValidator.ValidationResult result = ConfigValidator.validateServerConfig(configPath);
        
        if (!result.isValid()) {
            LOGGER.error("Server config validation failed: {} - {}", result.getFieldPath(), result.getErrorMessage());
            handleConfigError(configPath, result, false);
            return false;
        }
        
        LOGGER.info("Server config reloaded");
        return true;
    }
    
    /**
     * Handle config error: backup error file, restore old config, notify admins
     */
    private static void handleConfigError(Path configPath, ConfigValidator.ValidationResult result, boolean isClient) {
        // 1. Backup the erroneous config file
        Path backupPath = backupErrorConfig(configPath);
        
        // 2. Restore old config (Forge handles config file saving automatically)
        // We don't need to manually restore because Forge won't load invalid config
        
        // 3. Notify admins
        String errorMessage = String.format(
                "§c[Miracle Bridge] Config validation failed§r\n" +
                "§eConfig item: §f%s\n" +
                "§eError: §f%s\n" +
                "§eError config backed up to: §f%s",
                result.getFieldPath(),
                result.getErrorMessage(),
                backupPath != null ? backupPath.getFileName().toString() : "(backup failed)"
        );
        
        LOGGER.error(errorMessage.replace("§.", "").replace("§f", "").replace("§e", "").replace("§c", "").replace("§r", ""));
        
        // 向游戏内 OP 发送通知
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            MessageHelper.sendToOps(server, Component.literal(errorMessage));
        }
    }
    
    /**
     * Backup erroneous config file
     * 
     * @param configPath config file path
     * @return backup file path, or null on failure
     */
    @Nullable
    private static Path backupErrorConfig(Path configPath) {
        try {
            String timestamp = LocalDateTime.now().format(BACKUP_FORMAT);
            String backupName = timestamp + "-error_config.toml";
            Path backupPath = configPath.resolveSibling(backupName);
            
            Files.copy(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Error config backed up to: {}", backupPath);
            
            return backupPath;
            
        } catch (IOException e) {
            LOGGER.error("Failed to backup error config", e);
            return null;
        }
    }
    
    /**
     * Notify admins that some config items require restart to take effect
     */
    private static void notifyRestartRequired(Set<String> configPaths) {
        String message = String.format(
                "§6[Miracle Bridge] The following config changes require restart to take effect:§r\n§f%s",
                String.join(", ", configPaths)
        );
        
        LOGGER.warn("Config changes require restart: {}", configPaths);
        
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            MessageHelper.sendToOps(server, Component.literal(message));
        }
    }
    
    private ConfigReloader() {}
}
