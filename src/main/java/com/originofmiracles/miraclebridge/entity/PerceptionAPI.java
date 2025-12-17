package com.originofmiracles.miraclebridge.entity;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Predicate;

/**
 * 实体感知 API
 * 
 * 提供实体周围环境的感知能力，用于 AI 决策：
 * - 扫描周围方块
 * - 扫描周围实体
 * - 获取环境信息（时间、天气、生物群系）
 * - 视线检测
 * 
 * 所有方法返回 JSON 格式数据，便于传递给 LLM。
 */
public class PerceptionAPI {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    
    private final ServerPlayer player;
    private final ServerLevel level;
    
    public PerceptionAPI(ServerPlayer player) {
        this.player = player;
        this.level = player.serverLevel();
    }
    
    // ==================== 方块感知 ====================
    
    /**
     * 获取周围指定范围内的方块
     * 
     * @param radius 扫描半径
     * @return JSON 数组，包含方块信息
     */
    public JsonArray getNearbyBlocks(int radius) {
        return getNearbyBlocks(radius, null);
    }
    
    /**
     * 获取周围指定范围内的方块（带过滤）
     * 
     * @param radius 扫描半径
     * @param filter 方块过滤器，null 表示不过滤
     * @return JSON 数组
     */
    public JsonArray getNearbyBlocks(int radius, @Nullable Predicate<BlockState> filter) {
        JsonArray blocks = new JsonArray();
        BlockPos playerPos = player.blockPosition();
        
        int count = 0;
        int maxBlocks = 500; // 限制返回数量
        
        for (int x = -radius; x <= radius && count < maxBlocks; x++) {
            for (int y = -radius; y <= radius && count < maxBlocks; y++) {
                for (int z = -radius; z <= radius && count < maxBlocks; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    
                    // 跳过空气
                    if (state.isAir()) continue;
                    
                    // 应用过滤器
                    if (filter != null && !filter.test(state)) continue;
                    
                    JsonObject block = new JsonObject();
                    block.addProperty("x", pos.getX());
                    block.addProperty("y", pos.getY());
                    block.addProperty("z", pos.getZ());
                    block.addProperty("id", ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString());
                    block.addProperty("solid", state.canOcclude());
                    block.addProperty("distance", Math.sqrt(x*x + y*y + z*z));
                    
                    blocks.add(block);
                    count++;
                }
            }
        }
        
        return blocks;
    }
    
