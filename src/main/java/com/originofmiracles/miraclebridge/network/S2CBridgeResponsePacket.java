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
 * 服务端 → 客户端 桥接响应数据包
 * 
 * 用于将服务端处理结果返回给客户端。
 * 通过 requestId 与客户端的 CompletableFuture 匹配。
 * 
 * 数据格式：
 * - requestId: 请求标识符（用于匹配异步请求）
 * - success: 是否成功
 * - jsonPayload: JSON 格式的响应数据
 */
public class S2CBridgeResponsePacket {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    
    /**
     * 最大负载长度（字节）
     */
    private static final int MAX_PAYLOAD_LENGTH = 1048576; // 1MB
    
    private final String requestId;
    private final boolean success;
    private final String jsonPayload;
    
    /**
     * 创建桥接响应数据包
     * 
     * @param requestId 请求 ID
     * @param success 是否成功
     * @param jsonPayload JSON 格式的响应数据
     */
    public S2CBridgeResponsePacket(String requestId, boolean success, String jsonPayload) {
        this.requestId = requestId;
        this.success = success;
        this.jsonPayload = jsonPayload;
    }
    
    /**
     * 从 JsonObject 创建响应包
     */
    public static S2CBridgeResponsePacket fromResult(String requestId, JsonObject result) {
        boolean success = result.has("success") && result.get("success").getAsBoolean();
        result.addProperty("requestId", requestId);
        return new S2CBridgeResponsePacket(requestId, success, GSON.toJson(result));
    }
    
    /**
     * 创建错误响应包
     */
    public static S2CBridgeResponsePacket error(String requestId, String errorMessage) {
        JsonObject error = new JsonObject();
        error.addProperty("requestId", requestId);
        error.addProperty("success", false);
        error.addProperty("error", errorMessage);
        return new S2CBridgeResponsePacket(requestId, false, GSON.toJson(error));
    }
    
    /**
     * 编码数据包到网络缓冲区
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(requestId, 64);
        buf.writeBoolean(success);
        buf.writeUtf(jsonPayload, MAX_PAYLOAD_LENGTH);
    }
    
    /**
     * 从网络缓冲区解码数据包
     */
    public static S2CBridgeResponsePacket decode(FriendlyByteBuf buf) {
        String requestId = buf.readUtf(64);
        boolean success = buf.readBoolean();
        String jsonPayload = buf.readUtf(MAX_PAYLOAD_LENGTH);
        return new S2CBridgeResponsePacket(requestId, success, jsonPayload);
    }
    
    /**
     * 处理接收到的数据包（客户端）
     */
    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        // 确保在客户端主线程处理
        ctx.enqueueWork(() -> {
            try {
                handleResponse();
            } catch (Exception e) {
                LOGGER.error("处理桥接响应失败: requestId={}", requestId, e);
            }
        });
        ctx.setPacketHandled(true);
    }
    
    /**
     * 实际处理响应的逻辑
     */
    private void handleResponse() {
        if (ClientConfig.isDebugEnabled()) {
            LOGGER.debug("收到桥接响应: requestId={}, success={}, size={} bytes", 
                    requestId, success, jsonPayload.length());
        }
        
        // 查找主浏览器的 BridgeAPI 并传递响应
        MiracleBrowser mainBrowser = BrowserManager.getInstance().getBrowser("main");
        if (mainBrowser != null) {
            BridgeAPI bridgeAPI = mainBrowser.getBridgeAPI();
            if (bridgeAPI != null) {
                bridgeAPI.handleServerResponse(jsonPayload);
            } else {
                LOGGER.warn("BridgeAPI 未初始化，无法处理响应: requestId={}", requestId);
            }
        } else {
            // 尝试处理所有浏览器
            for (String browserName : BrowserManager.getInstance().getBrowserNames()) {
                MiracleBrowser browser = BrowserManager.getInstance().getBrowser(browserName);
                if (browser != null) {
                    BridgeAPI bridgeAPI = browser.getBridgeAPI();
                    if (bridgeAPI != null) {
                        bridgeAPI.handleServerResponse(jsonPayload);
                        return;
                    }
                }
            }
            LOGGER.warn("没有可用的浏览器处理响应: requestId={}", requestId);
        }
    }
    
    // ==================== Getter ====================
    
    public String getRequestId() {
        return requestId;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getJsonPayload() {
        return jsonPayload;
    }
}
