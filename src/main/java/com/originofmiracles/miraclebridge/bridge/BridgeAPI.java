package com.originofmiracles.miraclebridge.bridge;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.originofmiracles.miraclebridge.browser.MiracleBrowser;
import com.originofmiracles.miraclebridge.config.ClientConfig;
import com.originofmiracles.miraclebridge.config.ServerConfig;
import com.originofmiracles.miraclebridge.network.C2SBridgeActionPacket;
import com.originofmiracles.miraclebridge.network.ModNetworkHandler;
import com.originofmiracles.miraclebridge.util.ThreadScheduler;

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
    
    /**
     * 待处理的服务端请求（线程安全）
     * Key: requestId, Value: CompletableFuture<JsonObject>
     */
    private final Map<String, CompletableFuture<JsonObject>> pendingRequests = new ConcurrentHashMap<>();
    
    public BridgeAPI(MiracleBrowser browser) {
        this.browser = browser;
        registerDefaultHandlers();
        LOGGER.info("BridgeAPI 已初始化");
    }
    
    /**
     * 注册默认 API 处理器
     */
    private void registerDefaultHandlers() {
        // 示例：获取玩家信息（客户端本地处理）
        register("getPlayerInfo", request -> {
            JsonObject response = new JsonObject();
            response.addProperty("name", "Player");
            response.addProperty("health", 20);
            response.addProperty("level", 1);
            return response;
        });
        
        // 示例：传送玩家（需要服务端处理）
        register("teleport", request -> {
            // 这个处理器只是占位符，实际处理通过 requestFromServer 完成
            JsonObject response = new JsonObject();
            response.addProperty("error", "请使用 requestFromServer 方法");
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
     * 处理来自 JavaScript 的请求（本地处理）
     * @param action 动作名称
     * @param requestJson 请求负载（JSON 字符串）
     * @return 响应 JSON 字符串
     */
    public String handleRequest(String action, String requestJson) {
        // 日志记录
        if (ClientConfig.shouldLogBridgeRequests()) {
            LOGGER.info("Bridge 请求: action={}, payload={}", action, requestJson);
        }
        
        try {
            // 检查请求大小
            if (requestJson.length() > ServerConfig.getMaxRequestSize()) {
                LOGGER.warn("请求体过大: {} bytes", requestJson.length());
                return errorResponse("请求体过大");
            }
            
            JsonObject request = GSON.fromJson(requestJson, JsonObject.class);
            
            BridgeHandler handler = handlers.get(action);
            if (handler == null) {
                LOGGER.warn("未知的桥梁动作: {}", action);
                return errorResponse("未知动作: " + action);
            }
            
            JsonObject response = handler.handle(request);
            String responseJson = GSON.toJson(response);
            
            if (ClientConfig.shouldLogBridgeRequests()) {
                LOGGER.info("Bridge 响应: action={}, response={}", action, responseJson);
            }
            
            return responseJson;
            
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
        
        // 确保在客户端主线程执行 JS
        ThreadScheduler.runOnClientThread(() -> {
            browser.executeJavaScript(script);
        });
        
        if (ClientConfig.shouldLogBridgeRequests()) {
            LOGGER.debug("已推送事件: {} 数据: {}", eventName, dataJson);
        }
    }
    
    /**
     * 创建错误响应 JSON
     */
    private String errorResponse(String message) {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        return GSON.toJson(error);
    }
    
    // ==================== 服务端请求 ====================
    
    /**
     * 向服务端发送请求并异步获取响应
     * 
     * @param action 动作名称
     * @param payload 请求参数
     * @return 包含响应的 CompletableFuture
     */
    public CompletableFuture<JsonObject> requestFromServer(String action, JsonObject payload) {
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        
        // 注册等待响应
        pendingRequests.put(requestId, future);
        
        // 设置超时
        int timeout = ServerConfig.getRequestTimeout();
        ThreadScheduler.runLater(() -> {
            CompletableFuture<JsonObject> pending = pendingRequests.remove(requestId);
            if (pending != null && !pending.isDone()) {
                JsonObject error = new JsonObject();
                error.addProperty("error", "请求超时");
                pending.complete(error);
            }
        }, timeout, java.util.concurrent.TimeUnit.MILLISECONDS);
        
        // 发送网络包到服务端
        String payloadJson = GSON.toJson(payload);
        ModNetworkHandler.sendToServer(new C2SBridgeActionPacket(action, requestId, payloadJson));
        
        if (ClientConfig.shouldLogBridgeRequests()) {
            LOGGER.debug("已发送服务端请求: action={}, requestId={}", action, requestId);
        }
        
        return future;
    }
    
    /**
     * 处理来自服务端的响应
     * 应在收到 S2CFullSyncPacket (type="bridge_response") 时调用
     * 
     * @param responseJson 响应 JSON 字符串
     */
    public void handleServerResponse(String responseJson) {
        try {
            JsonObject response = GSON.fromJson(responseJson, JsonObject.class);
            String requestId = response.has("requestId") ? response.get("requestId").getAsString() : null;
            
            if (requestId == null) {
                LOGGER.warn("收到无 requestId 的服务端响应");
                return;
            }
            
            CompletableFuture<JsonObject> pending = pendingRequests.remove(requestId);
            if (pending != null) {
                pending.complete(response);
                if (ClientConfig.shouldLogBridgeRequests()) {
                    LOGGER.debug("已完成服务端请求: requestId={}", requestId);
                }
            } else {
                LOGGER.warn("收到未知 requestId 的响应: {}", requestId);
            }
            
        } catch (Exception e) {
            LOGGER.error("解析服务端响应失败", e);
        }
    }
    
    /**
     * 获取浏览器实例
     */
    public MiracleBrowser getBrowser() {
        return browser;
    }
    
    /**
     * 桥梁处理器的函数式接口
     */
    @FunctionalInterface
    public interface BridgeHandler extends Function<JsonObject, JsonObject> {
        /**
         * 处理桥梁请求
         * @param request 请求的 JSON 对象
         * @return 响应的 JSON 对象
         */
        JsonObject handle(JsonObject request);
        
        @Override
        default JsonObject apply(JsonObject request) {
            return handle(request);
        }
    }
}
