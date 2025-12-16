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
 * 配置重载器
 * 
 * 负责处理配置文件的重载逻辑，包括：
 * - 区分热生效和重启生效的配置项
 * - 配置校验失败时的回退机制
 * - 向管理员发送通知
 */
public class ConfigReloader {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private static final DateTimeFormatter BACKUP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    
    /**
     * 需要重启才能生效的客户端配置项
     */
    private static final Set<String> CLIENT_RESTART_REQUIRED = Set.of(
            "browser.defaultWidth",
            "browser.defaultHeight",
            "browser.transparentBackground"
    );
    
    /**
     * 需要重启才能生效的服务端配置项
     * TODO: 在实现服务端配置热重载时使用
     */
    @SuppressWarnings("unused")
    private static final Set<String> SERVER_RESTART_REQUIRED = Set.of(
            "entity.ysm.enabled"
    );
    
    /**
     * 配置项快照，用于检测变更
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
         * 获取与另一快照相比变更的配置项路径
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
     * 最后一次有效的配置快照
     * 用于配置回退机制和变更检测
     */
    @SuppressWarnings("unused") // TODO: 完善配置回退机制时使用
    private static ConfigSnapshot lastValidClientSnapshot;
    
    /**
     * 初始化配置快照（在配置加载完成后调用）
     */
    public static void initializeSnapshot() {
        lastValidClientSnapshot = new ConfigSnapshot();
        LOGGER.info("配置快照已初始化");
    }
    
    /**
     * 尝试重载客户端配置
     * 
     * @return true 如果重载成功，false 如果需要回退
     */
    public static boolean tryReloadClientConfig() {
        Path configPath = ModConfigs.getClientConfigPath();
        
        if (!Files.exists(configPath)) {
            LOGGER.warn("客户端配置文件不存在: {}", configPath);
            return false;
        }
        
        // 保存当前快照用于比较
        ConfigSnapshot beforeReload = new ConfigSnapshot();
        
        // 校验配置
        ConfigValidator.ValidationResult result = ConfigValidator.validateClientConfig(configPath);
        
        if (!result.isValid()) {
            LOGGER.error("客户端配置校验失败: {} - {}", result.getFieldPath(), result.getErrorMessage());
            handleConfigError(configPath, result, true);
            return false;
        }
        
        // 配置有效，检查变更
        ConfigSnapshot afterReload = new ConfigSnapshot();
        Set<String> changedPaths = beforeReload.getChangedPaths(afterReload);
        
        if (changedPaths.isEmpty()) {
            LOGGER.debug("客户端配置无变更");
            return true;
        }
        
        // 检查是否有需要重启的配置项变更
        Set<String> restartRequired = new HashSet<>(changedPaths);
        restartRequired.retainAll(CLIENT_RESTART_REQUIRED);
        
        if (!restartRequired.isEmpty()) {
            notifyRestartRequired(restartRequired);
        }
        
        // 更新快照
        lastValidClientSnapshot = afterReload;
        
        LOGGER.info("客户端配置已重载，变更项: {}", changedPaths);
        return true;
    }
    
    /**
     * 尝试重载服务端配置
     */
    public static boolean tryReloadServerConfig() {
        Path configPath = ModConfigs.getServerConfigPath();
        
        if (!Files.exists(configPath)) {
            LOGGER.warn("服务端配置文件不存在: {}", configPath);
            return false;
        }
        
        ConfigValidator.ValidationResult result = ConfigValidator.validateServerConfig(configPath);
        
        if (!result.isValid()) {
            LOGGER.error("服务端配置校验失败: {} - {}", result.getFieldPath(), result.getErrorMessage());
            handleConfigError(configPath, result, false);
            return false;
        }
        
        LOGGER.info("服务端配置已重载");
        return true;
    }
    
    /**
     * 处理配置错误：备份错误文件，恢复旧配置，通知管理员
     */
    private static void handleConfigError(Path configPath, ConfigValidator.ValidationResult result, boolean isClient) {
        // 1. 备份错误的配置文件
        Path backupPath = backupErrorConfig(configPath);
        
        // 2. 恢复旧配置（通过重新保存 ForgeConfigSpec 的当前值）
        // Forge 会自动处理配置文件的保存，这里我们不需要手动恢复
        // 因为错误的配置不会被 Forge 加载
        
        // 3. 通知管理员
        String errorMessage = String.format(
                "§c[Miracle Bridge] 配置校验失败§r\n" +
                "§e配置项: §f%s\n" +
                "§e错误: §f%s\n" +
                "§e错误配置已备份至: §f%s",
                result.getFieldPath(),
                result.getErrorMessage(),
                backupPath != null ? backupPath.getFileName().toString() : "(备份失败)"
        );
        
        LOGGER.error(errorMessage.replace("§.", "").replace("§f", "").replace("§e", "").replace("§c", "").replace("§r", ""));
        
        // 向游戏内 OP 发送通知
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            MessageHelper.sendToOps(server, Component.literal(errorMessage));
        }
    }
    
    /**
     * 备份错误的配置文件
     * 
     * @param configPath 配置文件路径
     * @return 备份文件路径，失败返回 null
     */
    @Nullable
    private static Path backupErrorConfig(Path configPath) {
        try {
            String timestamp = LocalDateTime.now().format(BACKUP_FORMAT);
            String backupName = timestamp + "-error_config.toml";
            Path backupPath = configPath.resolveSibling(backupName);
            
            Files.copy(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("错误配置已备份至: {}", backupPath);
            
            return backupPath;
            
        } catch (IOException e) {
            LOGGER.error("备份错误配置失败", e);
            return null;
        }
    }
    
    /**
     * 通知管理员有配置项需要重启才能生效
     */
    private static void notifyRestartRequired(Set<String> configPaths) {
        String message = String.format(
                "§6[Miracle Bridge] 以下配置项变更需要重启才能生效:§r\n§f%s",
                String.join(", ", configPaths)
        );
        
        LOGGER.warn("配置项变更需要重启: {}", configPaths);
        
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            MessageHelper.sendToOps(server, Component.literal(message));
        }
    }
    
    private ConfigReloader() {}
}
