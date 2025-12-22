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
 * Server → Client Event Push Packet
 * 
 * Used for server to actively push game events to client.
 * Client forwards events to JavaScript frontend via BridgeAPI.
 * 
 * Event types:
 * - entity_spawn: Entity spawned
 * - entity_death: Entity died
 * - block_change: Block changed
 * - chat_message: Chat message
 * - player_join: Player joined
 * - player_leave: Player left
 * - custom: Custom event
 */
public class S2CEventPushPacket {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    
    /**
     * Maximum data length (bytes)
     */
    private static final int MAX_DATA_LENGTH = 65536; // 64KB
    
    private final String eventType;
    private final String eventName;
    private final String jsonData;
    
    /**
     * Create event push packet
     * 
     * @param eventType event type category (e.g. "entity", "block", "chat")
     * @param eventName specific event name (e.g. "spawn", "death", "message")
     * @param jsonData JSON format event data
     */
    public S2CEventPushPacket(String eventType, String eventName, String jsonData) {
        this.eventType = eventType;
        this.eventName = eventName;
        this.jsonData = jsonData;
    }
    
    /**
     * Convenience constructor: create from JsonObject
     */
    public static S2CEventPushPacket create(String eventType, String eventName, JsonObject data) {
        return new S2CEventPushPacket(eventType, eventName, GSON.toJson(data));
    }
    
    /**
     * Encode packet to network buffer
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(eventType, 64);
        buf.writeUtf(eventName, 128);
        buf.writeUtf(jsonData, MAX_DATA_LENGTH);
    }
    
    /**
     * Decode packet from network buffer
     */
    public static S2CEventPushPacket decode(FriendlyByteBuf buf) {
        String eventType = buf.readUtf(64);
        String eventName = buf.readUtf(128);
        String jsonData = buf.readUtf(MAX_DATA_LENGTH);
        return new S2CEventPushPacket(eventType, eventName, jsonData);
    }
    
    /**
     * Handle received packet (client side)
     */
    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            try {
                handleEvent();
            } catch (Exception e) {
                LOGGER.error("Failed to handle event push: type={}, name={}", eventType, eventName, e);
            }
        });
        ctx.setPacketHandled(true);
    }
    
    /**
     * Actual event handling logic
     */
    private void handleEvent() {
        if (ClientConfig.isDebugEnabled()) {
            LOGGER.debug("Received event push: type={}, name={}, size={} bytes", 
                    eventType, eventName, jsonData.length());
        }
        
        // Construct full event name
        String fullEventName = eventType + ":" + eventName;
        
        // Parse data
        JsonObject data;
        try {
            data = GSON.fromJson(jsonData, JsonObject.class);
        } catch (Exception e) {
            LOGGER.error("Failed to parse event data: {}", jsonData, e);
            return;
        }
        
        // Push event to all browsers
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
            LOGGER.debug("Event pushed to {} browsers", BrowserManager.getInstance().getBrowserCount());
        }
    }
    
    // ==================== Convenience Factory Methods ====================
    
    /**
     * Create entity spawn event
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
     * Create entity death event
     */
    public static S2CEventPushPacket entityDeath(int entityId, String entityType, String cause) {
        JsonObject data = new JsonObject();
        data.addProperty("entityId", entityId);
        data.addProperty("entityType", entityType);
        data.addProperty("cause", cause);
        return create("entity", "death", data);
    }
    
    /**
     * Create chat message event
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
     * Create player join event
     */
    public static S2CEventPushPacket playerJoin(String playerName, String uuid) {
        JsonObject data = new JsonObject();
        data.addProperty("playerName", playerName);
        data.addProperty("uuid", uuid);
        data.addProperty("timestamp", System.currentTimeMillis());
        return create("player", "join", data);
    }
    
    /**
     * Create player leave event
     */
    public static S2CEventPushPacket playerLeave(String playerName, String uuid) {
        JsonObject data = new JsonObject();
        data.addProperty("playerName", playerName);
        data.addProperty("uuid", uuid);
        data.addProperty("timestamp", System.currentTimeMillis());
        return create("player", "leave", data);
    }
    
    /**
     * Create custom event
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
     * Get full event name
     */
    public String getFullEventName() {
        return eventType + ":" + eventName;
    }
}
