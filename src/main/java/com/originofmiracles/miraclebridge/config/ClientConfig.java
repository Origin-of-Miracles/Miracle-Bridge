package com.originofmiracles.miraclebridge.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Miracle Bridge 客户端配置
 * 
 * 配置项说明：
 * - [HOT] = 修改后立即生效
 * - [RESTART] = 需要重启客户端/重建浏览器实例才能生效
 */
public class ClientConfig {
    
    public static final ForgeConfigSpec SPEC;
    
    // ==================== Browser 配置 ====================
    
    /**
     * [RESTART] 默认浏览器宽度
     */
    public static final ForgeConfigSpec.IntValue BROWSER_DEFAULT_WIDTH;
    
    /**
     * [RESTART] 默认浏览器高度
     */
    public static final ForgeConfigSpec.IntValue BROWSER_DEFAULT_HEIGHT;
    
    /**
     * [RESTART] 浏览器背景是否透明
     */
    public static final ForgeConfigSpec.BooleanValue BROWSER_TRANSPARENT_BACKGROUND;
    
    /**
     * [HOT] Vite 开发服务器地址（生产环境留空使用内置资源）
     */
    public static final ForgeConfigSpec.ConfigValue<String> BROWSER_DEV_SERVER_URL;
    
    // ==================== Input 配置 ====================
    
    /**
     * [HOT] 滚轮灵敏度系数
     */
    public static final ForgeConfigSpec.IntValue SCROLL_SENSITIVITY;
    
    // ==================== Debug 配置 ====================
    
    /**
     * [HOT] 全局调试开关
     */
    public static final ForgeConfigSpec.BooleanValue DEBUG_ENABLED;
    
    /**
     * [HOT] 记录 Bridge API 请求/响应
     */
    public static final ForgeConfigSpec.BooleanValue DEBUG_LOG_BRIDGE_REQUESTS;
    
    /**
     * [HOT] 记录 JavaScript 执行
     */
    public static final ForgeConfigSpec.BooleanValue DEBUG_LOG_JS_EXECUTION;
    
    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        
        // ==================== Browser 配置 ====================
        builder.comment("浏览器相关配置")
               .push("browser");
        
        BROWSER_DEFAULT_WIDTH = builder
                .comment("[RESTART] 默认浏览器宽度（像素）")
                .defineInRange("defaultWidth", 1920, 800, 3840);
        
        BROWSER_DEFAULT_HEIGHT = builder
                .comment("[RESTART] 默认浏览器高度（像素）")
                .defineInRange("defaultHeight", 1080, 600, 2160);
        
        BROWSER_TRANSPARENT_BACKGROUND = builder
                .comment("[RESTART] 浏览器背景是否透明，用于叠加在游戏画面上")
                .define("transparentBackground", true);
        
        BROWSER_DEV_SERVER_URL = builder
                .comment("[HOT] Vite 开发服务器地址",
                        "开发时填写本地地址如 http://localhost:5173",
                        "生产环境留空使用内置资源")
                .define("devServerUrl", "http://localhost:5173");
        
        builder.pop();
        
        // ==================== Input 配置 ====================
        builder.comment("输入相关配置")
               .push("input");
        
        SCROLL_SENSITIVITY = builder
                .comment("[HOT] 滚轮灵敏度系数，默认 1，数值越大滚动越快")
                .defineInRange("scrollSensitivity", 1, 1, 50);
        
        builder.pop();
        
        // ==================== Debug 配置 ====================
        builder.comment("调试相关配置")
               .push("debug");
        
        DEBUG_ENABLED = builder
                .comment("[HOT] 全局调试开关，启用后会输出更多日志信息")
                .define("enabled", false);
        
        DEBUG_LOG_BRIDGE_REQUESTS = builder
                .comment("[HOT] 记录所有 Bridge API 的请求和响应内容")
                .define("logBridgeRequests", false);
        
        DEBUG_LOG_JS_EXECUTION = builder
                .comment("[HOT] 记录所有 JavaScript 执行脚本")
                .define("logJsExecution", false);
        
        builder.pop();
        
        SPEC = builder.build();
    }
    
    /**
     * 获取当前配置的浏览器宽度
     */
    public static int getBrowserWidth() {
        return BROWSER_DEFAULT_WIDTH.get();
    }
    
    /**
     * 获取当前配置的浏览器高度
     */
    public static int getBrowserHeight() {
        return BROWSER_DEFAULT_HEIGHT.get();
    }
    
    /**
     * 获取当前配置的开发服务器 URL
     */
    public static String getDevServerUrl() {
        return BROWSER_DEV_SERVER_URL.get();
    }
    
    /**
     * 获取滚轮灵敏度系数
     */
    public static int getScrollSensitivity() {
        return SCROLL_SENSITIVITY.get();
    }
    
    /**
     * 检查是否启用调试模式
     */
    public static boolean isDebugEnabled() {
        return DEBUG_ENABLED.get();
    }
    
    /**
     * 检查是否记录 Bridge 请求
     */
    public static boolean shouldLogBridgeRequests() {
        return DEBUG_LOG_BRIDGE_REQUESTS.get();
    }
    
    /**
     * 检查是否记录 JS 执行
     */
    public static boolean shouldLogJsExecution() {
        return DEBUG_LOG_JS_EXECUTION.get();
    }
    
    private ClientConfig() {}
}
