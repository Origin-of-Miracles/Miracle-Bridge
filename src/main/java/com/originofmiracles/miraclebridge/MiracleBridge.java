package com.originofmiracles.miraclebridge;

import org.slf4j.Logger;

import com.cinemamod.mcef.MCEF;
import com.mojang.logging.LogUtils;
import com.originofmiracles.miraclebridge.bridge.BridgeSchemeHandler;
import com.originofmiracles.miraclebridge.browser.BrowserConsoleLogger;
import com.originofmiracles.miraclebridge.browser.BrowserManager;
import com.originofmiracles.miraclebridge.browser.BrowserOverlay;
import com.originofmiracles.miraclebridge.browser.MiracleBrowser;
import com.originofmiracles.miraclebridge.config.ClientConfig;
import com.originofmiracles.miraclebridge.config.ConfigReloader;
import com.originofmiracles.miraclebridge.config.ConfigWatcher;
import com.originofmiracles.miraclebridge.config.ModConfigs;
import com.originofmiracles.miraclebridge.network.ModNetworkHandler;
import com.originofmiracles.miraclebridge.server.EmbeddedWebServer;
import com.originofmiracles.miraclebridge.util.ThreadScheduler;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Miracle Bridge - Origin of Miracles 核心前置模组
 * 
 * 本模组作为连接 Minecraft 与现代 Web/AI 技术的基础设施。
 * 提供功能：
 * - 基于 Chromium 的 Webview 渲染（通过 MCEF）
 * - 双向 JS ↔ Java 通信桥梁
 * - 用于 LLM 集成的实体 AI 接口
 * - YSM（Yes Steve Model）兼容层
 * 
 * @author Origin of Miracles Dev Team
 * @version 0.1.0-alpha
 */
@Mod(MiracleBridge.MOD_ID)
public class MiracleBridge {
    
    public static final String MOD_ID = "miraclebridge";
    public static final String MOD_NAME = "Miracle Bridge";
    public static final String VERSION = "0.1.0-alpha";
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private static MiracleBridge instance;
    
    public MiracleBridge() {
        instance = this;
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        
        // 注册配置
        ModConfigs.register();
        
        // Register setup methods
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::loadComplete);
        
        // Delayed registration to Forge event bus to avoid leaking 'this' in constructor
        modEventBus.addListener((FMLCommonSetupEvent event) -> {
            MinecraftForge.EVENT_BUS.register(instance);
        });
        
