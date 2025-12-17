package com.originofmiracles.miraclebridge.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.originofmiracles.miraclebridge.config.ClientConfig;
import com.originofmiracles.miraclebridge.config.ServerConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端 桥接请求数据包
 * 
 * 用于将客户端（通过 Bridge API）的请求转发到服务端处理。
 * 服务端处理完成后，通过 S2CFullSyncPacket 返回结果。
 * 
 * 数据格式：
 * - action: 动作名称（如 "teleport", "getInventory"）
 * - requestId: 请求 ID，用于匹配响应
 * - jsonPayload: JSON 格式的请求参数
 */
public class C2SBridgeActionPacket {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    
    /**
     * 最大负载长度（字节）
     */
    private static final int MAX_PAYLOAD_LENGTH = 65536; // 64KB
    
    private final String action;
    private final String requestId;
    private final String jsonPayload;
    
    /**
     * 创建桥接请求数据包
     * 
     * @param action 动作名称
     * @param requestId 请求 ID
     * @param jsonPayload JSON 格式的请求参数
     */
    public C2SBridgeActionPacket(String action, String requestId, String jsonPayload) {
        this.action = action;
        this.requestId = requestId;
        this.jsonPayload = jsonPayload;
    }
    
    /**
     * 编码数据包到网络缓冲区
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(action, 128);
        buf.writeUtf(requestId, 64);
        buf.writeUtf(jsonPayload, MAX_PAYLOAD_LENGTH);
    }
    
    /**
     * 从网络缓冲区解码数据包
     */
    public static C2SBridgeActionPacket decode(FriendlyByteBuf buf) {
        String action = buf.readUtf(128);
        String requestId = buf.readUtf(64);
        String jsonPayload = buf.readUtf(MAX_PAYLOAD_LENGTH);
        return new C2SBridgeActionPacket(action, requestId, jsonPayload);
    }
    
