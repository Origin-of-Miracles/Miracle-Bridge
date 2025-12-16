package com.originofmiracles.miraclebridge.config;

import java.util.List;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Miracle Bridge 服务端配置
 * 
 * 配置项说明：
 * - [HOT] = 修改后立即生效
 * - [RESTART] = 需要重启服务器才能生效
 */
public class ServerConfig {
    
    public static final ForgeConfigSpec SPEC;
    
    // ==================== Bridge 配置 ====================
    
    /**
     * [HOT] 最大请求体大小（字节）
     */
    public static final ForgeConfigSpec.IntValue BRIDGE_MAX_REQUEST_SIZE;
    
    /**
     * [HOT] 请求超时时间（毫秒）
     */
    public static final ForgeConfigSpec.IntValue BRIDGE_REQUEST_TIMEOUT;
    
    // ==================== Entity YSM 配置 ====================
    
    /**
     * [RESTART] 启用 Yes Steve Model 集成
     */
    public static final ForgeConfigSpec.BooleanValue YSM_ENABLED;
    
    /**
     * [HOT] YSM 不可用时优雅降级到原版
     */
    public static final ForgeConfigSpec.BooleanValue YSM_GRACEFUL_FALLBACK;
    
    // ==================== Security 配置 ====================
    
    /**
     * [HOT] 允许的请求来源列表
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SECURITY_ALLOWED_ORIGINS;
    
    /**
     * [HOT] 是否启用来源校验
     */
    public static final ForgeConfigSpec.BooleanValue SECURITY_ENABLE_ORIGIN_CHECK;
    
    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        
        // ==================== Bridge 配置 ====================
        builder.comment("Bridge API 相关配置")
               .push("bridge");
        
        BRIDGE_MAX_REQUEST_SIZE = builder
                .comment("[HOT] 最大请求体大小（字节）",
                        "默认 1MB，过大的请求会被拒绝")
                .defineInRange("maxRequestSize", 1048576, 1024, 10485760);
        
        BRIDGE_REQUEST_TIMEOUT = builder
                .comment("[HOT] 请求超时时间（毫秒）",
                        "超时后请求会被自动取消")
                .defineInRange("requestTimeout", 30000, 1000, 120000);
        
        builder.pop();
        
        // ==================== Entity YSM 配置 ====================
        builder.comment("实体 AI 相关配置")
               .push("entity");
        
        builder.push("ysm");
        
        YSM_ENABLED = builder
                .comment("[RESTART] 启用 Yes Steve Model 集成",
                        "需要安装 YSM 模组才能使用高级模型功能")
                .define("enabled", true);
        
        YSM_GRACEFUL_FALLBACK = builder
                .comment("[HOT] YSM 不可用时优雅降级到原版实体",
                        "关闭后如果 YSM 不可用会抛出错误")
                .define("gracefulFallback", true);
        
        builder.pop(); // ysm
        builder.pop(); // entity
        
        // ==================== Security 配置 ====================
        builder.comment("安全相关配置")
               .push("security");
        
        SECURITY_ALLOWED_ORIGINS = builder
                .comment("[HOT] 允许的请求来源列表",
                        "只有来自这些来源的请求才会被处理",
                        "bridge:// 是内部协议，通常需要保留")
                .defineList("allowedOrigins", 
                        List.of("bridge://", "http://localhost:5173"),
                        obj -> obj instanceof String);
        
        SECURITY_ENABLE_ORIGIN_CHECK = builder
                .comment("[HOT] 是否启用来源校验",
                        "生产环境建议开启，开发环境可以关闭")
                .define("enableOriginCheck", false);
        
        builder.pop();
        
        SPEC = builder.build();
    }
    
    /**
     * 获取最大请求体大小
     */
    public static int getMaxRequestSize() {
        return BRIDGE_MAX_REQUEST_SIZE.get();
    }
    
    /**
     * 获取请求超时时间
     */
    public static int getRequestTimeout() {
        return BRIDGE_REQUEST_TIMEOUT.get();
    }
    
    /**
     * 检查是否启用 YSM
     */
    public static boolean isYSMEnabled() {
        return YSM_ENABLED.get();
    }
    
    /**
     * 检查 YSM 是否优雅降级
     */
    public static boolean isYSMGracefulFallback() {
        return YSM_GRACEFUL_FALLBACK.get();
    }
    
    /**
     * 获取允许的来源列表
     */
    @SuppressWarnings("unchecked")
    public static List<String> getAllowedOrigins() {
        return (List<String>) SECURITY_ALLOWED_ORIGINS.get();
    }
    
    /**
     * 检查是否启用来源校验
     */
    public static boolean isOriginCheckEnabled() {
        return SECURITY_ENABLE_ORIGIN_CHECK.get();
    }
    
    /**
     * 检查指定来源是否被允许
     */
    public static boolean isOriginAllowed(String origin) {
        if (!isOriginCheckEnabled()) {
            return true;
        }
        return getAllowedOrigins().stream()
                .anyMatch(allowed -> origin.startsWith(allowed));
    }
    
    private ServerConfig() {}
}
