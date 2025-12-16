package com.originofmiracles.miraclebridge.util;

import com.mojang.logging.LogUtils;
import com.originofmiracles.miraclebridge.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import javax.annotation.Nullable;
import java.nio.file.Path;

/**
 * 消息发送工具类
 * 
 * 提供统一的消息发送 API，支持：
 * - 向玩家发送消息（聊天栏/ActionBar）
 * - 向所有 OP 广播消息
 * - 同时输出日志和游戏内消息
 * - 配置错误专用通知
 */
public class MessageHelper {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * 模组消息前缀
     */
    public static final String PREFIX = "§7[§bMiracle Bridge§7]§r ";
    
    /**
     * 错误前缀
     */
    public static final String ERROR_PREFIX = "§7[§cMiracle Bridge§7]§r ";
    
    /**
     * 警告前缀
     */
    public static final String WARN_PREFIX = "§7[§eMiracle Bridge§7]§r ";
    
    // ==================== 玩家消息 ====================
    
    /**
     * 向玩家发送聊天消息
     * 
     * @param player 目标玩家
     * @param message 消息内容
     */
    public static void sendToPlayer(Player player, Component message) {
        player.sendSystemMessage(message);
    }
    
    /**
     * 向玩家发送聊天消息（纯文本）
     */
    public static void sendToPlayer(Player player, String message) {
        sendToPlayer(player, Component.literal(PREFIX + message));
    }
    
    /**
     * 向玩家发送 ActionBar 消息
     * 
     * @param player 目标玩家
     * @param message 消息内容
     */
    @OnlyIn(Dist.CLIENT)
    public static void sendActionBar(Player player, Component message) {
        if (player instanceof net.minecraft.client.player.LocalPlayer localPlayer) {
            localPlayer.displayClientMessage(message, true);
        }
    }
    
    /**
     * 向本地玩家发送消息（仅客户端）
     */
    @OnlyIn(Dist.CLIENT)
    public static void sendToLocalPlayer(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            sendToPlayer(mc.player, message);
        }
    }
    
    // ==================== 广播消息 ====================
    
    /**
     * 向所有 OP 发送消息
     * 
     * @param server 服务器实例
     * @param message 消息内容
     */
    public static void sendToOps(MinecraftServer server, Component message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (server.getPlayerList().isOp(player.getGameProfile())) {
                player.sendSystemMessage(message);
            }
        }
    }
    
    /**
     * 向所有 OP 发送消息（纯文本）
     */
    public static void sendToOps(MinecraftServer server, String message) {
        sendToOps(server, Component.literal(PREFIX + message));
    }
    
    /**
     * 向所有玩家广播消息
     * 
     * @param server 服务器实例
     * @param message 消息内容
     */
    public static void broadcast(MinecraftServer server, Component message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
        }
    }
    
    /**
     * 向所有玩家广播消息（纯文本）
     */
    public static void broadcast(MinecraftServer server, String message) {
        broadcast(server, Component.literal(PREFIX + message));
    }
    
    // ==================== 日志 + 游戏内通知 ====================
    
    /**
     * 同时输出日志和向 OP 发送游戏内消息
     * 
     * @param level 日志级别
     * @param message 消息内容
     * @param server 服务器实例（可为 null，仅输出日志）
     */
    public static void logAndNotify(Level level, String message, @Nullable MinecraftServer server) {
        // 输出日志
        switch (level) {
            case ERROR -> LOGGER.error(message);
            case WARN -> LOGGER.warn(message);
            case INFO -> LOGGER.info(message);
            case DEBUG -> {
                if (ClientConfig.isDebugEnabled()) {
                    LOGGER.debug(message);
                }
            }
            default -> LOGGER.info(message);
        }
        
        // 发送游戏内消息
        if (server != null) {
            String prefix = switch (level) {
                case ERROR -> ERROR_PREFIX;
                case WARN -> WARN_PREFIX;
                default -> PREFIX;
            };
            sendToOps(server, Component.literal(prefix + message));
        }
    }
    
    /**
     * 自动获取服务器实例并发送
     */
    public static void logAndNotify(Level level, String message) {
        logAndNotify(level, message, ServerLifecycleHooks.getCurrentServer());
    }
    
    // ==================== 配置错误通知 ====================
    
    /**
     * 通知配置错误
     * 
     * @param reason 错误原因
     * @param backupPath 备份文件路径（可为 null）
     */
    public static void notifyConfigError(String reason, @Nullable Path backupPath) {
        StringBuilder sb = new StringBuilder();
        sb.append("§c配置错误: §f").append(reason);
        
        if (backupPath != null) {
            sb.append("\n§e错误配置已备份至: §f").append(backupPath.getFileName());
        }
        
        String message = sb.toString();
        
        // 日志输出
        LOGGER.error("配置错误: {} | 备份: {}", reason, backupPath);
        
        // 游戏内通知
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            sendToOps(server, Component.literal(ERROR_PREFIX + message));
        }
    }
    
    /**
     * 通知配置已重载
     * 
     * @param configName 配置文件名
     */
    public static void notifyConfigReloaded(String configName) {
        String message = "配置已重载: " + configName;
        
        if (ClientConfig.isDebugEnabled()) {
            LOGGER.info(message);
            
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                sendToOps(server, message);
            }
        }
    }
    
    /**
     * 通知配置项需要重启才能生效
     * 
     * @param configPaths 变更的配置项路径
     */
    public static void notifyRestartRequired(String... configPaths) {
        String paths = String.join(", ", configPaths);
        String message = "§6以下配置项变更需要重启才能生效: §f" + paths;
        
        LOGGER.warn("配置项变更需要重启: {}", paths);
        
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            sendToOps(server, Component.literal(WARN_PREFIX + message));
        }
    }
    
    private MessageHelper() {}
}