    /**
     * 获取玩家正在看的方块
     * 
     * @param maxDistance 最大距离
     * @return 方块信息，未命中返回 null
     */
    @Nullable
    public JsonObject getLookingAtBlock(double maxDistance) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.scale(maxDistance));
        
        BlockHitResult hit = level.clip(new ClipContext(
                eyePos, endPos,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
        ));
        
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            
            JsonObject result = new JsonObject();
            result.addProperty("x", pos.getX());
            result.addProperty("y", pos.getY());
            result.addProperty("z", pos.getZ());
            result.addProperty("id", ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString());
            result.addProperty("distance", hit.getLocation().distanceTo(eyePos));
            
            return result;
        }
        
        return null;
    }
    
    // ==================== 实体感知 ====================
    
    /**
     * 获取周围的实体
     * 
     * @param radius 扫描半径
     * @return JSON 数组
     */
    public JsonArray getNearbyEntities(double radius) {
        return getNearbyEntities(radius, null);
    }
    
    /**
     * 获取周围的实体（带过滤）
     * 
     * @param radius 扫描半径
     * @param filter 实体过滤器
     * @return JSON 数组
     */
    public JsonArray getNearbyEntities(double radius, @Nullable Predicate<Entity> filter) {
        JsonArray entities = new JsonArray();
        
        AABB box = player.getBoundingBox().inflate(radius);
        List<Entity> nearbyEntities = level.getEntities(player, box);
        
        for (Entity entity : nearbyEntities) {
            if (filter != null && !filter.test(entity)) continue;
            
            JsonObject entityJson = new JsonObject();
            entityJson.addProperty("id", entity.getId());
            entityJson.addProperty("uuid", entity.getUUID().toString());
            entityJson.addProperty("type", ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()).toString());
            entityJson.addProperty("x", entity.getX());
            entityJson.addProperty("y", entity.getY());
            entityJson.addProperty("z", entity.getZ());
            entityJson.addProperty("distance", entity.distanceTo(player));
            
            if (entity instanceof LivingEntity living) {
                entityJson.addProperty("health", living.getHealth());
                entityJson.addProperty("maxHealth", living.getMaxHealth());
                entityJson.addProperty("isAlive", living.isAlive());
            }
            
            if (entity instanceof Player p) {
                entityJson.addProperty("playerName", p.getName().getString());
                entityJson.addProperty("isPlayer", true);
            } else {
                entityJson.addProperty("isPlayer", false);
            }
            
            entities.add(entityJson);
        }
        
        return entities;
    }
    
    /**
     * 获取附近的玩家
     */
    public JsonArray getNearbyPlayers(double radius) {
        return getNearbyEntities(radius, entity -> entity instanceof Player);
    }
    
    /**
     * 获取附近的生物
     */
    public JsonArray getNearbyMobs(double radius) {
        return getNearbyEntities(radius, entity -> entity instanceof LivingEntity && !(entity instanceof Player));
    }
    
    /**
     * 检查是否能看到目标
     * 
     * @param target 目标位置
     * @return 是否有视线
     */
    public boolean canSee(BlockPos target) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 targetVec = Vec3.atCenterOf(target);
        
        BlockHitResult hit = level.clip(new ClipContext(
                eyePos, targetVec,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        
        return hit.getType() == HitResult.Type.MISS ||
               hit.getBlockPos().equals(target);
    }
    
    /**
     * 检查是否能看到实体
     */
    public boolean canSee(Entity target) {
        return player.hasLineOfSight(target);
    }
    
    // ==================== 环境感知 ====================
    
    /**
     * 获取完整的环境信息
     */
    public JsonObject getEnvironment() {
        JsonObject env = new JsonObject();
        
        // 时间
        env.addProperty("dayTime", level.getDayTime() % 24000);
        env.addProperty("isDay", level.isDay());
        env.addProperty("isNight", level.isNight());
        env.addProperty("gameTime", level.getGameTime());
        
        // 天气
        env.addProperty("isRaining", level.isRaining());
        env.addProperty("isThundering", level.isThundering());
        
        // 生物群系
        BlockPos pos = player.blockPosition();
        env.addProperty("biome", level.getBiome(pos).unwrapKey()
                .map(key -> key.location().toString())
                .orElse("unknown"));
        
        // 光照
        env.addProperty("skyLight", level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos));
        env.addProperty("blockLight", level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos));
        
        // 维度
        env.addProperty("dimension", level.dimension().location().toString());
        
        // 难度
        env.addProperty("difficulty", level.getDifficulty().getKey());
        
        return env;
    }
    
    /**
     * 获取当前时间段
     * @return "dawn", "day", "dusk", "night"
     */
    public String getTimeOfDay() {
        long time = level.getDayTime() % 24000;
        if (time < 1000) return "dawn";
        if (time < 12000) return "day";
        if (time < 13000) return "dusk";
        return "night";
    }
    
    /**
     * 获取天气状态
     * @return "clear", "rain", "thunder"
     */
    public String getWeather() {
        if (level.isThundering()) return "thunder";
        if (level.isRaining()) return "rain";
        return "clear";
    }
    
    // ==================== 导出完整感知数据 ====================
    
    /**
     * 获取完整的感知快照
     * 
     * @param blockRadius 方块扫描半径
     * @param entityRadius 实体扫描半径
     * @return 包含所有感知数据的 JSON
     */
    public JsonObject getFullPerception(int blockRadius, double entityRadius) {
        JsonObject perception = new JsonObject();
        
        // 玩家自身状态
        JsonObject self = new JsonObject();
        self.addProperty("x", player.getX());
        self.addProperty("y", player.getY());
        self.addProperty("z", player.getZ());
        self.addProperty("yaw", player.getYRot());
        self.addProperty("pitch", player.getXRot());
        self.addProperty("health", player.getHealth());
        self.addProperty("food", player.getFoodData().getFoodLevel());
        perception.add("self", self);
        
        // 环境
        perception.add("environment", getEnvironment());
        
        // 周围实体
        perception.add("entities", getNearbyEntities(entityRadius));
        
        // 正在看的方块
        JsonObject lookingAt = getLookingAtBlock(10);
        if (lookingAt != null) {
            perception.add("lookingAt", lookingAt);
        }
        
        // 方块数据（可选，数据量大）
        // perception.add("blocks", getNearbyBlocks(blockRadius));
        
        return perception;
    }
    
    /**
     * 转换为 JSON 字符串
     */
    public String toJsonString(JsonObject json) {
        return GSON.toJson(json);
    }
}
