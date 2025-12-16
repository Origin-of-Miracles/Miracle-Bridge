package com.originofmiracles.miraclebridge;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
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
        
        // Register setup methods
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        
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
            // TODO: 初始化客户端 ↔ 服务端通信通道
            
            LOGGER.info("网络处理器已注册");
        });
    }
    
    /**
     * 仅客户端设置
     */
    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("Miracle Bridge 客户端设置");
        
        event.enqueueWork(() -> {
            // 初始化 MCEF 浏览器引擎
            // TODO: 初始化 MiracleBrowser 管理器
            
            LOGGER.info("客户端组件已初始化");
        });
    }
}
