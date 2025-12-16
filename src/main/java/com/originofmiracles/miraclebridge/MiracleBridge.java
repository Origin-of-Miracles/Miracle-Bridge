package com.originofmiracles.miraclebridge;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.originofmiracles.miraclebridge.browser.BrowserManager;
import com.originofmiracles.miraclebridge.config.ClientConfig;
import com.originofmiracles.miraclebridge.config.ConfigReloader;
import com.originofmiracles.miraclebridge.config.ConfigWatcher;
import com.originofmiracles.miraclebridge.config.ModConfigs;
import com.originofmiracles.miraclebridge.network.ModNetworkHandler;
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
        
        // 延迟注册到 Forge 事件总线，避免构造函数中泄漏 this
        modEventBus.addListener((FMLCommonSetupEvent event) -> {
            MinecraftForge.EVENT_BUS.register(instance);
        });
        
        LOGGER.info("Miracle Bridge 已初始化 - 连接现实与奇迹...");
    }
    
    /**
     * 通用设置 - 在客户端和服务端都会运行
     */
    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Miracle Bridge 通用设置");
        
        event.enqueueWork(() -> {
            // 注册网络数据包
            ModNetworkHandler.register();
            
            LOGGER.info("网络处理器已注册");
        });
    }
    
    /**
     * 仅客户端设置
     */
    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("Miracle Bridge 客户端设置");
        
        event.enqueueWork(() -> {
            // 初始化 BrowserManager（使用配置中的默认值）
            BrowserManager.getInstance();
            
            // 如果配置了开发服务器 URL，记录日志
            String devUrl = ClientConfig.getDevServerUrl();
            if (devUrl != null && !devUrl.isEmpty()) {
                LOGGER.info("开发服务器 URL: {}", devUrl);
            }
            
            LOGGER.info("客户端组件已初始化");
        });
    }
    
    /**
     * 加载完成 - 所有模组都已加载
     * 在这里启动配置监听器
     */
    private void loadComplete(final FMLLoadCompleteEvent event) {
        LOGGER.info("Miracle Bridge 加载完成");
        
        event.enqueueWork(() -> {
            // 初始化配置快照
            ConfigReloader.initializeSnapshot();
            
            // 启动配置文件监听器
            ConfigWatcher.getInstance().start();
            
            LOGGER.info("配置监听器已启动");
        });
    }
    
    /**
     * 服务器停止事件处理
     */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("Miracle Bridge 正在清理资源...");
        
        // 停止配置监听器
        ConfigWatcher.getInstance().stop();
        
        // 关闭所有浏览器
        BrowserManager.getInstance().closeAll();
        
        // 关闭线程调度器
        ThreadScheduler.shutdown();
        
        LOGGER.info("资源清理完成");
    }
    
    /**
     * 获取模组实例
     */
    public static MiracleBridge getInstance() {
        return instance;
    }
}
