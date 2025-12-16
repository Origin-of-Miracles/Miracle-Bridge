package com.originofmiracles.miraclebridge.bridge;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.originofmiracles.miraclebridge.browser.MiracleBrowser;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * JavaScript ↔ Java 通信桥梁 API
 * 
 * 提供：
 * - JS → Java：通过 bridge:// 协议处理请求
 * - Java → JS：通过 executeJavaScript 推送事件
 * 
 * JS 使用示例：
 * ```javascript
 * // 调用 Java 方法
 * const result = await fetch('bridge://api/getPlayerInfo', {
 *   method: 'POST',
 *   body: JSON.stringify({ playerId: 'player1' })
 * }).then(r => r.json());
 * 
 * // 监听 Java 事件
 * window.addEventListener('gameEvent', (e) => {
 *   console.log('来自 Java 的事件:', e.detail);
 * });
 * ```
 */
public class BridgeAPI {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    
    private final MiracleBrowser browser;
    private final Map<String, BridgeHandler> handlers = new HashMap<>();
    
    public BridgeAPI(MiracleBrowser browser) {
        this.browser = browser;
        registerDefaultHandlers();
    }
    
    /**
     * 注册默认 API 处理器
     */
    private void registerDefaultHandlers() {
        // 示例：获取玩家信息
        register("getPlayerInfo", request -> {
            JsonObject response = new JsonObject();
            response.addProperty("name", "Player");
            response.addProperty("health", 20);
            response.addProperty("level", 1);
            return response;
        });
        
        // 示例：传送玩家
        register("teleport", request -> {
            double x = request.get("x").getAsDouble();
            double y = request.get("y").getAsDouble();
            double z = request.get("z").getAsDouble();
            
            // TODO: 实现实际传送逻辑
            LOGGER.info("请求传送到: ({}, {}, {})", x, y, z);
            
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            return response;
        });
        
        LOGGER.info("已注册 {} 个桥梁处理器", handlers.size());
    }
    
    /**
     * 注册自定义 API 处理器
     * @param action 动作名称（例如 "getInventory"）
     * @param handler 处理函数，接受请求 JSON 并返回响应 JSON
     */
    public void register(String action, BridgeHandler handler) {
        handlers.put(action, handler);
        LOGGER.debug("已注册桥梁处理器: {}", action);
    }
    
    /**
     * 处理来自 JavaScript 的请求
     * @param action 动作名称
     * @param requestJson 请求负载（JSON 字符串）
     * @return 响应 JSON 字符串
     */
    public String handleRequest(String action, String requestJson) {
        try {
            JsonObject request = GSON.fromJson(requestJson, JsonObject.class);
            
            BridgeHandler handler = handlers.get(action);
            if (handler == null) {
                LOGGER.warn("未知的桥梁动作: {}", action);
                return errorResponse("未知动作: " + action);
            }
            
            JsonObject response = handler.handle(request);
            return GSON.toJson(response);
            
        } catch (Exception e) {
            LOGGER.error("处理桥梁请求时出错: {}", action, e);
            return errorResponse("内部错误: " + e.getMessage());
        }
    }
    
    /**
     * 向 JavaScript 推送事件
     * @param eventName 事件名称
     * @param data 事件数据（将被序列化为 JSON）
     */
    public void pushEvent(String eventName, Object data) {
        if (!browser.isReady()) {
            LOGGER.warn("无法推送事件 '{}'：浏览器未就绪", eventName);
            return;
        }
        
        String dataJson = GSON.toJson(data);
        String script = String.format(
            "window.dispatchEvent(new CustomEvent('%s', { detail: %s }));",
            eventName, dataJson
        );
        
        browser.executeJavaScript(script);
        LOGGER.debug("已推送事件: {} 数据: {}", eventName, dataJson);
    }
    
    /**
     * 创建错误响应 JSON
     */
    private String errorResponse(String message) {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        return GSON.toJson(error);
    }
    
    /**
     * 桥梁处理器的函数式接口
     */
    @FunctionalInterface
    public interface BridgeHandler extends Function<JsonObject, JsonObject> {
        @Override
        JsonObject handle(JsonObject request);
        
        @Override
        default JsonObject apply(JsonObject request) {
            return handle(request);
        }
    }
}
