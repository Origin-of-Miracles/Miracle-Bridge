package com.originofmiracles.miraclebridge.browser;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;

import net.minecraft.client.renderer.GameRenderer;

/**
 * MCEF 浏览器实例的高层封装。
 * 
 * 提供简化的 API：
 * - 浏览器生命周期管理
 * - URL 导航
 * - JavaScript 执行
 * - 输入事件处理
 * - 纹理渲染
 * 
 * @see MCEFBrowser
 */
public class MiracleBrowser {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    @Nullable
    private MCEFBrowser browser;
    
    private final boolean transparent;
    private int width;
    private int height;
    
    /**
     * 创建新的浏览器封装器
     * @param transparent 浏览器背景是否透明
     */
    public MiracleBrowser(boolean transparent) {
        this.transparent = transparent;
    }
    
    /**
     * 使用指定 URL 初始化浏览器
     * @param url 初始 URL
     * @param width 浏览器宽度（像素）
     * @param height 浏览器高度（像素）
     * @return 初始化是否成功
     */
    public boolean create(String url, int width, int height) {
        if (!MCEF.isInitialized()) {
            LOGGER.error("MCEF 未初始化！无法创建浏览器。");
            return false;
        }
        
        this.width = width;
        this.height = height;
        
        try {
            MCEFBrowser createdBrowser = MCEF.createBrowser(url, transparent);
            if (createdBrowser == null) {
                LOGGER.error("MCEF.createBrowser 返回 null");
                return false;
            }
            this.browser = createdBrowser;
            createdBrowser.resize(width, height);
            LOGGER.info("浏览器已创建: {} ({}x{})", url, width, height);
            return true;
        } catch (Exception e) {
            LOGGER.error("创建浏览器失败", e);
            return false;
        }
    }
    
    /**
     * 加载新 URL
     */
    public void loadUrl(String url) {
        if (browser != null) {
            browser.loadURL(url);
            LOGGER.debug("正在加载 URL: {}", url);
        }
    }
    
    /**
     * 在浏览器上下文中执行 JavaScript 代码
     * @param script 要执行的 JavaScript 代码
     */
    public void executeJavaScript(String script) {
        if (browser != null) {
            browser.executeJavaScript(script, browser.getURL(), 0);
        }
    }
    
    /**
     * 调整浏览器视口大小
     */
    public void resize(int width, int height) {
        if (browser != null && (this.width != width || this.height != height)) {
            this.width = width;
            this.height = height;
            browser.resize(width, height);
        }
    }
    
    /**
     * 获取用于渲染的 OpenGL 纹理 ID
     * @return 纹理 ID，浏览器未就绪时返回 -1
     */
    public int getTextureId() {
        return browser != null ? browser.getRenderer().getTextureID() : -1;
    }
    
    /**
     * 在指定位置将浏览器渲染到屏幕
     * @param x 屏幕 X 位置
     * @param y 屏幕 Y 位置
     * @param renderWidth 渲染宽度
     * @param renderHeight 渲染高度
     */
    public void render(int x, int y, int renderWidth, int renderHeight) {
        if (browser == null) return;
        
        int textureId = getTextureId();
        if (textureId == -1) return;
        
        RenderSystem.setShaderTexture(0, textureId);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        
        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        
        bufferBuilder.vertex(x, y + renderHeight, 0).uv(0f, 1f).color(255, 255, 255, 255).endVertex();
        bufferBuilder.vertex(x + renderWidth, y + renderHeight, 0).uv(1f, 1f).color(255, 255, 255, 255).endVertex();
        bufferBuilder.vertex(x + renderWidth, y, 0).uv(1f, 0f).color(255, 255, 255, 255).endVertex();
        bufferBuilder.vertex(x, y, 0).uv(0f, 0f).color(255, 255, 255, 255).endVertex();
        
        BufferUploader.drawWithShader(bufferBuilder.end());
        
        RenderSystem.disableBlend();
    }
    
    // ==================== 输入事件 ====================
    
    public void sendMousePress(int x, int y, int button) {
        if (browser != null) {
            browser.sendMousePress(x, y, button);
        }
    }
    
    public void sendMouseRelease(int x, int y, int button) {
        if (browser != null) {
            browser.sendMouseRelease(x, y, button);
        }
    }
    
    public void sendMouseMove(int x, int y) {
        if (browser != null) {
            browser.sendMouseMove(x, y);
        }
    }
    
    public void sendMouseWheel(int x, int y, int delta, int modifiers) {
        if (browser != null) {
            browser.sendMouseWheel(x, y, delta, modifiers);
        }
    }
    
    public void sendKeyPress(int keyCode, long scanCode, int modifiers) {
        if (browser != null) {
            browser.sendKeyPress(keyCode, scanCode, modifiers);
        }
    }
    
    public void sendKeyRelease(int keyCode, long scanCode, int modifiers) {
        if (browser != null) {
            browser.sendKeyRelease(keyCode, scanCode, modifiers);
        }
    }
    
    public void sendKeyTyped(char character, int modifiers) {
        if (browser != null) {
            browser.sendKeyTyped(character, modifiers);
        }
    }
    
    // ==================== 生命周期 ====================
    
    /**
     * 检查浏览器是否就绪
     */
    public boolean isReady() {
        return browser != null;
    }
    
    /**
     * 关闭并释放浏览器资源
     */
    public void close() {
        if (browser != null) {
            browser.close();
            browser = null;
            LOGGER.info("浏览器已关闭");
        }
    }
    
    /**
     * 获取底层 MCEF 浏览器实例
     * 谨慎使用 - 尽可能使用高层方法
     */
    @Nullable
    public MCEFBrowser getRawBrowser() {
        return browser;
    }
    
    /**
     * 获取浏览器宽度
     */
    public int getWidth() {
        return width;
    }
    
    /**
     * 获取浏览器高度
     */
    public int getHeight() {
        return height;
    }
    
    /**
     * 是否透明背景
     */
    public boolean isTransparent() {
        return transparent;
    }
    
    /**
     * 检查 MCEF 是否可用且已初始化
     */
    public static boolean isMCEFAvailable() {
        try {
            return MCEF.isInitialized();
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }
}
