package com.originofmiracles.miraclebridge.config;

import java.nio.file.Path;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.originofmiracles.miraclebridge.MiracleBridge;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * 模组配置统一注册入口
 * 
 * 配置文件位置：
 * - 客户端：config/miraclebridge-client.toml
 * - 服务端：config/miraclebridge-server.toml
 */
public class ModConfigs {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * 客户端配置文件名
     */
    public static final String CLIENT_CONFIG_NAME = MiracleBridge.MOD_ID + "-client.toml";
    
    /**
     * 服务端配置文件名
     */
    public static final String SERVER_CONFIG_NAME = MiracleBridge.MOD_ID + "-server.toml";
    
    private static boolean registered = false;
    
    /**
     * 注册所有配置
     * 应在模组构造函数中调用
     */
    public static void register() {
        if (registered) {
            LOGGER.warn("配置已注册，跳过重复注册");
            return;
        }
        
        ModLoadingContext context = ModLoadingContext.get();
        
        // 注册客户端配置
        context.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC, CLIENT_CONFIG_NAME);
        LOGGER.info("已注册客户端配置: {}", CLIENT_CONFIG_NAME);
        
        // 注册服务端配置
        context.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC, SERVER_CONFIG_NAME);
        LOGGER.info("已注册服务端配置: {}", SERVER_CONFIG_NAME);
        
        registered = true;
    }
    
    /**
     * 获取配置目录路径
     */
    public static Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }
    
    /**
     * 获取客户端配置文件路径
     */
    public static Path getClientConfigPath() {
        return getConfigDir().resolve(CLIENT_CONFIG_NAME);
    }
    
    /**
     * 获取服务端配置文件路径
     */
    public static Path getServerConfigPath() {
        return getConfigDir().resolve(SERVER_CONFIG_NAME);
    }
    
    /**
     * 检查配置是否已注册
     */
    public static boolean isRegistered() {
        return registered;
    }
    
    private ModConfigs() {}
}