    /**
     * 处理接收到的数据包（服务端）
     */
    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                LOGGER.warn("收到无发送者的桥接请求: action={}", action);
                return;
            }
            
            try {
                handleAction(player);
            } catch (Exception e) {
                LOGGER.error("处理桥接请求失败: action={}, player={}", action, player.getName().getString(), e);
                sendErrorResponse(player, "内部错误: " + e.getMessage());
            }
        });
        ctx.setPacketHandled(true);
    }
    
    /**
     * 实际处理桥接请求的逻辑
     */
    private void handleAction(ServerPlayer player) {
        if (ClientConfig.isDebugEnabled()) {
            LOGGER.debug("收到桥接请求: action={}, player={}, payload={}", 
                    action, player.getName().getString(), jsonPayload);
        }
        
        // 检查请求大小
        if (jsonPayload.length() > ServerConfig.getMaxRequestSize()) {
            sendErrorResponse(player, "请求体过大");
            return;
        }
        
        // 解析请求
        JsonObject payload;
        try {
            payload = GSON.fromJson(jsonPayload, JsonObject.class);
        } catch (Exception e) {
            sendErrorResponse(player, "无效的 JSON 格式");
            return;
        }
        
        // 根据动作类型分发处理
        JsonObject result = switch (action) {
            case "teleport" -> handleTeleport(player, payload);
            case "getPlayerInfo" -> handleGetPlayerInfo(player, payload);
            case "getInventory" -> handleGetInventory(player, payload);
            case "sendChat" -> handleSendChat(player, payload);
            default -> {
                LOGGER.warn("未知的桥接动作: {}", action);
                yield errorResult("未知动作: " + action);
            }
        };
        
        // 发送响应
        sendResponse(player, result);
    }
    
    // ==================== 动作处理器 ====================
    
    /**
     * 处理传送请求
     */
    private JsonObject handleTeleport(ServerPlayer player, JsonObject payload) {
        // 检查权限
        if (!player.hasPermissions(2)) {
            return errorResult("权限不足");
        }
        
        try {
            double x = payload.get("x").getAsDouble();
            double y = payload.get("y").getAsDouble();
            double z = payload.get("z").getAsDouble();
            
            // 执行传送
            player.teleportTo(x, y, z);
            
            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("x", x);
            result.addProperty("y", y);
            result.addProperty("z", z);
            return result;
            
        } catch (Exception e) {
            return errorResult("传送失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理获取玩家信息请求
     */
    private JsonObject handleGetPlayerInfo(ServerPlayer player, JsonObject payload) {
        JsonObject result = new JsonObject();
        result.addProperty("success", true);
        result.addProperty("name", player.getName().getString());
        result.addProperty("uuid", player.getUUID().toString());
        result.addProperty("health", player.getHealth());
        result.addProperty("maxHealth", player.getMaxHealth());
        result.addProperty("foodLevel", player.getFoodData().getFoodLevel());
        result.addProperty("saturation", player.getFoodData().getSaturationLevel());
        result.addProperty("level", player.experienceLevel);
        result.addProperty("x", player.getX());
        result.addProperty("y", player.getY());
        result.addProperty("z", player.getZ());
        result.addProperty("dimension", player.level().dimension().location().toString());
        return result;
    }
    
    /**
     * 处理获取背包请求
     */
    private JsonObject handleGetInventory(ServerPlayer player, JsonObject payload) {
        JsonObject result = new JsonObject();
        result.addProperty("success", true);
        
        // 主背包物品
        com.google.gson.JsonArray mainInventory = new com.google.gson.JsonArray();
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            net.minecraft.world.item.ItemStack stack = player.getInventory().items.get(i);
            mainInventory.add(serializeItemStack(stack, i));
        }
        result.add("mainInventory", mainInventory);
        
        // 盔甲
        com.google.gson.JsonArray armor = new com.google.gson.JsonArray();
        for (int i = 0; i < player.getInventory().armor.size(); i++) {
            net.minecraft.world.item.ItemStack stack = player.getInventory().armor.get(i);
            armor.add(serializeItemStack(stack, i));
        }
        result.add("armor", armor);
        
        // 副手
        com.google.gson.JsonArray offhand = new com.google.gson.JsonArray();
        for (int i = 0; i < player.getInventory().offhand.size(); i++) {
            net.minecraft.world.item.ItemStack stack = player.getInventory().offhand.get(i);
            offhand.add(serializeItemStack(stack, i));
        }
        result.add("offhand", offhand);
        
        // 当前选中槽位
        result.addProperty("selectedSlot", player.getInventory().selected);
        
        return result;
    }
    
    /**
     * 序列化物品堆
     */
    private JsonObject serializeItemStack(net.minecraft.world.item.ItemStack stack, int slot) {
        JsonObject item = new JsonObject();
        item.addProperty("slot", slot);
        
        if (stack.isEmpty()) {
            item.addProperty("empty", true);
            return item;
        }
        
        item.addProperty("empty", false);
        item.addProperty("id", net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());
        item.addProperty("count", stack.getCount());
        item.addProperty("maxCount", stack.getMaxStackSize());
        item.addProperty("displayName", stack.getHoverName().getString());
        item.addProperty("damaged", stack.isDamaged());
        
        if (stack.isDamageableItem()) {
            item.addProperty("damage", stack.getDamageValue());
            item.addProperty("maxDamage", stack.getMaxDamage());
            item.addProperty("durabilityPercent", (stack.getMaxDamage() - stack.getDamageValue()) * 100.0 / stack.getMaxDamage());
        }
        
        // 附魔
        if (stack.isEnchanted()) {
            com.google.gson.JsonArray enchantments = new com.google.gson.JsonArray();
            for (var enchantmentInstance : stack.getEnchantmentTags()) {
                if (enchantmentInstance instanceof net.minecraft.nbt.CompoundTag tag) {
                    JsonObject ench = new JsonObject();
                    ench.addProperty("id", tag.getString("id"));
                    ench.addProperty("level", tag.getInt("lvl"));
                    enchantments.add(ench);
                }
            }
            item.add("enchantments", enchantments);
        }
        
        // NBT 数据（简化版）
        if (stack.hasTag()) {
            item.addProperty("hasNbt", true);
            // 可选：添加完整 NBT 序列化
            // item.addProperty("nbt", stack.getTag().toString());
        } else {
            item.addProperty("hasNbt", false);
        }
        
        return item;
    }
    
    /**
     * 处理发送聊天请求
     */
    private JsonObject handleSendChat(ServerPlayer player, JsonObject payload) {
        try {
            String message = payload.get("message").getAsString();
            
            // 限制消息长度
            if (message.length() > 256) {
                return errorResult("消息过长（最大 256 字符）");
            }
            
            // 发送消息
            player.server.getPlayerList().broadcastSystemMessage(
                    net.minecraft.network.chat.Component.literal("<" + player.getName().getString() + "> " + message),
                    false
            );
            
            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            return result;
            
        } catch (Exception e) {
            return errorResult("发送失败: " + e.getMessage());
        }
    }
    
    // ==================== 响应方法 ====================
    
    /**
     * 发送成功响应
     */
    private void sendResponse(ServerPlayer player, JsonObject result) {
        result.addProperty("requestId", requestId);
        String responseJson = GSON.toJson(result);
        
        ModNetworkHandler.sendToPlayer(player, new S2CFullSyncPacket("bridge_response", responseJson));
        
        if (ClientConfig.isDebugEnabled()) {
            LOGGER.debug("发送桥接响应: requestId={}, result={}", requestId, responseJson);
        }
    }
    
    /**
     * 发送错误响应
     */
    private void sendErrorResponse(ServerPlayer player, String errorMessage) {
        JsonObject error = errorResult(errorMessage);
        sendResponse(player, error);
    }
    
    /**
     * 创建错误结果
     */
    private JsonObject errorResult(String message) {
        JsonObject error = new JsonObject();
        error.addProperty("success", false);
        error.addProperty("error", message);
        return error;
    }
    
    // ==================== Getter ====================
    
    public String getAction() {
        return action;
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public String getJsonPayload() {
        return jsonPayload;
    }
}
