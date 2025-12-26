package com.originofmiracles.miraclebridge.event;

import java.util.Collection;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.originofmiracles.miraclebridge.browser.BrowserManager;
import com.originofmiracles.miraclebridge.browser.MiracleBrowser;
import com.originofmiracles.miraclebridge.network.ModNetworkHandler;
import com.originofmiracles.miraclebridge.network.S2CPushEventPacket;

import net.minecraft.server.level.ServerPlayer;

/**
 * Bridge 事件总线
 * 
 * 提供服务端 -> 客户端前端的事件推送 API。
 * 这是 Miracle-Bridge 对外的核心接口，其他模组通过此类发送 UI 事件。
 * 
 * 使用示例:
 * <pre>
 * // 服务端推送事件到某玩家的前端
 * JsonObject data = new JsonObject();
 * data.addProperty("message", "Hello!");
 * BridgeEventBus.pushToClient(player, "mymod:greeting", data);
 * 
 * // 批量推送到多个玩家
 * BridgeEventBus.pushToClients(playerList, "mymod:broadcast", data);
 * </pre>
 */
public class BridgeEventBus {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    
    // ==================== 服务端 API ====================
    
    /**
     * 推送事件到单个玩家的前端
     * 
     * @param player 目标玩家
     * @param eventType 事件类型，建议格式 "modid:eventName"
     * @param data JSON 对象数据
     */
    public static void pushToClient(ServerPlayer player, String eventType, JsonObject data) {
        pushToClient(player, eventType, GSON.toJson(data));
    }
    
    /**
     * 推送事件到单个玩家的前端
     * 
     * @param player 目标玩家
     * @param eventType 事件类型
     * @param jsonData JSON 字符串数据
     */
    public static void pushToClient(ServerPlayer player, String eventType, String jsonData) {
        if (player == null) {
            LOGGER.warn("尝试推送事件到 null 玩家: {}", eventType);
            return;
        }
        
        S2CPushEventPacket packet = new S2CPushEventPacket(eventType, jsonData);
        ModNetworkHandler.sendToPlayer(player, packet);
        
        LOGGER.debug("已推送事件 {} 到 {}", eventType, player.getName().getString());
    }
    
    /**
     * 推送事件到多个玩家的前端
     * 
     * @param players 目标玩家列表
     * @param eventType 事件类型
     * @param data JSON 对象数据
     */
    public static void pushToClients(Collection<ServerPlayer> players, String eventType, JsonObject data) {
        pushToClients(players, eventType, GSON.toJson(data));
    }
    
    /**
     * 推送事件到多个玩家的前端
     * 
     * @param players 目标玩家列表
     * @param eventType 事件类型
     * @param jsonData JSON 字符串数据
     */
    public static void pushToClients(Collection<ServerPlayer> players, String eventType, String jsonData) {
        if (players == null || players.isEmpty()) {
            return;
        }
        
        S2CPushEventPacket packet = new S2CPushEventPacket(eventType, jsonData);
        
        for (ServerPlayer player : players) {
            if (player != null) {
                ModNetworkHandler.sendToPlayer(player, packet);
            }
        }
        
        LOGGER.debug("已推送事件 {} 到 {} 位玩家", eventType, players.size());
    }
    
    // ==================== 客户端内部 API ====================
    
    /**
     * 将事件推送到前端浏览器（仅客户端调用）
     * 
     * @param eventType 事件类型
     * @param jsonData JSON 字符串数据
     */
    public static void pushEventToFrontend(String eventType, String jsonData) {
        LOGGER.info("[BridgeEventBus] 推送事件到前端: {}", eventType);
        
        // 使用 DOM CustomEvent - 这种方式不依赖 JS SDK 是否正确注入
        // 前端通过 window.addEventListener 监听
        String jsCall = String.format(
            "(function() { " +
            "  try { " +
            "    var data = JSON.parse('%s'); " +
            "    window.dispatchEvent(new CustomEvent('%s', { detail: data })); " +
            "    console.log('[MiracleBridge] Event dispatched: %s'); " +
            "  } catch(e) { " +
            "    console.error('[MiracleBridge] Event dispatch error:', e); " +
            "  } " +
            "})()",
            escapeJsString(jsonData),
            escapeJsString(eventType),
            escapeJsString(eventType)
        );
        
        LOGGER.debug("[BridgeEventBus] JS 调用长度: {}", jsCall.length());
        
        // 通过 BrowserManager 获取当前浏览器并执行 JS
        BrowserManager manager = BrowserManager.getInstance();
        if (manager != null) {
            String currentName = manager.getCurrentBrowserName();
            LOGGER.info("[BridgeEventBus] 当前浏览器名: {}", currentName);
            
            if (currentName != null) {
                MiracleBrowser browser = manager.getBrowser(currentName);
                if (browser != null) {
                    browser.executeJavaScript(jsCall);
                    LOGGER.info("[BridgeEventBus] 已执行前端事件: {}", eventType);
                    return;
                } else {
                    LOGGER.warn("[BridgeEventBus] 找不到当前浏览器实例: {}", currentName);
                }
            }
            
            // 尝试默认浏览器
            MiracleBrowser defaultBrowser = manager.getBrowser(BrowserManager.DEFAULT_BROWSER_NAME);
            LOGGER.info("[BridgeEventBus] 尝试默认浏览器: {}", BrowserManager.DEFAULT_BROWSER_NAME);
            if (defaultBrowser != null) {
                defaultBrowser.executeJavaScript(jsCall);
                LOGGER.info("[BridgeEventBus] 已执行前端事件(默认浏览器): {}", eventType);
                return;
            } else {
                LOGGER.warn("[BridgeEventBus] 默认浏览器不存在");
            }
        } else {
            LOGGER.warn("[BridgeEventBus] BrowserManager 为 null");
        }
        
        LOGGER.warn("[BridgeEventBus] 没有可用浏览器，无法推送事件: {}", eventType);
    }
    
    /**
     * 转义 JS 字符串中的特殊字符
     */
    private static String escapeJsString(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("'", "\\'")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r");
    }
}
