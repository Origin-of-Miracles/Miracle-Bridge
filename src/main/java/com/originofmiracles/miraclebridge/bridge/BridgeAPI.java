package com.originofmiracles.miraclebridge.bridge;

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
 * // Listen for Java events
 * window.addEventListener('gameEvent', (e) => {
 *   console.log('Event from Java:', e.detail);
 * });
 * ```
 */
public class BridgeAPI {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    
    private final MiracleBrowser browser;
    private final Map<String, BridgeHandler> handlers = new ConcurrentHashMap<>();
    
    /**
     * 待处理的服务端请求（线程安全）
     * Key: requestId, Value: CompletableFuture<JsonObject>
     */
    private final Map<String, CompletableFuture<JsonObject>> pendingRequests = new ConcurrentHashMap<>();
    
    public BridgeAPI(MiracleBrowser browser) {
        this.browser = browser;
        registerDefaultHandlers();
        LOGGER.info("BridgeAPI initialized");
    }
    
    /**
     * Register default API handlers
     */
    private void registerDefaultHandlers() {
        // ping - for checking if Bridge is available
        register("ping", request -> {
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("message", "pong");
            response.addProperty("timestamp", System.currentTimeMillis());
            return response;
        });
        
        // hud:exitComplete - 前端退出动画完成后调用，隐藏 HUD overlay
        register("hud:exitComplete", request -> {
            LOGGER.info("[BridgeAPI] 收到 hud:exitComplete，隐藏 HUD overlay");
            com.originofmiracles.miraclebridge.browser.BrowserOverlay.getInstance().hide();
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            return response;
        });
        
        // Example: get player info (client-side handling)
        register("getPlayerInfo", request -> {
            JsonObject response = new JsonObject();
            response.addProperty("name", "Player");
            response.addProperty("health", 20);
            response.addProperty("level", 1);
            return response;
        });
        
        // Example: teleport player (requires server-side handling)
        register("teleport", request -> {
            // This handler is a placeholder, actual processing via requestFromServer
            JsonObject response = new JsonObject();
            response.addProperty("error", "Use requestFromServer method instead");
            return response;
        });
        
        LOGGER.info("Registered {} bridge handlers", handlers.size());
    }
    
    /**
     * Register custom API handler
     * @param action action name (e.g. "getInventory")
     * @param handler handler function that takes request JSON and returns response JSON
     */
    public void register(String action, BridgeHandler handler) {
        handlers.put(action, handler);
        LOGGER.debug("Registered bridge handler: {}", action);
    }
    
    /**
     * Handle request from JavaScript (local processing)
     * @param action action name
     * @param requestJson request payload (JSON string)
     * @return response JSON string
     */
    public String handleRequest(String action, String requestJson) {
        // Logging
        if (ClientConfig.shouldLogBridgeRequests()) {
            LOGGER.info("Bridge request: action={}, payload={}", action, requestJson);
        }
        
        try {
            // Check request size
            if (requestJson.length() > ServerConfig.getMaxRequestSize()) {
                LOGGER.warn("Request body too large: {} bytes", requestJson.length());
                return errorResponse("Request body too large");
            }
            
            JsonObject request = GSON.fromJson(requestJson, JsonObject.class);
            JsonObject response = handleRequest(action, request);
            String responseJson = GSON.toJson(response);
            
            if (ClientConfig.shouldLogBridgeRequests()) {
                LOGGER.info("Bridge response: action={}, response={}", action, responseJson);
            }
            
            return responseJson;
            
        } catch (Exception e) {
            LOGGER.error("Error handling bridge request: {}", action, e);
            return errorResponse("Internal error: " + e.getMessage());
        }
    }
    
    /**
     * Handle request from JavaScript (local processing)
     * @param action action name
     * @param request request payload as JsonObject
     * @return response JsonObject
     */
    public JsonObject handleRequest(String action, JsonObject request) {
        // Logging
        if (ClientConfig.shouldLogBridgeRequests()) {
            LOGGER.info("Bridge request: action={}, payload={}", action, GSON.toJson(request));
        }
        
        try {
            BridgeHandler handler = handlers.get(action);
            if (handler == null) {
                LOGGER.warn("Unknown bridge action: {}", action);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Unknown action: " + action);
                return error;
            }
            
            JsonObject response = handler.handle(request);
            
            if (ClientConfig.shouldLogBridgeRequests()) {
                LOGGER.info("Bridge response: action={}, response={}", action, GSON.toJson(response));
            }
            
            return response;
            
        } catch (Exception e) {
            LOGGER.error("Error handling bridge request: {}", action, e);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Internal error: " + e.getMessage());
            return error;
        }
    }
    
    /**
     * Push event to JavaScript
     * @param eventName event name
     * @param data event data (will be serialized to JSON)
     */
    public void pushEvent(String eventName, Object data) {
        if (!browser.isReady()) {
            LOGGER.warn("Cannot push event '{}': browser not ready", eventName);
            return;
        }
        
        String dataJson = GSON.toJson(data);
        String script = String.format(
            "window.dispatchEvent(new CustomEvent('%s', { detail: %s }));",
            eventName, dataJson
        );
        
        // Ensure JS execution on client main thread
        ThreadScheduler.runOnClientThread(() -> {
            browser.executeJavaScript(script);
        });
        
        if (ClientConfig.shouldLogBridgeRequests()) {
            LOGGER.debug("Pushed event: {} data: {}", eventName, dataJson);
        }
    }
    
    /**
     * Create error response JSON
     */
    private String errorResponse(String message) {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        return GSON.toJson(error);
    }
    
    // ==================== Server Requests ====================
    
    /**
     * Send request to server and get response asynchronously
     * 
     * @param action action name
     * @param payload request parameters
     * @return CompletableFuture containing the response
     */
    public CompletableFuture<JsonObject> requestFromServer(String action, JsonObject payload) {
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        
        // 注册等待响应
        pendingRequests.put(requestId, future);
        
        // Set timeout
        int timeout = ServerConfig.getRequestTimeout();
        ThreadScheduler.runLater(() -> {
            CompletableFuture<JsonObject> pending = pendingRequests.remove(requestId);
            if (pending != null && !pending.isDone()) {
                JsonObject error = new JsonObject();
                error.addProperty("error", "Request timeout");
                pending.complete(error);
            }
        }, timeout, java.util.concurrent.TimeUnit.MILLISECONDS);
        
        // Send network packet to server
        String payloadJson = GSON.toJson(payload);
        ModNetworkHandler.sendToServer(new C2SBridgeActionPacket(action, requestId, payloadJson));
        
        if (ClientConfig.shouldLogBridgeRequests()) {
            LOGGER.debug("Sent server request: action={}, requestId={}", action, requestId);
        }
        
        return future;
    }
    
    /**
     * Handle response from server
     * Call when S2CFullSyncPacket (type="bridge_response") is received
     * 
     * @param responseJson response JSON string
     */
    public void handleServerResponse(String responseJson) {
        try {
            JsonObject response = GSON.fromJson(responseJson, JsonObject.class);
            String requestId = response.has("requestId") ? response.get("requestId").getAsString() : null;
            
            if (requestId == null) {
                LOGGER.warn("Received server response without requestId");
                return;
            }
            
            CompletableFuture<JsonObject> pending = pendingRequests.remove(requestId);
            if (pending != null) {
                pending.complete(response);
                if (ClientConfig.shouldLogBridgeRequests()) {
                    LOGGER.debug("Completed server request: requestId={}", requestId);
                }
            } else {
                LOGGER.warn("Received response for unknown requestId: {}", requestId);
            }
            
        } catch (Exception e) {
            LOGGER.error("Failed to parse server response", e);
        }
    }
    
    /**
     * Get browser instance
     */
    public MiracleBrowser getBrowser() {
        return browser;
    }
    
    /**
     * Functional interface for bridge handlers
     */
    @FunctionalInterface
    public interface BridgeHandler extends Function<JsonObject, JsonObject> {
        /**
         * Handle bridge request
         * @param request request JSON object
         * @return response JSON object
         */
        JsonObject handle(JsonObject request);
        
        @Override
        default JsonObject apply(JsonObject request) {
            return handle(request);
        }
    }
}
