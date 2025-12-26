package com.originofmiracles.miraclebridge.network;

import java.util.function.Supplier;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.network.NetworkEvent;

/**
 * 通用事件接收包 (Client -> Server)
 * 
 * 用于前端发送事件到服务端。服务端收到后会发布 Forge 事件，
 * 其他模组可以监听并处理。
 */
public class C2SSendEventPacket {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /** 事件类型，如 "channel:sendMessage" */
    private final String eventType;
    
    /** JSON 格式的事件数据 */
    private final String jsonData;
    
    public C2SSendEventPacket(String eventType, String jsonData) {
        this.eventType = eventType;
        this.jsonData = jsonData;
    }
    
    // ==================== 编解码 ====================
    
    public static void encode(C2SSendEventPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.eventType, 256);
        buf.writeUtf(packet.jsonData, 65536);
    }
    
    public static C2SSendEventPacket decode(FriendlyByteBuf buf) {
        String eventType = buf.readUtf(256);
        String jsonData = buf.readUtf(65536);
        return new C2SSendEventPacket(eventType, jsonData);
    }
    
    // ==================== 处理（服务端） ====================
    
    public static void handle(C2SSendEventPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                LOGGER.debug("收到客户端事件: {} from {} ({}字节)", 
                    packet.eventType, player.getName().getString(), packet.jsonData.length());
                
                // 发布 Forge 事件，让其他模组处理
                ClientEventReceived event = new ClientEventReceived(
                    player, packet.eventType, packet.jsonData);
                MinecraftForge.EVENT_BUS.post(event);
            }
        });
        ctx.get().setPacketHandled(true);
    }
    
    // ==================== Getters ====================
    
    public String getEventType() {
        return eventType;
    }
    
    public String getJsonData() {
        return jsonData;
    }
    
    // ==================== 服务端事件 ====================
    
    /**
     * 客户端事件接收 Forge 事件
     * 
     * 其他模组可以监听此事件来处理来自前端的请求
     */
    public static class ClientEventReceived extends Event {
        private final ServerPlayer player;
        private final String eventType;
        private final String jsonData;
        
        public ClientEventReceived(ServerPlayer player, String eventType, String jsonData) {
            this.player = player;
            this.eventType = eventType;
            this.jsonData = jsonData;
        }
        
        public ServerPlayer getPlayer() {
            return player;
        }
        
        public String getEventType() {
            return eventType;
        }
        
        public String getJsonData() {
            return jsonData;
        }
    }
}
