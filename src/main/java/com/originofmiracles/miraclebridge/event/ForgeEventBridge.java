package com.originofmiracles.miraclebridge.event;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.originofmiracles.miraclebridge.browser.BrowserManager;
import com.originofmiracles.miraclebridge.browser.MiracleBrowser;
import com.originofmiracles.miraclebridge.bridge.BridgeAPI;
import com.originofmiracles.miraclebridge.network.ModNetworkHandler;
import com.originofmiracles.miraclebridge.network.S2CEventPushPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Forge 事件桥接器
 * 
 * 监听 Minecraft 游戏事件并转发到浏览器/JS：
 * - 玩家事件（加入、离开、死亡、重生）
 * - 实体事件（生成、死亡）
 * - 聊天事件
 * - 世界事件
 * 
 * 事件通过两个通道传递：
 * 1. 客户端本地：直接推送到 BridgeAPI
 * 2. 网络广播：通过 S2CEventPushPacket 发送给所有玩家
 */
@Mod.EventBusSubscriber(modid = "miraclebridge", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeEventBridge {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * 事件开关（可通过配置控制）
     */
    private static boolean entityEventsEnabled = true;
    private static boolean playerEventsEnabled = true;
    private static boolean chatEventsEnabled = true;
    
    // ==================== 玩家事件 ====================
    
    /**
     * 玩家加入服务器
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!playerEventsEnabled) return;
        
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        
        JsonObject data = new JsonObject();
        data.addProperty("playerName", player.getName().getString());
        data.addProperty("uuid", player.getUUID().toString());
        data.addProperty("x", player.getX());
        data.addProperty("y", player.getY());
        data.addProperty("z", player.getZ());
        data.addProperty("dimension", player.level().dimension().location().toString());
        
        broadcastEvent("player:join", data);
        LOGGER.debug("玩家加入事件: {}", player.getName().getString());
    }
    
    /**
     * 玩家离开服务器
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!playerEventsEnabled) return;
        
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        
        JsonObject data = new JsonObject();
        data.addProperty("playerName", player.getName().getString());
        data.addProperty("uuid", player.getUUID().toString());
        
        broadcastEvent("player:leave", data);
        LOGGER.debug("玩家离开事件: {}", player.getName().getString());
    }
    
    /**
     * 玩家重生
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!playerEventsEnabled) return;
        
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        
        JsonObject data = new JsonObject();
        data.addProperty("playerName", player.getName().getString());
        data.addProperty("uuid", player.getUUID().toString());
        data.addProperty("x", player.getX());
        data.addProperty("y", player.getY());
        data.addProperty("z", player.getZ());
        data.addProperty("isEndConquered", event.isEndConquered());
        
        broadcastEvent("player:respawn", data);
        LOGGER.debug("玩家重生事件: {}", player.getName().getString());
    }
    
    /**
     * 玩家死亡
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!playerEventsEnabled) return;
        
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        
        JsonObject data = new JsonObject();
        data.addProperty("playerName", player.getName().getString());
        data.addProperty("uuid", player.getUUID().toString());
        data.addProperty("x", player.getX());
        data.addProperty("y", player.getY());
        data.addProperty("z", player.getZ());
        
        // 死亡来源
        if (event.getSource() != null) {
            data.addProperty("deathSource", event.getSource().type().msgId());
            Entity killer = event.getSource().getEntity();
            if (killer != null) {
                data.addProperty("killerType", BuiltInRegistries.ENTITY_TYPE.getKey(killer.getType()).toString());
                if (killer instanceof Player killerPlayer) {
                    data.addProperty("killerName", killerPlayer.getName().getString());
                }
            }
        }
        
        broadcastEvent("player:death", data);
        LOGGER.debug("玩家死亡事件: {}", player.getName().getString());
    }
    
    // ==================== 实体事件 ====================
    
    /**
     * 实体加入世界
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!entityEventsEnabled) return;
        
        Entity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        
        // 忽略玩家（由单独的事件处理）
        if (entity instanceof Player) return;
        
        // 只处理生物实体，避免过多事件
        if (!(entity instanceof LivingEntity)) return;
        
        JsonObject data = serializeEntity(entity);
        data.addProperty("eventType", "spawn");
        
        broadcastEvent("entity:spawn", data);
    }
    
    /**
     * 生物死亡
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onEntityDeath(LivingDeathEvent event) {
        if (!entityEventsEnabled) return;
        
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        
        // 玩家死亡由单独的处理逻辑
        if (entity instanceof Player) return;
        
        JsonObject data = serializeEntity(entity);
        data.addProperty("eventType", "death");
        
        // 死亡来源
        if (event.getSource() != null) {
            data.addProperty("deathSource", event.getSource().type().msgId());
            Entity killer = event.getSource().getEntity();
            if (killer != null) {
                data.addProperty("killerType", BuiltInRegistries.ENTITY_TYPE.getKey(killer.getType()).toString());
                if (killer instanceof Player killerPlayer) {
                    data.addProperty("killerName", killerPlayer.getName().getString());
                }
            }
        }
        
        broadcastEvent("entity:death", data);
    }
    
    // ==================== 聊天事件 ====================
    
    /**
     * 服务器聊天消息
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onServerChat(ServerChatEvent event) {
        if (!chatEventsEnabled) return;
        
        ServerPlayer player = event.getPlayer();
        String message = event.getRawText();
        
        JsonObject data = new JsonObject();
        data.addProperty("playerName", player.getName().getString());
        data.addProperty("uuid", player.getUUID().toString());
        data.addProperty("message", message);
        data.addProperty("timestamp", System.currentTimeMillis());
        
        broadcastEvent("chat:message", data);
        LOGGER.debug("聊天事件: {} -> {}", player.getName().getString(), message);
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 序列化实体为 JSON
     */
    private static JsonObject serializeEntity(Entity entity) {
        JsonObject data = new JsonObject();
        data.addProperty("entityId", entity.getId());
        data.addProperty("uuid", entity.getUUID().toString());
        data.addProperty("type", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        data.addProperty("x", entity.getX());
        data.addProperty("y", entity.getY());
        data.addProperty("z", entity.getZ());
        data.addProperty("dimension", entity.level().dimension().location().toString());
        
        if (entity instanceof LivingEntity living) {
            data.addProperty("health", living.getHealth());
            data.addProperty("maxHealth", living.getMaxHealth());
        }
        
        if (entity.hasCustomName()) {
            data.addProperty("customName", entity.getCustomName().getString());
        }
        
        return data;
    }
    
    /**
     * 广播事件到所有客户端
     */
    private static void broadcastEvent(String eventName, JsonObject data) {
        // 解析事件类型（从 eventName 中提取，如 "player:join" -> "player"）
        String eventType = eventName.contains(":") ? eventName.split(":")[0] : "general";
        
        // 1. 通过网络发送到所有客户端
        S2CEventPushPacket packet = S2CEventPushPacket.create(eventType, eventName, data);
        ModNetworkHandler.sendToAllPlayers(packet);
        
        // 2. 如果是单人游戏，也推送到本地浏览器
        pushToLocalBrowsers(eventName, data);
    }
    
    /**
     * 推送事件到本地浏览器
     */
    private static void pushToLocalBrowsers(String eventName, JsonObject data) {
        try {
            BrowserManager manager = BrowserManager.getInstance();
            if (manager == null) return;
        
            for (String browserName : manager.getBrowserNames()) {
                MiracleBrowser browser = manager.getBrowser(browserName);
                if (browser != null && browser.isReady()) {
                    BridgeAPI bridgeAPI = browser.getBridgeAPI();
                    if (bridgeAPI != null) {
                        bridgeAPI.pushEvent(eventName, data);
                    }
                }
            }
        } catch (Exception e) {
            // 忽略浏览器不可用的情况
        }
    }
    
    // ==================== 配置 ====================
    
    /**
     * 设置实体事件开关
     */
    public static void setEntityEventsEnabled(boolean enabled) {
        entityEventsEnabled = enabled;
        LOGGER.info("实体事件: {}", enabled ? "启用" : "禁用");
    }
    
    /**
     * 设置玩家事件开关
     */
    public static void setPlayerEventsEnabled(boolean enabled) {
        playerEventsEnabled = enabled;
        LOGGER.info("玩家事件: {}", enabled ? "启用" : "禁用");
    }
    
    /**
     * 设置聊天事件开关
     */
    public static void setChatEventsEnabled(boolean enabled) {
        chatEventsEnabled = enabled;
        LOGGER.info("聊天事件: {}", enabled ? "启用" : "禁用");
    }
    
    /**
     * 检查实体事件是否启用
     */
    public static boolean isEntityEventsEnabled() {
        return entityEventsEnabled;
    }
    
    /**
     * 检查玩家事件是否启用
     */
    public static boolean isPlayerEventsEnabled() {
        return playerEventsEnabled;
    }
    
    /**
     * 检查聊天事件是否启用
     */
    public static boolean isChatEventsEnabled() {
        return chatEventsEnabled;
    }
}
