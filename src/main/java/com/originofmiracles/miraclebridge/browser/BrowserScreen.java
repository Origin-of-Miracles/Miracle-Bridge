package com.originofmiracles.miraclebridge.browser;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
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
     * 实际像素尺寸（考虑 GUI scale）
     */
    private int actualWidth;
    private int actualHeight;
    
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
        
        // 获取实际像素尺寸（窗口分辨率，不受 GUI scale 影响）
        Minecraft mc = Minecraft.getInstance();
        actualWidth = mc.getWindow().getWidth();
        actualHeight = mc.getWindow().getHeight();
        
        if (browser != null) {
            // 使用实际像素尺寸调整浏览器，避免模糊
            browser.resize(actualWidth, actualHeight);
            LOGGER.debug("浏览器界面已初始化: GUI={}x{}, 实际像素={}x{}", width, height, actualWidth, actualHeight);
        }
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 不填充背景，让周围区域透明显示游戏画面
        // 如果需要半透明黑色边框效果，可以取消下面的注释：
        // guiGraphics.fill(0, 0, width, height, 0x80000000);
        
        // 获取显示比例（从配置读取，支持热重载）
        double displayScale = com.originofmiracles.miraclebridge.config.ClientConfig.getBrowserDisplayScale();
        
        // 计算居中显示的位置和尺寸
        int renderWidth = (int) (width * displayScale);
        int renderHeight = (int) (height * displayScale);
        int renderX = (width - renderWidth) / 2;
        int renderY = (height - renderHeight) / 2;
        
        // 渲染浏览器
        if (browser != null && browser.isReady()) {
            // 检查纹理ID是否有效
            int textureId = browser.getTextureId();
            if (textureId > 0) {
                browser.render(renderX, renderY, renderWidth, renderHeight);
            } else {
                guiGraphics.drawCenteredString(font, "Browser texture not ready (ID: " + textureId + ")...", width / 2, height / 2, 0xFFFF00);
            }
        } else {
            // 浏览器未就绪时显示加载信息
            String status = browser == null ? "Browser is null" : "Browser not ready";
            guiGraphics.drawCenteredString(font, "Loading browser... (" + status + ")", width / 2, height / 2, 0xFFFFFF);
        }
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    @Override
    public void tick() {
        super.tick();
        // 可以在这里添加周期性任务，如轮询消息队列
    }
    
    // ==================== 鼠标事件 ====================
    
    /**
     * 获取当前的显示比例
     */
    private double getDisplayScale() {
        return com.originofmiracles.miraclebridge.config.ClientConfig.getBrowserDisplayScale();
    }
    
    /**
     * 获取浏览器渲染区域的起始 X 坐标（GUI 坐标系）
     */
    private int getRenderX() {
        double displayScale = getDisplayScale();
        int renderWidth = (int) (width * displayScale);
        return (width - renderWidth) / 2;
    }
    
    /**
     * 获取浏览器渲染区域的起始 Y 坐标（GUI 坐标系）
     */
    private int getRenderY() {
        double displayScale = getDisplayScale();
        int renderHeight = (int) (height * displayScale);
        return (height - renderHeight) / 2;
    }
    
    /**
     * 将 GUI 坐标转换为浏览器像素坐标
     * GUI 坐标受 GUI scale 和显示比例影响，需要转换为实际像素
     * 坐标是相对于浏览器渲染区域的，并考虑了居中显示的偏移
     */
    private int toActualX(double guiX) {
        double displayScale = getDisplayScale();
        int renderX = getRenderX();
        int renderWidth = (int) (width * displayScale);
        
        // 将鼠标位置相对于浏览器渲染区域计算，然后映射到浏览器实际像素
        double relativeX = guiX - renderX;
        return (int) (relativeX * actualWidth / renderWidth);
    }
    
    private int toActualY(double guiY) {
        double displayScale = getDisplayScale();
        int renderY = getRenderY();
        int renderHeight = (int) (height * displayScale);
        
        // 将鼠标位置相对于浏览器渲染区域计算，然后映射到浏览器实际像素
        double relativeY = guiY - renderY;
        return (int) (relativeY * actualHeight / renderHeight);
    }
    
    /**
     * 检查鼠标是否在浏览器渲染区域内
     */
    private boolean isInBrowserArea(double mouseX, double mouseY) {
        double displayScale = getDisplayScale();
        int renderX = getRenderX();
        int renderY = getRenderY();
        int renderWidth = (int) (width * displayScale);
        int renderHeight = (int) (height * displayScale);
        
        return mouseX >= renderX && mouseX < renderX + renderWidth
                && mouseY >= renderY && mouseY < renderY + renderHeight;
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (browser != null && isInBrowserArea(mouseX, mouseY)) {
            browser.sendMousePress(toActualX(mouseX), toActualY(mouseY), button);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (browser != null && isInBrowserArea(mouseX, mouseY)) {
            browser.sendMouseRelease(toActualX(mouseX), toActualY(mouseY), button);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (browser != null && isInBrowserArea(mouseX, mouseY)) {
            browser.sendMouseMove(toActualX(mouseX), toActualY(mouseY));
        }
        super.mouseMoved(mouseX, mouseY);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (browser != null && isInBrowserArea(mouseX, mouseY)) {
            browser.sendMouseMove(toActualX(mouseX), toActualY(mouseY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (browser != null && isInBrowserArea(mouseX, mouseY)) {
            // 使用配置的滚轮灵敏度系数（默认 40，原来是 120 太快了）
            int scrollDelta = (int) (delta * com.originofmiracles.miraclebridge.config.ClientConfig.getScrollSensitivity());
            browser.sendMouseWheel(toActualX(mouseX), toActualY(mouseY), scrollDelta, inputHandler.getCurrentModifiers());
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
