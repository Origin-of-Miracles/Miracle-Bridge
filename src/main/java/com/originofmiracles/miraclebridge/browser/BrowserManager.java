package com.originofmiracles.miraclebridge.browser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.originofmiracles.miraclebridge.config.ClientConfig;

/**
 * 浏览器实例管理器
 * 处理命名浏览器窗口的生命周期和注册
 */
public class BrowserManager {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private static BrowserManager instance;
    
    /**
     * 默认浏览器名称
     */
    public static final String DEFAULT_BROWSER_NAME = "main";
    
    /**
     * 浏览器实例映射表（线程安全）
     */
    private final Map<String, MiracleBrowser> browsers = new ConcurrentHashMap<>();
    
    /**
     * 浏览器创建顺序列表
     */
    private final List<String> browserOrder = new ArrayList<>();
    
    /**
     * 当前选中的浏览器名称
     */
    @Nullable
    private String currentBrowserName = null;
    
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
            
            // 维护顺序：main 始终在第一位
            if (DEFAULT_BROWSER_NAME.equals(name)) {
                browserOrder.remove(name);
                browserOrder.add(0, name);
            } else if (!browserOrder.contains(name)) {
                browserOrder.add(name);
            }
            
            // 如果是第一个浏览器或创建的是 main，自动选中
            if (currentBrowserName == null || DEFAULT_BROWSER_NAME.equals(name)) {
                currentBrowserName = name;
            }
            
            // 发布浏览器创建事件，供外部模组注册处理器
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                    new com.originofmiracles.miraclebridge.event.BrowserCreatedEvent(name, browser));
            
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
     * 获取当前选中的浏览器
     */
    @Nullable
    public MiracleBrowser getCurrentBrowser() {
        if (currentBrowserName != null) {
            return browsers.get(currentBrowserName);
        }
        return null;
    }
    
    /**
     * 获取当前选中的浏览器名称
     */
    @Nullable
    public String getCurrentBrowserName() {
        return currentBrowserName;
    }
    
    /**
     * 选择当前使用的浏览器
     * @param name 浏览器名称
     * @return 是否选择成功
     */
    public boolean selectBrowser(String name) {
        if (browsers.containsKey(name)) {
            currentBrowserName = name;
            LOGGER.info("已选择浏览器: {}", name);
            return true;
        }
        LOGGER.warn("浏览器不存在: {}", name);
        return false;
    }
    
    /**
     * 关闭并注销浏览器
     */
    public void closeBrowser(String name) {
        MiracleBrowser browser = browsers.remove(name);
        if (browser != null) {
            browser.close();
            browserOrder.remove(name);
            
            // 如果关闭的是当前选中的浏览器，切换到下一个
            if (name.equals(currentBrowserName)) {
                if (!browserOrder.isEmpty()) {
                    currentBrowserName = browserOrder.get(0);
                } else {
                    currentBrowserName = null;
                }
            }
            
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
        browserOrder.clear();
        currentBrowserName = null;
    }
    
    /**
     * 获取当前注册的浏览器数量
     */
    public int getBrowserCount() {
        return browsers.size();
    }
    
    /**
     * 获取所有已注册的浏览器名称（按创建顺序，main 优先）
     */
    public List<String> getBrowserNames() {
        return new ArrayList<>(browserOrder);
    }
    
    /**
     * 检查指定名称的浏览器是否存在
     */
    public boolean hasBrowser(String name) {
        return browsers.containsKey(name);
    }
}