        LOGGER.info("Miracle Bridge initialized - Connecting reality with miracles...");
    }
    
    /**
     * Common setup - runs on both client and server
     */
    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Miracle Bridge common setup");
        
        event.enqueueWork(() -> {
            // Register network packets
            ModNetworkHandler.register();
            
            LOGGER.info("Network handler registered");
        });
    }
    
    /**
     * Client-only setup
     */
    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("Miracle Bridge client setup");
        
        event.enqueueWork(() -> {
            // Initialize BrowserManager with config defaults
            BrowserManager.getInstance();
            
            // Start embedded web server if not using dev server
            startEmbeddedServerIfNeeded();
            
            // Register bridge:// scheme handler (after MCEF initialization)
            LOGGER.info("Scheduling MCEF initialization callback...");
            MCEF.scheduleForInit(success -> {
                LOGGER.info("MCEF initialization callback executed, success: {}", success);
                if (success) {
                    registerBridgeScheme();
                    registerConsoleLogger();
                    
                    // Auto-create default browser (for BrowserScreen)
                    LOGGER.info("Starting default browser creation...");
                    createDefaultBrowser();
                    
                    // Initialize HUD overlay browser (for CommunicationHUD)
                    LOGGER.info("Initializing HUD overlay browser...");
                    initHudOverlay();
                } else {
                    LOGGER.warn("MCEF initialization failed, bridge:// scheme not registered");
                }
            });
            
            // Log server URL
            logServerUrl();
            
            LOGGER.info("Client components initialized");
        });
    }
    
    /**
     * Start embedded web server if devServerUrl is empty
     */
    private void startEmbeddedServerIfNeeded() {
        if (ClientConfig.shouldUseEmbeddedServer()) {
            int port = ClientConfig.getEmbeddedServerPort();
            LOGGER.info("Starting embedded web server on port {}...", port);
            
            EmbeddedWebServer server = EmbeddedWebServer.getInstance();
            if (server.start(port)) {
                LOGGER.info("Embedded web server started successfully");
            } else {
                LOGGER.error("Failed to start embedded web server");
            }
        } else {
            LOGGER.info("Using external dev server, embedded server not started");
        }
    }
    
    /**
     * Log the active server URL for debugging
     */
    private void logServerUrl() {
        if (ClientConfig.shouldUseEmbeddedServer()) {
            EmbeddedWebServer server = EmbeddedWebServer.getInstance();
            if (server.isRunning()) {
                LOGGER.info("Web UI available at: {}", server.getServerUrl());
            }
        } else {
            String devUrl = ClientConfig.getDevServerUrl();
            LOGGER.info("Using dev server URL: {}", devUrl);
        }
    }
    
    /**
     * Register bridge:// custom protocol handler
     */
    private void registerBridgeScheme() {
        try {
            MCEF.getApp().getHandle().registerSchemeHandlerFactory(
                "bridge", "",
                (browser, frame, schemeName, request) -> {
                    String url = request.getURL();
                    String postData = extractPostData(request);
                    return new BridgeSchemeHandler(url, postData);
                }
            );
            LOGGER.info("bridge:// scheme handler registered");
        } catch (Exception e) {
            LOGGER.error("Failed to register bridge:// scheme handler", e);
        }
    }
    
    /**
     * Register console logger to capture browser JavaScript console output
     */
    private void registerConsoleLogger() {
        try {
            BrowserConsoleLogger logger = BrowserConsoleLogger.getInstance();
            logger.start();
            MCEF.getClient().addDisplayHandler(logger);
            LOGGER.info("Browser console logger registered, output: {}", logger.getLogFilePath());
        } catch (Exception e) {
            LOGGER.error("Failed to register browser console logger", e);
        }
    }
    
    /**
     * Auto-create default browser on startup
     */
    private void createDefaultBrowser() {
        try {
            // Get the URL to load
            String url = null;
            
            if (ClientConfig.shouldUseEmbeddedServer()) {
                LOGGER.info("Using embedded web server for default browser");
                EmbeddedWebServer server = EmbeddedWebServer.getInstance();
                if (server.isRunning()) {
                    url = server.getServerUrl();
                    LOGGER.info("Embedded server URL: {}", url);
                } else {
                    LOGGER.warn("Embedded server not running, cannot create default browser");
                    return;
                }
            } else {
                // Try dev server URL
                String devUrl = ClientConfig.getDevServerUrl();
                LOGGER.info("Attempting to use dev server: {}", devUrl);
                
                // TODO: 可以在这里添加 dev server 可用性检测
                // 现在先假定配置的 URL 是可用的
                if (devUrl != null && !devUrl.trim().isEmpty()) {
                    url = devUrl;
                } else {
                    LOGGER.warn("Dev server URL is empty, falling back to embedded server");
                    // 降级到内置服务器
                    EmbeddedWebServer server = EmbeddedWebServer.getInstance();
                    if (server.isRunning()) {
                        url = server.getServerUrl();
                        LOGGER.info("Fallback to embedded server URL: {}", url);
                    } else {
                        LOGGER.error("Both dev server and embedded server unavailable");
                        return;
                    }
                }
            }
            
            // Validate URL
            if (url == null || url.trim().isEmpty()) {
                LOGGER.error("Cannot create default browser: URL is empty");
                return;
            }
            
            LOGGER.info("Creating default browser with URL: {}", url);
            
            // Create default browser
            MiracleBrowser browser = BrowserManager.getInstance().createBrowser(
                BrowserManager.DEFAULT_BROWSER_NAME, 
                url
            );
            
            if (browser != null) {
                LOGGER.info("Default browser created successfully, loading: {}", url);
            } else {
                LOGGER.error("Failed to create default browser (browser is null)");
            }
        } catch (Exception e) {
            LOGGER.error("Error creating default browser", e);
        }
    }
    
    /**
     * Initialize HUD overlay browser for communication HUD
     * This is a separate browser instance that renders transparently over the game
     */
    private void initHudOverlay() {
        try {
            String baseUrl = null;
            
            if (ClientConfig.shouldUseEmbeddedServer()) {
                EmbeddedWebServer server = EmbeddedWebServer.getInstance();
                if (server.isRunning()) {
                    baseUrl = server.getServerUrl();
                }
            } else {
                baseUrl = ClientConfig.getDevServerUrl();
            }
            
            if (baseUrl == null || baseUrl.trim().isEmpty()) {
                LOGGER.warn("Cannot initialize HUD overlay: no URL available");
                return;
            }
            
            // HUD Overlay 加载专用的 HUD 页面，而不是首页
            // 这个页面会渲染透明的通讯 HUD（像素风 Toast）
            String hudUrl = baseUrl + "#/hud/sidebar";
            
            // Initialize with full screen size (透明背景，覆盖整个屏幕)
            int width = ClientConfig.getBrowserWidth();
            int height = ClientConfig.getBrowserHeight();
            
            BrowserOverlay overlay = BrowserOverlay.getInstance();
            overlay.init(hudUrl, width, height);
            
            // Register overlay to Forge event bus for rendering
            MinecraftForge.EVENT_BUS.register(overlay);
            
            LOGGER.info("HUD overlay browser initialized and registered: {} ({}x{})", hudUrl, width, height);
        } catch (Exception e) {
            LOGGER.error("Error initializing HUD overlay", e);
        }
    }
    
    /**
     * 从 CEF 请求中提取 POST 数据
     */
    private String extractPostData(org.cef.network.CefRequest request) {
        try {
            org.cef.network.CefPostData postData = request.getPostData();
            if (postData == null) {
                return "{}";
            }
            
            java.util.Vector<org.cef.network.CefPostDataElement> elements = new java.util.Vector<>();
            postData.getElements(elements);
            
            if (elements.isEmpty()) {
                return "{}";
            }
            
            StringBuilder sb = new StringBuilder();
            for (org.cef.network.CefPostDataElement element : elements) {
                int size = element.getBytesCount();
                if (size > 0) {
                    byte[] bytes = new byte[size];
                    element.getBytes(size, bytes);
                    sb.append(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            
            String result = sb.toString();
            return result.isEmpty() ? "{}" : result;
        } catch (Exception e) {
            LOGGER.warn("Failed to extract POST data", e);
            return "{}";
        }
    }
    
    /**
     * Load complete - all mods have been loaded
     * Start config watcher here
     */
    private void loadComplete(final FMLLoadCompleteEvent event) {
        LOGGER.info("Miracle Bridge load complete");
        
        event.enqueueWork(() -> {
            // Initialize config snapshot
            ConfigReloader.initializeSnapshot();
            
            // Start config file watcher
            ConfigWatcher.getInstance().start();
            
            LOGGER.info("Config watcher started");
        });
    }
    
    /**
     * Server stopping event handler
     */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("Miracle Bridge cleaning up resources...");
        
        // Stop config watcher
        ConfigWatcher.getInstance().stop();
        
        // Stop browser console logger
        BrowserConsoleLogger.getInstance().stop();
        
        // Stop embedded web server
        EmbeddedWebServer.getInstance().stop();
        
        // Close all browsers
        BrowserManager.getInstance().closeAll();
        
        // Shutdown thread scheduler
        ThreadScheduler.shutdown();
        
        LOGGER.info("Resource cleanup completed");
    }
    
    /**
     * 获取模组实例
     */
    public static MiracleBridge getInstance() {
        return instance;
    }
    
    /**
     * 获取主浏览器的 BridgeAPI 实例
     * 供外部模组（如 Anima）调用以注册处理器和推送事件
     * 
     * @return BridgeAPI 实例，如果主浏览器不存在则返回 null
     */
    @javax.annotation.Nullable
    public static com.originofmiracles.miraclebridge.bridge.BridgeAPI getBridgeAPI() {
        MiracleBrowser browser = BrowserManager.getInstance().getBrowser(BrowserManager.DEFAULT_BROWSER_NAME);
        if (browser != null) {
            return browser.getBridgeAPI();
        }
        return null;
    }
    
    /**
     * 获取指定浏览器的 BridgeAPI 实例
     * 
     * @param browserName 浏览器名称
     * @return BridgeAPI 实例，如果浏览器不存在则返回 null
     */
    @javax.annotation.Nullable
    public static com.originofmiracles.miraclebridge.bridge.BridgeAPI getBridgeAPI(String browserName) {
        MiracleBrowser browser = BrowserManager.getInstance().getBrowser(browserName);
        if (browser != null) {
            return browser.getBridgeAPI();
        }
        return null;
    }
    
    /**
     * 获取浏览器管理器实例
     * 
     * @return BrowserManager 单例
     */
    public static BrowserManager getBrowserManager() {
        return BrowserManager.getInstance();
    }
}
