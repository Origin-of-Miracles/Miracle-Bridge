package com.originofmiracles.miraclebridge.bridge;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.cef.callback.CefCallback;
import org.cef.handler.CefResourceHandler;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;
import org.slf4j.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.originofmiracles.miraclebridge.browser.BrowserManager;
import com.originofmiracles.miraclebridge.browser.MiracleBrowser;
import com.originofmiracles.miraclebridge.config.ClientConfig;

/**
 * Bridge Protocol Handler
 * 
 * Handles bridge://api/action style requests for JS → Java communication.
 * 
 * URL format: bridge://api/{action}
 * Method: POST
 * Request body: JSON parameters
 * Response: JSON result
 * 
 * Example:
 * ```javascript
 * const response = await fetch('bridge://api/anima.chat', {
 *   method: 'POST',
 *   headers: { 'Content-Type': 'application/json' },
 *   body: JSON.stringify({ studentId: 'arona', message: 'Hello' })
 * });
 * const result = await response.json();
 * ```
 */
public class BridgeSchemeHandler implements CefResourceHandler {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private final String url;
    private final String postData;
    
    private byte[] responseData;
    private int responseOffset;
    private String contentType = "application/json";
    private int statusCode = 200;
    private String statusText = "OK";
    
    public BridgeSchemeHandler(String url, String postData) {
        this.url = url;
        this.postData = postData;
    }
    
    @Override
    public boolean processRequest(CefRequest request, CefCallback callback) {
        // Parse URL: bridge://api/{action}
        String path = url;
        if (path.startsWith("bridge://")) {
            path = path.substring("bridge://".length());
        }
        
        // Remove leading slashes
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        
        // Parse action
        String action = "";
        if (path.startsWith("api/")) {
            action = path.substring("api/".length());
        } else {
            action = path;
        }
        
        // Remove query parameters
        int queryIndex = action.indexOf('?');
        if (queryIndex > 0) {
            action = action.substring(0, queryIndex);
        }
        
        if (ClientConfig.shouldLogBridgeRequests()) {
            LOGGER.info("Bridge request: action={}, postData={}", action, postData);
        }
        
        try {
            // Get BridgeAPI and handle request
            MiracleBrowser browser = BrowserManager.getInstance().getBrowser(BrowserManager.DEFAULT_BROWSER_NAME);
            if (browser == null || browser.getBridgeAPI() == null) {
                LOGGER.error("Bridge request failed: browser or BridgeAPI not initialized");
                setErrorResponse("Bridge not initialized");
                callback.Continue();
                return true;
            }
            
            BridgeAPI bridgeAPI = browser.getBridgeAPI();
            String payload = (postData != null && !postData.isEmpty()) ? postData : "{}";
            
            // Handle request
            String response = bridgeAPI.handleRequest(action, payload);
            setSuccessResponse(response);
            
        } catch (Exception e) {
            LOGGER.error("Error handling Bridge request: {}", action, e);
            setErrorResponse("Internal error: " + e.getMessage());
        }
        
        callback.Continue();
        return true;
    }
    
    private void setSuccessResponse(String jsonResponse) {
        this.statusCode = 200;
        this.statusText = "OK";
        this.contentType = "application/json";
        this.responseData = jsonResponse.getBytes(StandardCharsets.UTF_8);
        this.responseOffset = 0;
    }
    
    private void setErrorResponse(String message) {
        this.statusCode = 500;
        this.statusText = "Internal Server Error";
        this.contentType = "application/json";
        
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        error.addProperty("success", false);
        
        this.responseData = error.toString().getBytes(StandardCharsets.UTF_8);
        this.responseOffset = 0;
    }
    
    @Override
    public void getResponseHeaders(CefResponse response, IntRef responseLength, StringRef redirectUrl) {
        response.setStatus(statusCode);
        response.setStatusText(statusText);
        response.setMimeType(contentType);
        
        // 设置 CORS 头部
        response.setHeaderByName("Access-Control-Allow-Origin", "*", true);
        response.setHeaderByName("Access-Control-Allow-Methods", "GET, POST, OPTIONS", true);
        response.setHeaderByName("Access-Control-Allow-Headers", "Content-Type", true);
        
        if (responseData != null) {
            responseLength.set(responseData.length);
        } else {
            responseLength.set(0);
        }
    }
    
    @Override
    public boolean readResponse(byte[] dataOut, int bytesToRead, IntRef bytesRead, CefCallback callback) {
        if (responseData == null || responseOffset >= responseData.length) {
            bytesRead.set(0);
            return false;
        }
        
        int available = responseData.length - responseOffset;
        int toRead = Math.min(bytesToRead, available);
        
        System.arraycopy(responseData, responseOffset, dataOut, 0, toRead);
        responseOffset += toRead;
        bytesRead.set(toRead);
        
        return responseOffset < responseData.length;
    }
    
    @Override
    public void cancel() {
        // 清理资源
        responseData = null;
    }
}
