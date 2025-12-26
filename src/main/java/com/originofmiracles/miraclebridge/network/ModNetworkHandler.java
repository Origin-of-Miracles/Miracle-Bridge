package com.originofmiracles.miraclebridge.network;

import com.mojang.logging.LogUtils;
import com.originofmiracles.miraclebridge.MiracleBridge;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;

/**
 * 网络数据包处理器
 * 
 * 基于 Forge SimpleChannel 实现客户端 ↔ 服务端通信。
 * 
 * 数据包类型：
 * - S2C (Server to Client): 服务端推送到客户端
 * - C2S (Client to Server): 客户端请求到服务端
 * 
 * 同步策略：全量同步
 */
public class ModNetworkHandler {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * 网络协议版本
     * 修改数据包结构时需要更新此版本号
     */
    private static final String PROTOCOL_VERSION = "1";
    
    /**
     * 网络通道
     */
    private static SimpleChannel CHANNEL;
    
    /**
     * 数据包 ID 计数器
     */
    private static int packetId = 0;
    
    /**
     * 初始化网络通道并注册所有数据包
     * 应在 commonSetup 中调用
     */
    public static void register() {
        LOGGER.info("正在注册网络通道...");
        
        CHANNEL = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(MiracleBridge.MOD_ID, "main"),
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        );
        
        // 注册数据包
        registerPackets();
        
        LOGGER.info("网络通道注册完成，协议版本: {}", PROTOCOL_VERSION);
    }
    
    /**
     * 注册所有数据包类型
     */
    private static void registerPackets() {
        // S2C: 服务端 → 客户端 全量同步
        CHANNEL.registerMessage(
                nextId(),
                S2CFullSyncPacket.class,
                S2CFullSyncPacket::encode,
                S2CFullSyncPacket::decode,
                S2CFullSyncPacket::handle
        );
        
        // C2S: 客户端 → 服务端 桥接请求
        CHANNEL.registerMessage(
                nextId(),
                C2SBridgeActionPacket.class,
                C2SBridgeActionPacket::encode,
                C2SBridgeActionPacket::decode,
                C2SBridgeActionPacket::handle
        );
        
        // S2C: 服务端 → 客户端 桥接响应
        CHANNEL.registerMessage(
                nextId(),
                S2CBridgeResponsePacket.class,
                S2CBridgeResponsePacket::encode,
                S2CBridgeResponsePacket::decode,
                S2CBridgeResponsePacket::handle
        );
        
        // S2C: 服务端 → 客户端 事件推送
        CHANNEL.registerMessage(
                nextId(),
                S2CEventPushPacket.class,
                S2CEventPushPacket::encode,
                S2CEventPushPacket::decode,
                S2CEventPushPacket::handle
        );
        
        // ==================== 通用事件通信数据包 ====================
        
        // S2C: 通用事件推送（服务端 → 客户端前端）
        CHANNEL.registerMessage(
                nextId(),
                S2CPushEventPacket.class,
                S2CPushEventPacket::encode,
                S2CPushEventPacket::decode,
                S2CPushEventPacket::handle
        );
        
        // C2S: 通用事件发送（客户端前端 → 服务端）
        CHANNEL.registerMessage(
                nextId(),
                C2SSendEventPacket.class,
                C2SSendEventPacket::encode,
                C2SSendEventPacket::decode,
                C2SSendEventPacket::handle
        );
        
        LOGGER.debug("已注册 {} 个数据包类型", packetId);
    }
    
    /**
     * 获取下一个数据包 ID
     */
    private static int nextId() {
        return packetId++;
    }
    
    // ==================== 发送方法 ====================
    
    /**
     * 向指定玩家发送数据包
     * 
     * @param player 目标玩家
     * @param packet 数据包
     */
    public static <T> void sendToPlayer(ServerPlayer player, T packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
    
    /**
     * 向所有玩家广播数据包
     * 
     * @param packet 数据包
     */
    public static <T> void sendToAll(T packet) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }
    
    /**
     * 向服务端发送数据包（从客户端调用）
     * 
     * @param packet 数据包
     */
    public static <T> void sendToServer(T packet) {
        CHANNEL.sendToServer(packet);
    }
    
    /**
     * 向指定维度的所有玩家发送数据包
     * 
     * @param dimension 维度 ResourceKey
     * @param packet 数据包
     */
    public static <T> void sendToDimension(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, T packet) {
        CHANNEL.send(PacketDistributor.DIMENSION.with(() -> dimension), packet);
    }
    
    /**
     * 向指定位置附近的玩家发送数据包
     * 
     * @param target 目标点信息
     * @param packet 数据包
     */
    public static <T> void sendToNear(PacketDistributor.TargetPoint target, T packet) {
        CHANNEL.send(PacketDistributor.NEAR.with(() -> target), packet);
    }
    
    // ==================== 便捷方法 ====================
    
    /**
     * 向所有玩家广播数据包（sendToAll 的别名）
     * 
     * @param packet 数据包
     */
    public static <T> void sendToAllPlayers(T packet) {
        sendToAll(packet);
    }
    
    /**
     * 向玩家发送全量同步数据
     */
    public static void syncToPlayer(ServerPlayer player, String dataType, String jsonData) {
        sendToPlayer(player, new S2CFullSyncPacket(dataType, jsonData));
    }
    
    /**
     * 向所有玩家广播全量同步数据
     */
    public static void syncToAll(String dataType, String jsonData) {
        sendToAll(new S2CFullSyncPacket(dataType, jsonData));
    }
    
    /**
     * 获取网络通道（用于高级操作）
     */
    public static SimpleChannel getChannel() {
        return CHANNEL;
    }
}
