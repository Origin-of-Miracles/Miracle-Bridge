package com.originofmiracles.miraclebridge.network;

import com.mojang.logging.LogUtils;
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
        // TODO: 实现实体状态同步处理
        // 解析 jsonData 并更新本地实体状态
        if (ClientConfig.isDebugEnabled()) {
            LOGGER.debug("实体状态同步: {}", jsonData);
        }
    }
    
    /**
     * 处理游戏事件同步
     */
    private void handleGameEventSync() {
        // TODO: 实现游戏事件同步处理
        // 触发相应的客户端事件
        if (ClientConfig.isDebugEnabled()) {
            LOGGER.debug("游戏事件同步: {}", jsonData);
        }
    }
    
    /**
     * 处理浏览器命令同步
     */
    private void handleBrowserCommandSync() {
        // TODO: 实现浏览器命令同步处理
        // 将命令转发给 BrowserManager
        if (ClientConfig.isDebugEnabled()) {
            LOGGER.debug("浏览器命令同步: {}", jsonData);
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
