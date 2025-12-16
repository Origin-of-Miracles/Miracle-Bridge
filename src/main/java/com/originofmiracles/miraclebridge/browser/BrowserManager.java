package com.originofmiracles.miraclebridge.browser;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * 浏览器实例管理器
 * 处理命名浏览器窗口的生命周期和注册
 */
public class BrowserManager {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private static BrowserManager instance;
    
    private final Map<String, MiracleBrowser> browsers = new HashMap<>();
    
    private BrowserManager() {}
    
    public static BrowserManager getInstance() {
        if (instance == null) {
            instance = new BrowserManager();
        }
        return instance;
    }
    
    /**
     * 创建并注册新浏览器
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
            LOGGER.info("已注册浏览器: {}", name);
            return browser;
        }
        
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
}
