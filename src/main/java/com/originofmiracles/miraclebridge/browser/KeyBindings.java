package com.originofmiracles.miraclebridge.browser;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import com.originofmiracles.miraclebridge.MiracleBridge;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/**
 * 快捷键注册和处理
 * 
 * 注册的快捷键：
 * - F11: 切换全屏浏览器
 * - F12: 打开开发者工具（如果支持）
 * - Ctrl+B: 打开 Bridge 控制面板
 * - Ctrl+Shift+B: 切换叠加层显示
 */
@Mod.EventBusSubscriber(modid = MiracleBridge.MOD_ID, value = Dist.CLIENT)
public class KeyBindings {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * 快捷键类别
     */
    public static final String KEY_CATEGORY = "key.categories.miraclebridge";
    
    /**
     * 切换全屏浏览器
     */
    public static KeyMapping KEY_TOGGLE_BROWSER;
    
    /**
     * 开发者工具
     */
    public static KeyMapping KEY_DEV_TOOLS;
    
    /**
     * Bridge 控制面板
     */
    public static KeyMapping KEY_BRIDGE_PANEL;
    
    /**
     * 切换叠加层
     */
    public static KeyMapping KEY_TOGGLE_OVERLAY;
    
    /**
     * 刷新浏览器
     */
    public static KeyMapping KEY_REFRESH_BROWSER;
    
    /**
     * 注册快捷键
     */
    @Mod.EventBusSubscriber(modid = MiracleBridge.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            LOGGER.info("注册 Miracle Bridge 快捷键");
            
            // F11 - 切换全屏浏览器
            KEY_TOGGLE_BROWSER = new KeyMapping(
                    "key.miraclebridge.toggle_browser",
                    KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_F11,
                    KEY_CATEGORY
            );
            event.register(KEY_TOGGLE_BROWSER);
            
            // F12 - 开发者工具
            KEY_DEV_TOOLS = new KeyMapping(
                    "key.miraclebridge.dev_tools",
                    KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_F12,
                    KEY_CATEGORY
            );
            event.register(KEY_DEV_TOOLS);
            
            // B - Bridge 控制面板（需要 Ctrl）
            KEY_BRIDGE_PANEL = new KeyMapping(
                    "key.miraclebridge.bridge_panel",
                    KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_B,
                    KEY_CATEGORY
            );
            event.register(KEY_BRIDGE_PANEL);
            
            // O - 切换叠加层
            KEY_TOGGLE_OVERLAY = new KeyMapping(
                    "key.miraclebridge.toggle_overlay",
                    KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_O,
                    KEY_CATEGORY
            );
            event.register(KEY_TOGGLE_OVERLAY);
            
            // F5 - 刷新浏览器
            KEY_REFRESH_BROWSER = new KeyMapping(
                    "key.miraclebridge.refresh_browser",
                    KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_F5,
                    KEY_CATEGORY
            );
            event.register(KEY_REFRESH_BROWSER);
            
            LOGGER.info("已注册 5 个快捷键");
        }
    }
    
    /**
     * 处理按键事件
     */
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        
        // 只处理按下事件
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        
        // 在游戏中才处理
        if (mc.screen != null && !(mc.screen instanceof BrowserScreen)) {
            return;
        }
        
        // 检查修饰键
        boolean ctrlDown = (event.getModifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shiftDown = (event.getModifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        
        // F11 - 切换全屏浏览器
        if (KEY_TOGGLE_BROWSER.matches(event.getKey(), event.getScanCode())) {
            handleToggleBrowser(mc);
        }
        
        // F12 - 开发者工具
        else if (KEY_DEV_TOOLS.matches(event.getKey(), event.getScanCode())) {
            handleDevTools();
        }
        
        // B - 打开浏览器（无需 Ctrl）
        else if (!ctrlDown && !shiftDown && KEY_BRIDGE_PANEL.matches(event.getKey(), event.getScanCode())) {
            handleToggleBrowser(mc);
        }
        
        // Ctrl+B - Bridge 控制面板
        else if (ctrlDown && !shiftDown && KEY_BRIDGE_PANEL.matches(event.getKey(), event.getScanCode())) {
            handleBridgePanel(mc);
        }
        
        // Ctrl+Shift+B 或 O - 切换叠加层
        else if ((ctrlDown && shiftDown && event.getKey() == GLFW.GLFW_KEY_B) ||
                 KEY_TOGGLE_OVERLAY.matches(event.getKey(), event.getScanCode())) {
            handleToggleOverlay();
        }
        
        // F5 - 刷新浏览器
        else if (KEY_REFRESH_BROWSER.matches(event.getKey(), event.getScanCode())) {
            handleRefreshBrowser();
        }
    }
    
    /**
     * 切换全屏浏览器
     */
    private static void handleToggleBrowser(Minecraft mc) {
        if (mc.screen instanceof BrowserScreen) {
            mc.setScreen(null);
            LOGGER.info("关闭全屏浏览器");
            sendPlayerMessage(mc, "§a浏览器已关闭");
        } else {
            LOGGER.info("尝试打开全屏浏览器...");
            
            // 检查 MCEF 是否就绪
            if (!com.cinemamod.mcef.MCEF.isInitialized()) {
                LOGGER.error("MCEF 未初始化，无法打开浏览器");
                sendPlayerMessage(mc, "§cMCEF 未初始化，请检查 MCEF mod 是否正确安装");
                return;
            }
            
            // 获取或创建主浏览器
            MiracleBrowser browser = BrowserManager.getInstance().getBrowser("main");
            if (browser == null) {
                LOGGER.info("创建新浏览器实例...");
                browser = BrowserManager.getInstance().createBrowser("main", null);
            }
            
            if (browser != null) {
                mc.setScreen(new BrowserScreen("main", browser, true));
                LOGGER.info("全屏浏览器已打开");
            } else {
                LOGGER.error("无法创建浏览器实例");
                sendPlayerMessage(mc, "§c无法创建浏览器，请检查日志");
            }
        }
    }
    
    /**
     * 向玩家发送消息
     */
    private static void sendPlayerMessage(Minecraft mc, String message) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), true);
        }
    }
    
    /**
     * 打开开发者工具
     */
    private static void handleDevTools() {
        MiracleBrowser browser = BrowserManager.getInstance().getBrowser("main");
        if (browser != null && browser.getRawBrowser() != null) {
            // MCEF 可能支持开发者工具
            // browser.getRawBrowser().openDevTools();
            LOGGER.info("开发者工具（如果 MCEF 支持）");
        }
    }
    
    /**
     * 打开 Bridge 控制面板
     */
    private static void handleBridgePanel(Minecraft mc) {
        // 导航到控制面板页面
        MiracleBrowser browser = BrowserManager.getInstance().getBrowser("main");
        if (browser != null) {
            browser.loadUrl("bridge://panel");
            mc.setScreen(new BrowserScreen("main", browser, true));
            LOGGER.debug("打开 Bridge 控制面板");
        }
    }
    
    /**
     * 切换叠加层显示
     */
    private static void handleToggleOverlay() {
        BrowserOverlay overlay = BrowserOverlay.getInstance();
        overlay.toggle();
        LOGGER.debug("叠加层: {}", overlay.isVisible() ? "显示" : "隐藏");
    }
    
    /**
     * 刷新当前浏览器
     */
    private static void handleRefreshBrowser() {
        MiracleBrowser browser = BrowserManager.getInstance().getBrowser("main");
        if (browser != null && browser.getRawBrowser() != null) {
            String url = browser.getRawBrowser().getURL();
            browser.loadUrl(url);
            LOGGER.debug("刷新浏览器: {}", url);
        }
    }
}
