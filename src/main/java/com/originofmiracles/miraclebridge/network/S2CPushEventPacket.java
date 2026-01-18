package com.originofmiracles.miraclebridge.network;

import java.util.function.Supplier;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.originofmiracles.miraclebridge.browser.BrowserOverlay;
import com.originofmiracles.miraclebridge.event.BridgeEventBus;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * 通用事件推送包 (Server -> Client)
 * 
 * 用于将任意事件从服务端推送到客户端前端。
 * 这是 Miracle-Bridge 提供的核心 API，其他模组可以通过
 * BridgeEventBus.pushToClient() 来发送事件。
 */
public class S2CPushEventPacket {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /** 事件类型，如 "channel:joined", "momotalk:message" */
    private final String eventType;
    
    /** JSON 格式的事件数据 */
    private final String jsonData;
    
    public S2CPushEventPacket(String eventType, String jsonData) {
        this.eventType = eventType;
        this.jsonData = jsonData;
    }
    
    // ==================== 编解码 ====================
    
    public static void encode(S2CPushEventPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.eventType, 256);       // 事件类型最长 256 字符
        buf.writeUtf(packet.jsonData, 65536);      // JSON 数据最长 64KB
    }
    
    public static S2CPushEventPacket decode(FriendlyByteBuf buf) {
        String eventType = buf.readUtf(256);
        String jsonData = buf.readUtf(65536);
        return new S2CPushEventPacket(eventType, jsonData);
    }
    
    // ==================== 处理（客户端） ====================
    
    public static void handle(S2CPushEventPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 仅在客户端执行
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                handleOnClient(packet);
            });
        });
        ctx.get().setPacketHandled(true);
    }
    
    private static void handleOnClient(S2CPushEventPacket packet) {
        LOGGER.info("[S2CPushEvent] 收到服务端事件: {} ({}字节)", packet.eventType, packet.jsonData.length());
        
        BrowserOverlay overlay = BrowserOverlay.getInstance();
        
        // 如果是频道加入事件，自动显示浏览器覆盖层并推送事件到 HUD
        if ("channel:joined".equals(packet.eventType)) {
            LOGGER.info("[S2CPushEvent] 检测到 channel:joined 事件，自动显示 HUD 覆盖层");
            overlay.show();
            overlay.pushEvent(packet.eventType, packet.jsonData);
        }
        // 如果是频道离开/关闭事件，触发前端退出动画
        else if ("channel:closed".equals(packet.eventType) || "channel:left".equals(packet.eventType)) {
            LOGGER.info("[S2CPushEvent] 检测到频道关闭事件，触发前端退出动画");
            overlay.pushEvent(packet.eventType, packet.jsonData);
        }
        // 其他所有频道相关事件也推送到 HUD
        else if (packet.eventType.startsWith("channel:")) {
            LOGGER.debug("[S2CPushEvent] 推送频道事件到 HUD: {}", packet.eventType);
            overlay.pushEvent(packet.eventType, packet.jsonData);
        }
        
        // 推送到主浏览器（BrowserScreen）
        BridgeEventBus.pushEventToFrontend(packet.eventType, packet.jsonData);
    }
    
    // ==================== Getters ====================
    
    public String getEventType() {
        return eventType;
    }
    
    public String getJsonData() {
        return jsonData;
    }
}
