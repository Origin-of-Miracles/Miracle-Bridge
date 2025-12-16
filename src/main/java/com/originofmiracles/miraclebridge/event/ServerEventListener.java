package com.originofmiracles.miraclebridge.event;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.originofmiracles.miraclebridge.network.ModNetworkHandler;
import com.originofmiracles.miraclebridge.network.S2CEventPushPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * 服务端事件监听器
 * 
 * 处理服务端特有的事件：
 * - 世界加载/卸载
 * - 方块变化
 * - 服务器 Tick
 */
@Mod.EventBusSubscriber(modid = "miraclebridge", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerEventListener {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * 方块事件开关
     */
    private static boolean blockEventsEnabled = false; // 默认关闭，事件量大
    
    // ==================== 世界事件 ====================
    
    /**
     * 世界加载
     */
    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        if (event.getLevel().isClientSide()) return;
        
        Level level = (Level) event.getLevel();
        
        JsonObject data = new JsonObject();
        data.addProperty("dimension", level.dimension().location().toString());
        data.addProperty("event", "load");
        
        LOGGER.debug("世界加载: {}", level.dimension().location());
        
        // 广播事件
        broadcastEvent("world:load", data);
    }
    
    /**
     * 世界卸载
     */
    @SubscribeEvent
    public static void onWorldUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) return;
        
        Level level = (Level) event.getLevel();
        
        JsonObject data = new JsonObject();
        data.addProperty("dimension", level.dimension().location().toString());
        data.addProperty("event", "unload");
        
        LOGGER.debug("世界卸载: {}", level.dimension().location());
        
        broadcastEvent("world:unload", data);
    }
    
    // ==================== 方块事件 ====================
    
    /**
     * 方块放置
     */
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!blockEventsEnabled) return;
        if (event.getLevel().isClientSide()) return;
        
        BlockPos pos = event.getPos();
        
        JsonObject data = new JsonObject();
        data.addProperty("x", pos.getX());
        data.addProperty("y", pos.getY());
        data.addProperty("z", pos.getZ());
        data.addProperty("block", event.getPlacedBlock().getBlock().toString());
        data.addProperty("event", "place");
        
        if (event.getEntity() instanceof ServerPlayer player) {
            data.addProperty("playerName", player.getName().getString());
            data.addProperty("playerUuid", player.getUUID().toString());
        }
        
        broadcastEvent("block:place", data);
    }
    
    /**
     * 方块破坏
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!blockEventsEnabled) return;
        if (event.getLevel().isClientSide()) return;
        
        BlockPos pos = event.getPos();
        
        JsonObject data = new JsonObject();
        data.addProperty("x", pos.getX());
        data.addProperty("y", pos.getY());
        data.addProperty("z", pos.getZ());
        data.addProperty("block", event.getState().getBlock().toString());
        data.addProperty("event", "break");
        data.addProperty("playerName", event.getPlayer().getName().getString());
        data.addProperty("playerUuid", event.getPlayer().getUUID().toString());
        
        broadcastEvent("block:break", data);
    }
    
    // ==================== Tick 事件 ====================
    
    /**
     * 服务器 Tick
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        
        // 可用于定期任务
        // 例如：每 20 tick (1秒) 执行一次状态同步
    }
    
    /**
     * 世界 Tick
     */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.level.isClientSide()) return;
        
        // 可用于维度特定的定期任务
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 广播事件到所有客户端
     */
    private static void broadcastEvent(String eventName, JsonObject data) {
        String eventType = eventName.contains(":") ? eventName.split(":")[0] : "general";
        S2CEventPushPacket packet = S2CEventPushPacket.create(eventType, eventName, data);
        ModNetworkHandler.sendToAllPlayers(packet);
    }
    
    // ==================== 配置 ====================
    
    /**
     * 设置方块事件开关
     */
    public static void setBlockEventsEnabled(boolean enabled) {
        blockEventsEnabled = enabled;
        LOGGER.info("方块事件: {}", enabled ? "启用" : "禁用");
    }
    
    /**
     * 检查方块事件是否启用
     */
    public static boolean isBlockEventsEnabled() {
        return blockEventsEnabled;
    }
}
