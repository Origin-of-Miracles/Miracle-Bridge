package com.originofmiracles.miraclebridge.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.originofmiracles.miraclebridge.MiracleBridge;
import com.originofmiracles.miraclebridge.bridge.BridgeAPI;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * 内置 Web 服务器
 * 
 * 提供以下功能：
 * 1. 静态文件服务 - 从 JAR 内或外部目录 serve 前端构建产物
 * 2. Bridge API 端点 - 处理 /api/* 请求，转发到 BridgeAPI
 * 3. CORS 支持 - 允许跨域请求（开发时需要）
 * 
 * @author Origin of Miracles Dev Team
 */
public class EmbeddedWebServer {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    
    /** 静态资源在 JAR 内的路径前缀 */
    private static final String ASSETS_PATH = "/assets/miraclebridge/web/";
    
    /** MIME 类型映射 */
    private static final Map<String, String> MIME_TYPES = new HashMap<>();
    
    static {
        MIME_TYPES.put("html", "text/html; charset=utf-8");
        MIME_TYPES.put("htm", "text/html; charset=utf-8");
        MIME_TYPES.put("css", "text/css; charset=utf-8");
        MIME_TYPES.put("js", "application/javascript; charset=utf-8");
        MIME_TYPES.put("mjs", "application/javascript; charset=utf-8");
        MIME_TYPES.put("json", "application/json; charset=utf-8");
        MIME_TYPES.put("png", "image/png");
        MIME_TYPES.put("jpg", "image/jpeg");
        MIME_TYPES.put("jpeg", "image/jpeg");
        MIME_TYPES.put("gif", "image/gif");
        MIME_TYPES.put("svg", "image/svg+xml");
        MIME_TYPES.put("ico", "image/x-icon");
        MIME_TYPES.put("woff", "font/woff");
        MIME_TYPES.put("woff2", "font/woff2");
        MIME_TYPES.put("ttf", "font/ttf");
        MIME_TYPES.put("eot", "application/vnd.ms-fontobject");
        MIME_TYPES.put("map", "application/json");
        MIME_TYPES.put("webp", "image/webp");
        MIME_TYPES.put("mp3", "audio/mpeg");
        MIME_TYPES.put("wav", "audio/wav");
        MIME_TYPES.put("ogg", "audio/ogg");
        MIME_TYPES.put("mp4", "video/mp4");
        MIME_TYPES.put("webm", "video/webm");
        MIME_TYPES.put("txt", "text/plain; charset=utf-8");
        MIME_TYPES.put("xml", "application/xml");
    }
    
    private static volatile EmbeddedWebServer instance;
    private static final Object LOCK = new Object();
    
    private HttpServer server;
    private int port;
    private boolean running = false;
    
