package com.originofmiracles.miraclebridge.event;

import com.originofmiracles.miraclebridge.browser.MiracleBrowser;

import net.minecraftforge.eventbus.api.Event;

/**
 * 浏览器创建完成事件
 * 
 * 当 BrowserManager 创建新浏览器后触发此事件
 * 外部模组可以监听此事件来注册 BridgeAPI 处理器
 */
public class BrowserCreatedEvent extends Event {
    
    private final String browserName;
    private final MiracleBrowser browser;
    
    public BrowserCreatedEvent(String browserName, MiracleBrowser browser) {
        this.browserName = browserName;
        this.browser = browser;
    }
    
    /**
     * 获取浏览器名称
     */
    public String getBrowserName() {
        return browserName;
    }
    
    /**
     * 获取浏览器实例
     */
    public MiracleBrowser getBrowser() {
        return browser;
    }
}
