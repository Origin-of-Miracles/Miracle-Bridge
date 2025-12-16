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
 * 服务端 → 客户端 事件推送数据包
 * 
 * 用于服务端主动向客户端推送游戏事件。
 * 客户端收到后通过 BridgeAPI 转发给 JavaScript 前端。
 * 
 * 事件类型：
 * - entity_spawn: 实体生成
 * - entity_death: 实体死亡
 * - block_change: 方块变化
 * - chat_message: 聊天消息
 * - player_join: 玩家加入
 * - player_leave: 玩家离开
 * - custom: 自定义事件
 */
public class S2CEventPushPacket {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    
    /**
     * 最大数据长度（字节）
     */
    private static final int MAX_DATA_LENGTH = 65536; // 64KB
    
    private final String eventType;
    private final String eventName;
    private final String jsonData;
    
    /**
     * 创建事件推送数据包
     * 
     * @param eventType 事件类型分类（如 "entity", "block", "chat"）
     * @param eventName 具体事件名称（如 "spawn", "death", "message"）
     * @param jsonData JSON 格式的事件数据
     */
    public S2CEventPushPacket(String eventType, String eventName, String jsonData) {
        this.eventType = eventType;
        this.eventName = eventName;
        this.jsonData = jsonData;
    }
    
    /**
     * 便捷构造函数：从 JsonObject 创建
     */
    public static S2CEventPushPacket create(String eventType, String eventName, JsonObject data) {
        return new S2CEventPushPacket(eventType, eventName, GSON.toJson(data));
    }
    
    /**
     * 编码数据包到网络缓冲区
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(eventType, 64);
        buf.writeUtf(eventName, 128);
        buf.writeUtf(jsonData, MAX_DATA_LENGTH);
    }
    
    /**
     * 从网络缓冲区解码数据包
     */
    public static S2CEventPushPacket decode(FriendlyByteBuf buf) {
        String eventType = buf.readUtf(64);
        String eventName = buf.readUtf(128);
        String jsonData = buf.readUtf(MAX_DATA_LENGTH);
        return new S2CEventPushPacket(eventType, eventName, jsonData);
    }
    
    /**
     * 处理接收到的数据包（客户端）
     */
    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            try {
                handleEvent();
            } catch (Exception e) {
                LOGGER.error("处理事件推送失败: type={}, name={}", eventType, eventName, e);
            }
        });
        ctx.setPacketHandled(true);
    }
    
    /**
     * 实际处理事件的逻辑
     */
    private void handleEvent() {
        if (ClientConfig.isDebugEnabled()) {
            LOGGER.debug("收到事件推送: type={}, name={}, size={} bytes", 
                    eventType, eventName, jsonData.length());
        }
        
        // 构造完整的事件名称
        String fullEventName = eventType + ":" + eventName;
        
        // 解析数据
        JsonObject data;
        try {
            data = GSON.fromJson(jsonData, JsonObject.class);
        } catch (Exception e) {
            LOGGER.error("解析事件数据失败: {}", jsonData, e);
            return;
        }
        
        // 向所有浏览器推送事件
        for (String browserName : BrowserManager.getInstance().getBrowserNames()) {
            MiracleBrowser browser = BrowserManager.getInstance().getBrowser(browserName);
            if (browser != null && browser.isReady()) {
                BridgeAPI bridgeAPI = browser.getBridgeAPI();
                if (bridgeAPI != null) {
                    bridgeAPI.pushEvent(fullEventName, data);
                }
            }
        }
        
        if (ClientConfig.isDebugEnabled()) {
            LOGGER.debug("事件已推送到 {} 个浏览器", BrowserManager.getInstance().getBrowserCount());
        }
    }
    
    // ==================== 便捷工厂方法 ====================
    
    /**
     * 创建实体生成事件
     */
    public static S2CEventPushPacket entitySpawn(int entityId, String entityType, double x, double y, double z) {
        JsonObject data = new JsonObject();
        data.addProperty("entityId", entityId);
        data.addProperty("entityType", entityType);
        data.addProperty("x", x);
        data.addProperty("y", y);
        data.addProperty("z", z);
        return create("entity", "spawn", data);
    }
    
    /**
     * 创建实体死亡事件
     */
    public static S2CEventPushPacket entityDeath(int entityId, String entityType, String cause) {
        JsonObject data = new JsonObject();
        data.addProperty("entityId", entityId);
        data.addProperty("entityType", entityType);
        data.addProperty("cause", cause);
        return create("entity", "death", data);
    }
    
    /**
     * 创建聊天消息事件
     */
    public static S2CEventPushPacket chatMessage(String sender, String message, boolean isSystem) {
        JsonObject data = new JsonObject();
        data.addProperty("sender", sender);
        data.addProperty("message", message);
        data.addProperty("isSystem", isSystem);
        data.addProperty("timestamp", System.currentTimeMillis());
        return create("chat", "message", data);
    }
    
    /**
     * 创建玩家加入事件
     */
    public static S2CEventPushPacket playerJoin(String playerName, String uuid) {
        JsonObject data = new JsonObject();
        data.addProperty("playerName", playerName);
        data.addProperty("uuid", uuid);
        data.addProperty("timestamp", System.currentTimeMillis());
        return create("player", "join", data);
    }
    
    /**
     * 创建玩家离开事件
     */
    public static S2CEventPushPacket playerLeave(String playerName, String uuid) {
        JsonObject data = new JsonObject();
        data.addProperty("playerName", playerName);
        data.addProperty("uuid", uuid);
        data.addProperty("timestamp", System.currentTimeMillis());
        return create("player", "leave", data);
    }
    
    /**
     * 创建自定义事件
     */
    public static S2CEventPushPacket custom(String eventName, JsonObject data) {
        return create("custom", eventName, data);
    }
    
    // ==================== Getter ====================
    
    public String getEventType() {
        return eventType;
    }
    
    public String getEventName() {
        return eventName;
    }
    
    public String getJsonData() {
        return jsonData;
    }
    
    /**
     * 获取完整事件名称
     */
    public String getFullEventName() {
        return eventType + ":" + eventName;
    }
}
