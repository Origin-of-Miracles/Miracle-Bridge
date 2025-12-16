package com.originofmiracles.miraclebridge.entity;

import com.mojang.logging.LogUtils;
import com.originofmiracles.miraclebridge.entity.ysm.YSMCompat;
import com.originofmiracles.miraclebridge.entity.ysm.YSMEntityDriver;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 实体驱动工厂
 * 
 * 根据实体类型和环境自动选择合适的驱动实现：
 * 1. 检测 YSM 是否可用
 * 2. 根据实体类型选择驱动
 * 3. 支持自定义驱动注册
 * 
 * 优先级：
 * 1. 自定义注册的驱动
 * 2. YSM 驱动（如果 YSM 可用）
 * 3. 原版驱动（回退方案）
 */
public class EntityDriverFactory {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private static EntityDriverFactory instance;
    
    /**
     * 自定义驱动注册表
     * Key: 实体类型 ID, Value: 驱动创建函数
     */
    private final Map<String, Function<ServerPlayer, IEntityDriver>> customDrivers = new HashMap<>();
    
    /**
     * 驱动缓存
     * Key: 实体 UUID, Value: 驱动实例
     */
    private final Map<String, IEntityDriver> driverCache = new HashMap<>();
    
    private EntityDriverFactory() {}
    
    public static EntityDriverFactory getInstance() {
        if (instance == null) {
            instance = new EntityDriverFactory();
        }
        return instance;
    }
    
    /**
     * 获取玩家的实体驱动
     * 
     * @param player 目标玩家
     * @return 实体驱动实例
     */
    public IEntityDriver getDriver(ServerPlayer player) {
        String uuid = player.getUUID().toString();
        
        // 检查缓存
        IEntityDriver cached = driverCache.get(uuid);
        if (cached != null && cached.isAvailable()) {
            return cached;
        }
        
        // 创建新驱动
        IEntityDriver driver = createDriver(player);
        driverCache.put(uuid, driver);
        
        LOGGER.debug("创建实体驱动: {} -> {}", player.getName().getString(), driver.getClass().getSimpleName());
        
        return driver;
    }
    
    /**
     * 创建驱动实例
     */
    private IEntityDriver createDriver(ServerPlayer player) {
        // 1. 检查自定义驱动
        String entityType = "player"; // 玩家类型固定
        if (customDrivers.containsKey(entityType)) {
            IEntityDriver custom = customDrivers.get(entityType).apply(player);
            if (custom != null && custom.isAvailable()) {
                LOGGER.debug("使用自定义驱动: {}", custom.getClass().getSimpleName());
                return custom;
            }
        }
        
        // 2. 检查 YSM 是否可用
        if (YSMCompat.isYSMLoaded()) {
            YSMEntityDriver ysmDriver = new YSMEntityDriver(player);
            if (ysmDriver.isAvailable()) {
                LOGGER.debug("使用 YSM 驱动");
                return ysmDriver;
            }
        }
        
        // 3. 回退到原版驱动
        LOGGER.debug("使用原版驱动");
        return new VanillaEntityDriver(player);
    }
    
    /**
     * 注册自定义驱动
     * 
     * @param entityType 实体类型 ID（如 "player", "villager"）
     * @param driverFactory 驱动创建函数
     */
    public void registerDriver(String entityType, Function<ServerPlayer, IEntityDriver> driverFactory) {
        customDrivers.put(entityType, driverFactory);
        LOGGER.info("注册自定义驱动: {}", entityType);
    }
    
    /**
     * 取消注册自定义驱动
     */
    public void unregisterDriver(String entityType) {
        customDrivers.remove(entityType);
        LOGGER.info("取消注册自定义驱动: {}", entityType);
    }
    
    /**
     * 检查是否有自定义驱动
     */
    public boolean hasCustomDriver(String entityType) {
        return customDrivers.containsKey(entityType);
    }
    
    /**
     * 清除驱动缓存
     */
    public void clearCache() {
        driverCache.clear();
        LOGGER.debug("驱动缓存已清除");
    }
    
    /**
     * 从缓存中移除指定玩家的驱动
     */
    public void removeFromCache(ServerPlayer player) {
        String uuid = player.getUUID().toString();
        IEntityDriver removed = driverCache.remove(uuid);
        if (removed != null) {
            LOGGER.debug("已移除驱动缓存: {}", player.getName().getString());
        }
    }
    
    /**
     * 获取当前可用的驱动类型描述
     */
    public String getAvailableDriverType() {
        if (YSMCompat.isYSMLoaded()) {
            return "YSM + Vanilla";
        }
        return "Vanilla";
    }
    
    /**
     * 获取缓存的驱动数量
     */
    public int getCacheSize() {
        return driverCache.size();
    }
    
    /**
     * 检查 YSM 是否可用
     */
    public boolean isYSMAvailable() {
        return YSMCompat.isYSMLoaded();
    }
}
