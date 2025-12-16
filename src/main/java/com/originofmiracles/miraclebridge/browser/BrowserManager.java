package com.originofmiracles.miraclebridge.browser;

import com.mojang.logging.LogUtils;
import com.originofmiracles.miraclebridge.config.ClientConfig;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 浏览器实例管理器
 * 处理命名浏览器窗口的生命周期和注册
 */
public class BrowserManager {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private static BrowserManager instance;
    
    /**
     * 浏览器实例映射表（线程安全）
     */
    private final Map<String, MiracleBrowser> browsers = new ConcurrentHashMap<>();
    
    private BrowserManager() {
        // 私有构造函数，单例模式
    }
    
    public static BrowserManager getInstance() {
        if (instance == null) {
            instance = new BrowserManager();
        }
        return instance;
    }
    
    /**
     * 使用默认配置创建浏览器
     * 
     * @param name 浏览器标识符
     * @param url 初始 URL（如果为 null 或空，使用配置中的开发服务器 URL）
     * @return 创建的浏览器，失败时返回 null
     */
    @Nullable
    public MiracleBrowser createBrowser(String name, @Nullable String url) {
        String actualUrl = (url == null || url.isEmpty()) ? ClientConfig.getDevServerUrl() : url;
        return createBrowser(
                name,
                actualUrl,
                ClientConfig.getBrowserWidth(),
                ClientConfig.getBrowserHeight(),
                ClientConfig.BROWSER_TRANSPARENT_BACKGROUND.get()
        );
    }
    
    /**
     * 创建并注册新浏览器
     * 
     * @param name 浏览器标识符
     * @param url 初始 URL
     * @param width 视口宽度
     * @param height 视口高度
     * @param transparent 背景是否透明
     * @return 创建的浏览器，失败时返回 null
     */
    @Nullable
    public MiracleBrowser createBrowser(String name, String url, int width, int height, boolean transparent) {
        if (browsers.containsKey(name)) {
            LOGGER.warn("浏览器 '{}' 已存在，关闭旧实例", name);
            closeBrowser(name);
        }
        
        MiracleBrowser browser = new MiracleBrowser(transparent);
        if (browser.create(url, width, height)) {
            browsers.put(name, browser);
            LOGGER.info("已注册浏览器: {} -> {}", name, url);
            return browser;
        }
        
        LOGGER.error("创建浏览器失败: {}", name);
        return null;
    }
    
    /**
     * 根据名称获取已注册的浏览器
     */
    @Nullable
    public MiracleBrowser getBrowser(String name) {
        return browsers.get(name);
    }
    
    /**
     * 关闭并注销浏览器
     */
    public void closeBrowser(String name) {
        MiracleBrowser browser = browsers.remove(name);
        if (browser != null) {
            browser.close();
            LOGGER.info("已关闭浏览器: {}", name);
        }
    }
    
    /**
     * 关闭所有浏览器
     */
    public void closeAll() {
        LOGGER.info("正在关闭所有浏览器 ({})", browsers.size());
        browsers.values().forEach(MiracleBrowser::close);
        browsers.clear();
    }
    
    /**
     * 获取当前注册的浏览器数量
     */
    public int getBrowserCount() {
        return browsers.size();
    }
    
    /**
     * 获取所有已注册的浏览器名称
     */
    public java.util.Set<String> getBrowserNames() {
        return java.util.Collections.unmodifiableSet(browsers.keySet());
    }
    
    /**
     * 检查指定名称的浏览器是否存在
     */
    public boolean hasBrowser(String name) {
        return browsers.containsKey(name);
    }
}