    /**
     * 获取单例实例（线程安全）
     */
    public static EmbeddedWebServer getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new EmbeddedWebServer();
                }
            }
        }
        return instance;
    }
    
    private EmbeddedWebServer() {}
    
    /**
     * 启动 Web 服务器
     * 
     * @param port 监听端口
     * @return 是否成功启动
     */
    public boolean start(int port) {
        if (running) {
            LOGGER.warn("Embedded web server already running on port {}", this.port);
            return true;
        }
        
        this.port = port;
        
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            
            // API 端点 - 处理 Bridge 请求
            server.createContext("/api/", new BridgeApiHandler());
            
            // 静态文件服务
            server.createContext("/", new StaticFileHandler());
            
            // 使用线程池处理请求
            server.setExecutor(Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "MiracleBridge-WebServer");
                t.setDaemon(true);
                return t;
            }));
            
            server.start();
            running = true;
            
            LOGGER.info("Embedded web server started on http://127.0.0.1:{}", port);
            return true;
            
        } catch (IOException e) {
            LOGGER.error("Failed to start embedded web server on port {}", port, e);
            return false;
        }
    }
    
    /**
     * 停止 Web 服务器
     */
    public void stop() {
        if (server != null && running) {
            server.stop(0);
            running = false;
            LOGGER.info("Embedded web server stopped");
        }
    }
    
    /**
     * 检查服务器是否正在运行
     */
    public boolean isRunning() {
        return running;
    }
    
    /**
     * 获取服务器 URL（带缓存破坏参数）
     * 使用启动时间戳确保每次启动游戏后加载最新前端资源
     */
    public String getServerUrl() {
        if (!running) return null;
        // 添加时间戳参数避免 MCEF 缓存旧版本前端
        return "http://127.0.0.1:" + port + "?_t=" + startTime;
    }
    
    // 启动时间戳，用于缓存破坏
    private final long startTime = System.currentTimeMillis();
    
    /**
     * 获取服务器端口
     */
    public int getPort() {
        return port;
    }
    
    // ==================== HTTP Handlers ====================
    
    /**
     * Bridge API 处理器
     * 处理 /api/{action} 请求
     */
    private class BridgeApiHandler implements HttpHandler {
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 添加 CORS 头
            addCorsHeaders(exchange);
            
            // 处理 OPTIONS 预检请求
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            String path = exchange.getRequestURI().getPath();
            String action = path.substring("/api/".length()); // 移除 /api/ 前缀
            
            // 只接受 POST 请求
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonError(exchange, 405, "Method not allowed. Use POST.");
                return;
            }
            
            try {
                // 读取请求体
                String requestBody = readRequestBody(exchange);
                JsonObject request;
                try {
                    request = JsonParser.parseString(requestBody).getAsJsonObject();
                } catch (Exception e) {
                    request = new JsonObject();
                }
                
                // 获取 BridgeAPI 并处理请求
                BridgeAPI bridgeAPI = MiracleBridge.getBridgeAPI();
                if (bridgeAPI == null) {
                    sendJsonError(exchange, 503, "BridgeAPI not available. Browser not created yet.");
                    return;
                }
                
                // 调用处理器
                JsonObject response = bridgeAPI.handleRequest(action, request);
                
                // 发送响应
                sendJsonResponse(exchange, 200, response);
                
            } catch (Exception e) {
                LOGGER.error("Error handling API request: {}", action, e);
                sendJsonError(exchange, 500, "Internal server error: " + e.getMessage());
            }
        }
    }
    
    /**
     * 静态文件处理器
     * 从 JAR 内的 assets 目录 serve 文件
     */
    private class StaticFileHandler implements HttpHandler {
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 添加 CORS 头
            addCorsHeaders(exchange);
            
            // 只接受 GET 请求
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }
                sendTextError(exchange, 405, "Method not allowed");
                return;
            }
            
            String path = exchange.getRequestURI().getPath();
            
            // 默认页面
            if (path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }
            
            // 安全检查 - 防止路径遍历攻击
            if (path.contains("..") || path.contains("//")) {
                sendTextError(exchange, 400, "Invalid path");
                return;
            }
            
            // 从 JAR 资源加载
            String resourcePath = ASSETS_PATH + path.substring(1); // 移除开头的 /
            InputStream resourceStream = getClass().getResourceAsStream(resourcePath);
            
            if (resourceStream == null) {
                // 对于 SPA，非静态资源路径返回 index.html
                if (!hasFileExtension(path)) {
                    resourceStream = getClass().getResourceAsStream(ASSETS_PATH + "index.html");
                    if (resourceStream != null) {
                        serveResource(exchange, resourceStream, "text/html; charset=utf-8");
                        return;
                    }
                }
                
                sendTextError(exchange, 404, "File not found: " + path);
                return;
            }
            
            // 确定 MIME 类型
            String mimeType = getMimeType(path);
            
            serveResource(exchange, resourceStream, mimeType);
        }
        
        private void serveResource(HttpExchange exchange, InputStream resourceStream, String mimeType) throws IOException {
            try {
                byte[] content = resourceStream.readAllBytes();
                
                exchange.getResponseHeaders().set("Content-Type", mimeType);
                exchange.getResponseHeaders().set("Cache-Control", "public, max-age=3600");
                exchange.sendResponseHeaders(200, content.length);
                
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(content);
                }
            } finally {
                resourceStream.close();
            }
        }
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * 添加 CORS 响应头
     */
    private void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.getResponseHeaders().set("Access-Control-Max-Age", "86400");
    }
    
    /**
     * 读取请求体
     */
    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    
    /**
     * 发送 JSON 响应
     */
    private void sendJsonResponse(HttpExchange exchange, int statusCode, JsonObject data) throws IOException {
        String json = GSON.toJson(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    /**
     * 发送 JSON 错误响应
     */
    private void sendJsonError(HttpExchange exchange, int statusCode, String message) throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        error.addProperty("status", statusCode);
        sendJsonResponse(exchange, statusCode, error);
    }
    
    /**
     * 发送纯文本错误响应
     */
    private void sendTextError(HttpExchange exchange, int statusCode, String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    /**
     * 根据文件扩展名获取 MIME 类型
     */
    private String getMimeType(String path) {
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < path.length() - 1) {
            String ext = path.substring(dotIndex + 1).toLowerCase();
            return MIME_TYPES.getOrDefault(ext, "application/octet-stream");
        }
        return "application/octet-stream";
    }
    
    /**
     * 检查路径是否有文件扩展名
     */
    private boolean hasFileExtension(String path) {
        int lastSlash = path.lastIndexOf('/');
        int lastDot = path.lastIndexOf('.');
        return lastDot > lastSlash && lastDot < path.length() - 1;
    }
}
