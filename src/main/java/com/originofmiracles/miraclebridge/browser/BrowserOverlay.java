package com.originofmiracles.miraclebridge.browser;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.originofmiracles.miraclebridge.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

import javax.annotation.Nullable;

/**
 * HUD 叠加层浏览器
 * 
 * 在游戏画面上方渲染透明浏览器，适用于：
 * - 对话气泡
 * - 状态面板
 * - 通知提示
 * - 悬浮菜单
 * 
 * 输入模式：
 * - PASSTHROUGH: 输入透传给游戏
 * - CAPTURE: 拦截输入到浏览器
 */
public class BrowserOverlay {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * 输入模式
     */
    public enum InputMode {
        /** 输入透传给游戏 */
        PASSTHROUGH,
        /** 拦截输入到浏览器 */
        CAPTURE
    }
    
    private static volatile BrowserOverlay instance;
    private static final Object LOCK = new Object();
    
    @Nullable
    private MiracleBrowser overlayBrowser;
    private final String browserName = "overlay";
    
    private boolean visible = false;
    private InputMode inputMode = InputMode.PASSTHROUGH;
    
    // 位置和尺寸（屏幕坐标系，0-1 范围）
    private float x = 0f;
    private float y = 0f;
    private float widthPercent = 1f;
    private float heightPercent = 1f;
    
    // 透明度
    private float alpha = 1f;
    
    private BrowserOverlay() {}
    
