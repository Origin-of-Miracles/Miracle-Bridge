package com.originofmiracles.miraclebridge.browser;
import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import javax.annotation.Nullable;

/**
 * 全屏浏览器界面
 * 
 * 提供独占式浏览器显示，捕获所有输入事件并转发到浏览器。
 * 适用于：
 * - 主 UI 界面
 * - 设置界面
 * - 全屏对话界面
 */
public class BrowserScreen extends Screen {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    @Nullable
    private final MiracleBrowser browser;
    private final String browserName;
    private final boolean allowEscapeClose;
    
    /**
     * 输入处理器
     */
    private final InputHandler inputHandler;
    
    /**
     * 创建浏览器界面
     * 
     * @param browserName 浏览器名称（用于从 BrowserManager 获取）
     * @param allowEscapeClose 是否允许 ESC 键关闭界面
     */
    public BrowserScreen(String browserName, boolean allowEscapeClose) {
        super(Component.literal("Browser"));
        this.browserName = browserName;
        this.browser = BrowserManager.getInstance().getBrowser(browserName);
        this.allowEscapeClose = allowEscapeClose;
        this.inputHandler = new InputHandler();
        
        if (browser == null) {
            LOGGER.warn("浏览器不存在: {}", browserName);
        }
    }
    
    /**
     * 使用已有浏览器创建界面
     */
    public BrowserScreen(String browserName, MiracleBrowser browser, boolean allowEscapeClose) {
        super(Component.literal("Browser"));
        this.browserName = browserName;
        this.browser = browser;
        this.allowEscapeClose = allowEscapeClose;
        this.inputHandler = new InputHandler();
    }
    
    @Override
    protected void init() {
        super.init();
        
        if (browser != null) {
            // 调整浏览器尺寸以匹配屏幕
            browser.resize(width, height);
            LOGGER.debug("浏览器界面已初始化: {}x{}", width, height);
        }
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 渲染黑色背景
        renderBackground(guiGraphics);
        
        // 渲染浏览器
        if (browser != null && browser.isReady()) {
            browser.render(0, 0, width, height);
        } else {
            // 浏览器未就绪时显示加载信息
            guiGraphics.drawCenteredString(font, "Loading browser...", width / 2, height / 2, 0xFFFFFF);
        }
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    @Override
    public void tick() {
        super.tick();
        // 可以在这里添加周期性任务，如轮询消息队列
    }
    
    // ==================== 鼠标事件 ====================
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (browser != null) {
            int x = inputHandler.scaleX(mouseX, width, browser.getWidth());
            int y = inputHandler.scaleY(mouseY, height, browser.getHeight());
            browser.sendMousePress(x, y, button);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (browser != null) {
            int x = inputHandler.scaleX(mouseX, width, browser.getWidth());
            int y = inputHandler.scaleY(mouseY, height, browser.getHeight());
            browser.sendMouseRelease(x, y, button);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (browser != null) {
            int x = inputHandler.scaleX(mouseX, width, browser.getWidth());
            int y = inputHandler.scaleY(mouseY, height, browser.getHeight());
            browser.sendMouseMove(x, y);
        }
        super.mouseMoved(mouseX, mouseY);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (browser != null) {
            int x = inputHandler.scaleX(mouseX, width, browser.getWidth());
            int y = inputHandler.scaleY(mouseY, height, browser.getHeight());
            browser.sendMouseMove(x, y);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (browser != null) {
            int x = inputHandler.scaleX(mouseX, width, browser.getWidth());
            int y = inputHandler.scaleY(mouseY, height, browser.getHeight());
            int scrollDelta = (int) (delta * 120); // CEF 使用的滚轮单位
            browser.sendMouseWheel(x, y, scrollDelta, inputHandler.getCurrentModifiers());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
    
    // ==================== 键盘事件 ====================
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC 键处理
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (allowEscapeClose) {
                onClose();
                return true;
            }
            // 如果不允许 ESC 关闭，仍然传递给浏览器
        }
        
        // F5 刷新
        if (keyCode == GLFW.GLFW_KEY_F5 && browser != null) {
            browser.loadUrl(browser.getRawBrowser() != null ? browser.getRawBrowser().getURL() : "");
            return true;
        }
        
        // 传递给浏览器
        if (browser != null) {
            browser.sendKeyPress(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (browser != null) {
            browser.sendKeyRelease(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (browser != null) {
            browser.sendKeyTyped(codePoint, modifiers);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }
    
    // ==================== 生命周期 ====================
    
    @Override
    public void onClose() {
        LOGGER.debug("浏览器界面关闭: {}", browserName);
        super.onClose();
    }
    
    @Override
    public void removed() {
        super.removed();
        // 界面移除时不关闭浏览器，只是隐藏
    }
    
    @Override
    public boolean isPauseScreen() {
        return false; // 不暂停游戏
    }
    
    // ==================== Getter ====================
    
    @Nullable
    public MiracleBrowser getBrowser() {
        return browser;
    }
    
    public String getBrowserName() {
        return browserName;
    }
}
