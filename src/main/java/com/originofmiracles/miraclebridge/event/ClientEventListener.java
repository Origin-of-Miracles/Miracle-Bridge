package com.originofmiracles.miraclebridge.event;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.originofmiracles.miraclebridge.browser.BrowserManager;
import com.originofmiracles.miraclebridge.browser.MiracleBrowser;
import com.originofmiracles.miraclebridge.bridge.BridgeAPI;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * 客户端事件监听器
 * 
 * 处理仅在客户端发生的事件：
 * - 玩家加入/离开服务器（客户端视角）
 * - 渲染事件
 * - 客户端 Tick
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "miraclebridge", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEventListener {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * 每秒帧率统计
     */
    private static int tickCounter = 0;
    private static long lastTickTime = 0;
    
    // ==================== 网络事件 ====================
    
    /**
     * 客户端加入服务器
     */
    @SubscribeEvent
    public static void onClientJoinServer(ClientPlayerNetworkEvent.LoggingIn event) {
        LOGGER.info("客户端已连接到服务器");
        
        JsonObject data = new JsonObject();
        data.addProperty("event", "connected");
        data.addProperty("playerName", event.getPlayer().getName().getString());
        
        pushToLocalBrowsers("client:connected", data);
    }
    
    /**
     * 客户端离开服务器
     */
    @SubscribeEvent
    public static void onClientLeaveServer(ClientPlayerNetworkEvent.LoggingOut event) {
        LOGGER.info("客户端已断开服务器连接");
        
        JsonObject data = new JsonObject();
        data.addProperty("event", "disconnected");
        
        pushToLocalBrowsers("client:disconnected", data);
    }
    
    // ==================== Tick 事件 ====================
    
    /**
     * 客户端 Tick（每游戏 tick 执行）
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        
        tickCounter++;
        
        // 每秒发送一次状态更新
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastTickTime >= 1000) {
            lastTickTime = currentTime;
            
            // 可选：发送心跳/状态事件
            // pushHeartbeat();
            
            tickCounter = 0;
        }
    }
    
    /**
     * 渲染世界阶段事件
     */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        // 可用于在特定渲染阶段执行操作
        // 例如：在 AFTER_WEATHER 阶段渲染自定义内容
    }
    
    // ==================== 辅助方法 ====================
    
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
    
    /**
     * 发送心跳事件（可选）
     */
    @SuppressWarnings("unused")
    private static void pushHeartbeat() {
        JsonObject data = new JsonObject();
        data.addProperty("timestamp", System.currentTimeMillis());
        data.addProperty("tps", tickCounter);
        
        pushToLocalBrowsers("client:heartbeat", data);
    }
}