    public static BrowserOverlay getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new BrowserOverlay();
                }
            }
        }
        return instance;
    }
    
    /**
     * 初始化叠加层浏览器
     * 
     * @param url 初始 URL
     * @param width 浏览器宽度
     * @param height 浏览器高度
     */
    public void init(String url, int width, int height) {
        if (overlayBrowser != null) {
            BrowserManager.getInstance().closeBrowser(browserName);
        }
        
        overlayBrowser = BrowserManager.getInstance().createBrowser(
                browserName,
                url,
                width,
                height,
                true // 透明背景
        );
        
        if (overlayBrowser != null) {
            LOGGER.info("叠加层浏览器已初始化: {} ({}x{})", url, width, height);
        }
    }
    
    /**
     * 显示叠加层
     */
    public void show() {
        visible = true;
        LOGGER.info("叠加层已显示, visible={}, browser={}, isReady={}", 
            visible, 
            overlayBrowser != null ? "存在" : "null",
            overlayBrowser != null ? overlayBrowser.isReady() : "N/A");
    }
    
    /**
     * 隐藏叠加层
     */
    public void hide() {
        visible = false;
        LOGGER.debug("叠加层已隐藏");
    }
    
    /**
     * 切换显示状态
     */
    public void toggle() {
        if (visible) {
            hide();
        } else {
            show();
        }
    }
    
    /**
     * 设置位置和尺寸（百分比）
     */
    public void setBounds(float x, float y, float widthPercent, float heightPercent) {
        this.x = Math.max(0, Math.min(1, x));
        this.y = Math.max(0, Math.min(1, y));
        this.widthPercent = Math.max(0.1f, Math.min(1, widthPercent));
        this.heightPercent = Math.max(0.1f, Math.min(1, heightPercent));
    }
    
    /**
     * 设置透明度
     */
    public void setAlpha(float alpha) {
        this.alpha = Math.max(0, Math.min(1, alpha));
    }
    
    /**
     * 设置输入模式
     */
    public void setInputMode(InputMode mode) {
        this.inputMode = mode;
        LOGGER.debug("叠加层输入模式: {}", mode);
    }
    
    /**
     * 导航到新 URL
     */
    public void navigate(String url) {
        if (overlayBrowser != null) {
            overlayBrowser.loadUrl(url);
        }
    }
    
    /**
     * 执行 JavaScript
     */
    public void executeJs(String script) {
        if (overlayBrowser != null) {
            overlayBrowser.executeJavaScript(script);
        }
    }
    
    /**
     * 推送事件到 HUD 浏览器
     * 
     * @param eventType 事件类型
     * @param jsonData JSON 数据
     */
    public void pushEvent(String eventType, String jsonData) {
        if (overlayBrowser == null) {
            LOGGER.warn("[BrowserOverlay] 无法推送事件，浏览器未初始化");
            return;
        }
        
        // 转义 JSON 字符串中的特殊字符
        String escapedJson = jsonData
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
        
        String jsCall = String.format(
            "(function() { " +
            "  try { " +
            "    var data = JSON.parse('%s'); " +
            "    window.dispatchEvent(new CustomEvent('%s', { detail: data })); " +
            "    console.log('[BrowserOverlay] Event dispatched: %s'); " +
            "  } catch(e) { " +
            "    console.error('[BrowserOverlay] Event dispatch error:', e); " +
            "  } " +
            "})()",
            escapedJson,
            eventType,
            eventType
        );
        
        overlayBrowser.executeJavaScript(jsCall);
        LOGGER.info("[BrowserOverlay] 事件已推送到 HUD: {}", eventType);
    }
    
    // 用于诊断的标志
    private boolean firstRenderLogged = false;
    
    /**
     * 渲染叠加层（由事件系统调用）
     */
    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        // 诊断：为什么不渲染
        if (visible && !firstRenderLogged) {
            LOGGER.info("[BrowserOverlay] 渲染检查: visible={}, browser={}, isReady={}", 
                visible, overlayBrowser != null, overlayBrowser != null ? overlayBrowser.isReady() : false);
            firstRenderLogged = true;
        }
        
        if (!visible || overlayBrowser == null || !overlayBrowser.isReady()) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        
        // 计算实际位置和尺寸
        int renderX = (int) (x * screenWidth);
        int renderY = (int) (y * screenHeight);
        int renderWidth = (int) (widthPercent * screenWidth);
        int renderHeight = (int) (heightPercent * screenHeight);
        
        // 设置透明度
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        
        // 渲染浏览器
        overlayBrowser.render(renderX, renderY, renderWidth, renderHeight);
        
        // 恢复颜色
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        
        if (ClientConfig.isDebugEnabled()) {
            // 调试模式下绘制边框
            // drawDebugBorder(event.getGuiGraphics(), renderX, renderY, renderWidth, renderHeight);
        }
    }
    
    /**
     * 处理鼠标点击（当 inputMode == CAPTURE 时）
     * 
     * @return 是否消费了事件
     */
    public boolean handleMouseClick(double mouseX, double mouseY, int button) {
        if (!visible || inputMode != InputMode.CAPTURE || overlayBrowser == null) {
            return false;
        }
        
        // 检查点击是否在叠加层范围内
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        
        int renderX = (int) (x * screenWidth);
        int renderY = (int) (y * screenHeight);
        int renderWidth = (int) (widthPercent * screenWidth);
        int renderHeight = (int) (heightPercent * screenHeight);
        
        if (mouseX >= renderX && mouseX <= renderX + renderWidth &&
            mouseY >= renderY && mouseY <= renderY + renderHeight) {
            
            // 防止除零错误
            int browserWidth = overlayBrowser.getWidth();
            int browserHeight = overlayBrowser.getHeight();
            if (renderWidth <= 0 || renderHeight <= 0 || browserWidth <= 0 || browserHeight <= 0) {
                return false;
            }
            
            // 转换为浏览器坐标
            int browserX = (int) ((mouseX - renderX) * browserWidth / renderWidth);
            int browserY = (int) ((mouseY - renderY) * browserHeight / renderHeight);
            
            overlayBrowser.sendMousePress(browserX, browserY, button);
            return true;
        }
        
        return false;
    }
    
    /**
     * 处理鼠标释放
     */
    public boolean handleMouseRelease(double mouseX, double mouseY, int button) {
        if (!visible || inputMode != InputMode.CAPTURE || overlayBrowser == null) {
            return false;
        }
        
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        
        int renderX = (int) (x * screenWidth);
        int renderY = (int) (y * screenHeight);
        int renderWidth = (int) (widthPercent * screenWidth);
        int renderHeight = (int) (heightPercent * screenHeight);
        
        if (mouseX >= renderX && mouseX <= renderX + renderWidth &&
            mouseY >= renderY && mouseY <= renderY + renderHeight) {
            
            // 防止除零错误
            int browserWidth = overlayBrowser.getWidth();
            int browserHeight = overlayBrowser.getHeight();
            if (renderWidth <= 0 || renderHeight <= 0 || browserWidth <= 0 || browserHeight <= 0) {
                return false;
            }
            
            int browserX = (int) ((mouseX - renderX) * browserWidth / renderWidth);
            int browserY = (int) ((mouseY - renderY) * browserHeight / renderHeight);
            
            overlayBrowser.sendMouseRelease(browserX, browserY, button);
            return true;
        }
        
        return false;
    }
    
    /**
     * 关闭叠加层
     */
    public void close() {
        hide();
        if (overlayBrowser != null) {
            BrowserManager.getInstance().closeBrowser(browserName);
            overlayBrowser = null;
        }
    }
    
    // ==================== Getter ====================
    
    public boolean isVisible() {
        return visible;
    }
    
    public InputMode getInputMode() {
        return inputMode;
    }
    
    @Nullable
    public MiracleBrowser getBrowser() {
        return overlayBrowser;
    }
}
