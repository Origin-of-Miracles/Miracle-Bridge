package com.originofmiracles.miraclebridge.browser;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import com.originofmiracles.miraclebridge.bridge.BridgeAPI;
import com.originofmiracles.miraclebridge.bridge.BridgeMessageQueue;

import net.minecraft.client.renderer.GameRenderer;

/**
 * High-level wrapper for MCEF browser instance.
 * 
 * Provides simplified API for:
 * - Browser lifecycle management
 * - URL navigation
 * - JavaScript execution
 * - Input event handling
 * - Texture rendering
 * 
 * @see MCEFBrowser
 */
public class MiracleBrowser {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    @Nullable
    private MCEFBrowser browser;
    
    @Nullable
    private BridgeAPI bridgeAPI;
    
    private final boolean transparent;
    private int width;
    private int height;
    
    /**
     * Create a new browser wrapper
     * @param transparent whether the browser background is transparent
     */
    public MiracleBrowser(boolean transparent) {
        this.transparent = transparent;
    }
    
    /**
     * Initialize browser with specified URL
     * @param url initial URL
     * @param width browser width (pixels)
     * @param height browser height (pixels)
     * @return true if initialization succeeded
     */
    public boolean create(String url, int width, int height) {
        if (!MCEF.isInitialized()) {
            LOGGER.error("MCEF not initialized! Cannot create browser.");
            return false;
        }
        
        this.width = width;
        this.height = height;
        
        try {
            MCEFBrowser createdBrowser = MCEF.createBrowser(url, transparent);
            if (createdBrowser == null) {
                LOGGER.error("MCEF.createBrowser returned null");
                return false;
            }
            this.browser = createdBrowser;
            createdBrowser.resize(width, height);
            
            // Initialize BridgeAPI
            this.bridgeAPI = new BridgeAPI(this);
            
            // Inject bridge script
            injectBridgeScript();
            
            LOGGER.info("Browser created: {} ({}x{})", url, width, height);
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to create browser", e);
            return false;
        }
    }
    
    /**
     * Load a new URL
     */
    public void loadUrl(String url) {
        if (browser != null) {
            browser.loadURL(url);
            LOGGER.debug("Loading URL: {}", url);
        }
    }
    
    /**
     * Execute JavaScript code in browser context
     * @param script JavaScript code to execute
     */
    public void executeJavaScript(String script) {
        if (browser != null) {
            browser.executeJavaScript(script, browser.getURL(), 0);
        }
    }
    
    /**
     * Resize browser viewport
     */
    public void resize(int width, int height) {
        if (browser != null && (this.width != width || this.height != height)) {
            this.width = width;
            this.height = height;
            browser.resize(width, height);
        }
    }
    
    /**
     * Get OpenGL texture ID for rendering
     * @return texture ID, or -1 if browser not ready
     */
    public int getTextureId() {
        if (browser == null) return -1;
        var renderer = browser.getRenderer();
        return renderer != null ? renderer.getTextureID() : -1;
    }
    
    /**
     * Render browser to screen at specified position
     * @param x screen X position
     * @param y screen Y position
     * @param renderWidth render width
     * @param renderHeight render height
     */
    public void render(int x, int y, int renderWidth, int renderHeight) {
        if (browser == null) return;
        
        int textureId = getTextureId();
        // Texture ID must be > 0 (0 = invalid texture, -1 = browser not ready)
        if (textureId <= 0) return;
        
        // Set render state
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, textureId);
        
        // Build quad vertices
        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        
        // Vertex order: bottom-left -> bottom-right -> top-right -> top-left (clockwise)
        bufferBuilder.vertex(x, y + renderHeight, 0).uv(0f, 1f).color(255, 255, 255, 255).endVertex();
        bufferBuilder.vertex(x + renderWidth, y + renderHeight, 0).uv(1f, 1f).color(255, 255, 255, 255).endVertex();
        bufferBuilder.vertex(x + renderWidth, y, 0).uv(1f, 0f).color(255, 255, 255, 255).endVertex();
        bufferBuilder.vertex(x, y, 0).uv(0f, 0f).color(255, 255, 255, 255).endVertex();
        
        BufferUploader.drawWithShader(bufferBuilder.end());
        
        RenderSystem.disableBlend();
    }
    
    // ==================== Input Events ====================
    
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
    
    // ==================== Lifecycle ====================
    
    /**
     * Check if browser is ready
     */
    public boolean isReady() {
        return browser != null;
    }
    
    /**
     * Close and release browser resources
     */
    public void close() {
        if (browser != null) {
            browser.close();
            browser = null;
            bridgeAPI = null;
            LOGGER.info("Browser closed");
        }
    }
    
    /**
     * Get BridgeAPI instance
     */
    @Nullable
    public BridgeAPI getBridgeAPI() {
        return bridgeAPI;
    }
    
    /**
     * Inject bridge script into browser
     */
    private void injectBridgeScript() {
        if (browser == null) return;
        
        // Inject JS SDK
        String bridgeScript = BridgeMessageQueue.generateBridgeScript();
        executeJavaScript(bridgeScript);
        
        LOGGER.debug("Bridge script injected");
    }
    
    /**
     * Re-inject bridge script (call after page navigation)
     */
    public void reinjectBridgeScript() {
        injectBridgeScript();
    }
    
    /**
     * Get underlying MCEF browser instance
     * Use with caution - prefer high-level methods
     */
    @Nullable
    public MCEFBrowser getRawBrowser() {
        return browser;
    }
    
    /**
     * Get browser width
     */
    public int getWidth() {
        return width;
    }
    
    /**
     * Get browser height
     */
    public int getHeight() {
        return height;
    }
    
    /**
     * Whether background is transparent
     */
    public boolean isTransparent() {
        return transparent;
    }
    
    /**
     * Check if MCEF is available and initialized
     */
    public static boolean isMCEFAvailable() {
        try {
            return MCEF.isInitialized();
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }
}
