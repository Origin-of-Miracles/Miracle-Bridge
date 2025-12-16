package com.originofmiracles.miraclebridge.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.originofmiracles.miraclebridge.bridge.BridgeAPI;
import com.originofmiracles.miraclebridge.browser.BrowserManager;
import com.originofmiracles.miraclebridge.browser.MiracleBrowser;
import com.originofmiracles.miraclebridge.config.ClientConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端 全量同步数据包
 * 
 * 用于将服务端状态同步到客户端，采用全量同步策略。
 * 
 * 数据格式：
 * - dataType: 数据类型标识符（如 "entity_state", "game_event"）
 * - jsonData: JSON 格式的数据内容
 */
public class S2CFullSyncPacket {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * 最大数据长度（字节）
     */
    private static final int MAX_DATA_LENGTH = 1048576; // 1MB
    
    private final String dataType;
    private final String jsonData;
    
    /**
     * 创建全量同步数据包
     * 
     * @param dataType 数据类型标识符
     * @param jsonData JSON 格式的数据内容
     */
    public S2CFullSyncPacket(String dataType, String jsonData) {
        this.dataType = dataType;
        this.jsonData = jsonData;
    }
    
    /**
     * 编码数据包到网络缓冲区
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(dataType, 256);
        buf.writeUtf(jsonData, MAX_DATA_LENGTH);
    }
    
    /**
     * 从网络缓冲区解码数据包
     */
    public static S2CFullSyncPacket decode(FriendlyByteBuf buf) {
        String dataType = buf.readUtf(256);
        String jsonData = buf.readUtf(MAX_DATA_LENGTH);
        return new S2CFullSyncPacket(dataType, jsonData);
    }
    
    /**
     * 处理接收到的数据包（客户端）
     */
    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        // 确保在客户端主线程处理
        ctx.enqueueWork(() -> {
            try {
                handleSync();
            } catch (Exception e) {
                LOGGER.error("处理同步数据失败: type={}", dataType, e);
            }
        });
        ctx.setPacketHandled(true);
    }
    
    /**
     * 实际处理同步数据的逻辑
     */
    private void handleSync() {
        if (ClientConfig.isDebugEnabled()) {
            LOGGER.debug("收到同步数据: type={}, size={} bytes", dataType, jsonData.length());
        }
        
        // 根据数据类型分发处理
        switch (dataType) {
            case "entity_state" -> handleEntityStateSync();
            case "game_event" -> handleGameEventSync();
            case "browser_command" -> handleBrowserCommandSync();
            case "bridge_response" -> handleBridgeResponse();
            default -> {
                if (ClientConfig.isDebugEnabled()) {
                    LOGGER.warn("未知的同步数据类型: {}", dataType);
                }
            }
        }
    }
    
    /**
     * 处理实体状态同步
     */
    private void handleEntityStateSync() {
        if (ClientConfig.isDebugEnabled()) {
            LOGGER.debug("实体状态同步: {}", jsonData);
        }
        
        // 解析实体状态数据
        try {
            Gson gson = new Gson();
            JsonObject data = gson.fromJson(jsonData, JsonObject.class);
            
            // 推送到所有浏览器
            pushEventToAllBrowsers("entity:stateUpdate", data);
            
        } catch (Exception e) {
            LOGGER.error("解析实体状态数据失败", e);
        }
    }
    
    /**
     * 处理游戏事件同步
     */
    private void handleGameEventSync() {
        if (ClientConfig.isDebugEnabled()) {
            LOGGER.debug("游戏事件同步: {}", jsonData);
        }
        
        // 解析游戏事件数据
        try {
            Gson gson = new Gson();
            JsonObject data = gson.fromJson(jsonData, JsonObject.class);
            
            // 获取事件名称
            String eventName = data.has("eventName") ? data.get("eventName").getAsString() : "unknown";
            
            // 推送到所有浏览器
            pushEventToAllBrowsers("game:" + eventName, data);
            
        } catch (Exception e) {
            LOGGER.error("解析游戏事件数据失败", e);
        }
    }
    
    /**
     * 处理浏览器命令同步
     */
    private void handleBrowserCommandSync() {
        if (ClientConfig.isDebugEnabled()) {
            LOGGER.debug("浏览器命令同步: {}", jsonData);
        }
        
        // 解析浏览器命令
        try {
            Gson gson = new Gson();
            JsonObject data = gson.fromJson(jsonData, JsonObject.class);
            
            String command = data.has("command") ? data.get("command").getAsString() : "";
            String browserName = data.has("browser") ? data.get("browser").getAsString() : "main";
            
            MiracleBrowser browser = BrowserManager.getInstance().getBrowser(browserName);
            if (browser == null) {
                LOGGER.warn("浏览器不存在: {}", browserName);
                return;
            }
            
            switch (command) {
                case "navigate" -> {
                    String url = data.has("url") ? data.get("url").getAsString() : "";
                    if (!url.isEmpty()) {
                        browser.loadUrl(url);
                    }
                }
                case "executeJs" -> {
                    String script = data.has("script") ? data.get("script").getAsString() : "";
                    if (!script.isEmpty()) {
                        browser.executeJavaScript(script);
                    }
                }
                case "resize" -> {
                    int width = data.has("width") ? data.get("width").getAsInt() : 0;
                    int height = data.has("height") ? data.get("height").getAsInt() : 0;
                    if (width > 0 && height > 0) {
                        browser.resize(width, height);
                    }
                }
                case "close" -> BrowserManager.getInstance().closeBrowser(browserName);
                default -> LOGGER.warn("未知的浏览器命令: {}", command);
            }
            
        } catch (Exception e) {
            LOGGER.error("解析浏览器命令失败", e);
        }
    }
    
    /**
     * 处理桥接响应
     */
    private void handleBridgeResponse() {
        if (ClientConfig.isDebugEnabled()) {
            LOGGER.debug("桥接响应: {}", jsonData);
        }
        
        // 查找所有浏览器并传递响应
        for (String browserName : BrowserManager.getInstance().getBrowserNames()) {
            MiracleBrowser browser = BrowserManager.getInstance().getBrowser(browserName);
            if (browser != null) {
                BridgeAPI bridgeAPI = browser.getBridgeAPI();
                if (bridgeAPI != null) {
                    bridgeAPI.handleServerResponse(jsonData);
                }
            }
        }
    }
    
    /**
     * 向所有浏览器推送事件
     */
    private void pushEventToAllBrowsers(String eventName, JsonObject data) {
        for (String browserName : BrowserManager.getInstance().getBrowserNames()) {
            MiracleBrowser browser = BrowserManager.getInstance().getBrowser(browserName);
            if (browser != null && browser.isReady()) {
                BridgeAPI bridgeAPI = browser.getBridgeAPI();
                if (bridgeAPI != null) {
                    bridgeAPI.pushEvent(eventName, data);
                }
            }
        }
    }
    
    // ==================== Getter ====================
    
    public String getDataType() {
        return dataType;
    }
    
    public String getJsonData() {
        return jsonData;
    }
}
