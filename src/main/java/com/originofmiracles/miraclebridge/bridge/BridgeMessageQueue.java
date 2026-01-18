package com.originofmiracles.miraclebridge.bridge;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;

/**
 * Bidirectional Message Queue
 * 
 * Provides async message passing mechanism for JS ↔ Java communication.
 * Uses polling mode due to MCEF's SchemeHandler registration timing limitations.
 * 
 * Workflow:
 * 1. JS calls submitRequest() to add request to queue
 * 2. Java main thread polls pollRequest() to process requests
 * 3. Java calls submitResponse() after processing to add response to queue
 * 4. JS polls pollResponse() to get response
 */
public class BridgeMessageQueue {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    
    /**
     * Message type
     */
    public enum MessageType {
        REQUEST,
        RESPONSE,
        EVENT
    }
    
    /**
     * Message wrapper class
     */
    public static class Message {
        private final long id;
        private final MessageType type;
        private final String action;
        private final String payload;
        private final long timestamp;
        
        public Message(long id, MessageType type, String action, String payload) {
            this.id = id;
            this.type = type;
            this.action = action;
            this.payload = payload;
            this.timestamp = System.currentTimeMillis();
        }
        
        public long getId() {
            return id;
        }
        
        public MessageType getType() {
            return type;
        }
        
        public String getAction() {
            return action;
        }
        
        public String getPayload() {
            return payload;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("id", id);
            json.addProperty("type", type.name());
            json.addProperty("action", action);
            json.addProperty("payload", payload);
            json.addProperty("timestamp", timestamp);
            return json;
        }
        
        @Override
        public String toString() {
            return String.format("Message{id=%d, type=%s, action='%s'}", id, type, action);
        }
    }
    
    private final AtomicLong messageIdGenerator = new AtomicLong(0);
    
    /**
     * JS → Java request queue
     */
    private final Queue<Message> requestQueue = new ConcurrentLinkedQueue<>();
    
    /**
     * Java → JS response queue
     */
    private final Queue<Message> responseQueue = new ConcurrentLinkedQueue<>();
    
    /**
     * Java → JS event queue
     */
    private final Queue<Message> eventQueue = new ConcurrentLinkedQueue<>();
    
    /**
     * Max queue size (prevent memory overflow)
     */
    private static final int MAX_QUEUE_SIZE = 1000;
    
    /**
     * Submit request (called by JS)
     * 
     * @param action action name
     * @param payload JSON payload
     * @return message ID for matching response
     */
    public long submitRequest(String action, String payload) {
        if (requestQueue.size() >= MAX_QUEUE_SIZE) {
            LOGGER.warn("Request queue full, dropping request: action={}", action);
            return -1;
        }
        
        long messageId = messageIdGenerator.incrementAndGet();
        Message message = new Message(messageId, MessageType.REQUEST, action, payload);
        requestQueue.offer(message);
        
        LOGGER.debug("Request enqueued: {}", message);
        return messageId;
    }
    
    /**
     * Poll request (called by Java)
     * 
     * @return next pending request, or null if queue is empty
     */
    @Nullable
    public Message pollRequest() {
        return requestQueue.poll();
    }
    
    /**
     * Peek at request (without removing)
     */
    @Nullable
    public Message peekRequest() {
        return requestQueue.peek();
    }
    
    /**
     * Submit response (called by Java)
     * 
     * @param requestId corresponding request ID
     * @param action action name
     * @param payload JSON payload
     */
    public void submitResponse(long requestId, String action, String payload) {
        if (responseQueue.size() >= MAX_QUEUE_SIZE) {
            LOGGER.warn("Response queue full, dropping response: requestId={}", requestId);
            return;
        }
        
        Message message = new Message(requestId, MessageType.RESPONSE, action, payload);
        responseQueue.offer(message);
        
        LOGGER.debug("Response enqueued: {}", message);
    }
    
    /**
     * Poll response (called by JS)
     * 
     * @return next response, or null if queue is empty
     */
    @Nullable
    public Message pollResponse() {
        return responseQueue.poll();
    }
    
    /**
     * Poll all responses (called by JS)
     * 
     * @param maxCount maximum number to retrieve
     * @return JSON array string
     */
    public String pollAllResponses(int maxCount) {
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        int count = 0;
        Message message;
        
        while (count < maxCount && (message = responseQueue.poll()) != null) {
            array.add(message.toJson());
            count++;
        }
        
        return GSON.toJson(array);
    }
    
    /**
     * Push event (called by Java)
     * 
     * @param eventName event name
     * @param payload JSON payload
     */
    public void pushEvent(String eventName, String payload) {
        if (eventQueue.size() >= MAX_QUEUE_SIZE) {
            LOGGER.warn("Event queue full, dropping event: event={}", eventName);
            return;
        }
        
        long messageId = messageIdGenerator.incrementAndGet();
        Message message = new Message(messageId, MessageType.EVENT, eventName, payload);
        eventQueue.offer(message);
        
        LOGGER.debug("Event enqueued: {}", message);
    }
    
    /**
     * Poll event (called by JS)
     * 
     * @return next event, or null if queue is empty
     */
    @Nullable
    public Message pollEvent() {
        return eventQueue.poll();
    }
    
    /**
     * Poll all events (called by JS)
     * 
     * @param maxCount maximum number to retrieve
     * @return JSON array string
     */
    public String pollAllEvents(int maxCount) {
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        int count = 0;
        Message message;
        
        while (count < maxCount && (message = eventQueue.poll()) != null) {
            array.add(message.toJson());
            count++;
        }
        
        return GSON.toJson(array);
    }
    
    /**
     * 获取请求队列长度
     */
    public int getRequestQueueSize() {
        return requestQueue.size();
    }
    
    /**
     * 获取响应队列长度
     */
    public int getResponseQueueSize() {
        return responseQueue.size();
    }
    
    /**
     * 获取事件队列长度
     */
    public int getEventQueueSize() {
        return eventQueue.size();
    }
    
    /**
     * 清空所有队列
     */
    public void clear() {
        requestQueue.clear();
        responseQueue.clear();
        eventQueue.clear();
        LOGGER.info("所有消息队列已清空");
    }
    
    /**
     * 生成用于 JS 注入的桥接代码
     * 
     * 实现方案：
     * 1. JS 调用 call() 时，将请求存入全局请求队列 __miracleBridgeRequestQueue
     * 2. Java 定期调用 __miracleBridgePollRequests() 获取待处理请求
     * 3. Java 处理完成后调用 __miracleBridgeHandleResponse() 返回响应
     */
    public static String generateBridgeScript() {
        return """
            (function() {
                // MiracleBridge JS SDK v2 - 基于轮询的通信机制
                window.MiracleBridge = window.MiracleBridge || {};
                
                const pendingRequests = new Map();
                const eventListeners = new Map();
                
                // 全局请求队列 - Java 端会定期轮询
                window.__miracleBridgeRequestQueue = window.__miracleBridgeRequestQueue || [];
                window.__miracleBridgeReady = true;
                
                // 生成唯一请求 ID
                let requestIdCounter = 0;
                function generateRequestId() {
                    return 'req_' + Date.now() + '_' + (++requestIdCounter);
                }
                
                // 提交请求到队列（供内部使用）
                window.__miracleBridgeSubmitRequest = function(requestId, action, payload) {
                    window.__miracleBridgeRequestQueue.push({
                        id: requestId,
                        action: action,
                        payload: payload,
                        timestamp: Date.now()
                    });
                    console.debug('[MiracleBridge] Request submitted:', action, requestId);
                };
                
                // Java 调用此函数获取所有待处理请求
                window.__miracleBridgePollRequests = function() {
                    const requests = window.__miracleBridgeRequestQueue.splice(0);
                    return JSON.stringify(requests);
                };
                
                // 调用 Java 方法
                MiracleBridge.call = function(action, payload = {}) {
                    return new Promise((resolve, reject) => {
                        const requestId = generateRequestId();
                        const timeoutMs = 30000;
                        
                        // 设置超时
                        const timeout = setTimeout(() => {
                            pendingRequests.delete(requestId);
                            reject(new Error('Request timeout: ' + action));
                        }, timeoutMs);
                        
                        // 保存回调
                        pendingRequests.set(requestId, {
                            resolve: (data) => {
                                clearTimeout(timeout);
                                pendingRequests.delete(requestId);
                                resolve(data);
                            },
                            reject: (error) => {
                                clearTimeout(timeout);
                                pendingRequests.delete(requestId);
                                reject(error);
                            }
                        });
                        
                        // 发送请求到队列
                        window.__miracleBridgeSubmitRequest(requestId, action, JSON.stringify(payload));
                    });
                };
                
                // 调用服务端 API（与 call 相同，由 Java 端区分处理）
                MiracleBridge.callServer = MiracleBridge.call;
                
                // 监听事件
                MiracleBridge.on = function(eventName, callback) {
                    if (!eventListeners.has(eventName)) {
                        eventListeners.set(eventName, new Set());
                    }
                    eventListeners.get(eventName).add(callback);
                };
                
                // 取消监听
                MiracleBridge.off = function(eventName, callback) {
                    if (eventListeners.has(eventName)) {
                        eventListeners.get(eventName).delete(callback);
                    }
                };
                
                // 单次监听
                MiracleBridge.once = function(eventName, callback) {
                    const wrapper = (data) => {
                        MiracleBridge.off(eventName, wrapper);
                        callback(data);
                    };
                    MiracleBridge.on(eventName, wrapper);
                };
                
                // 处理响应（由 Java 调用）
                window.__miracleBridgeHandleResponse = function(requestId, success, data) {
                    console.debug('[MiracleBridge] Response received:', requestId, success);
                    const pending = pendingRequests.get(requestId);
                    if (pending) {
                        if (success) {
                            try {
                                pending.resolve(JSON.parse(data));
                            } catch (e) {
                                pending.resolve(data);
                            }
                        } else {
                            pending.reject(new Error(data));
                        }
                    } else {
                        console.warn('[MiracleBridge] No pending request for:', requestId);
                    }
                };
                
                // 处理事件（由 Java 调用）
                window.__miracleBridgeHandleEvent = function(eventName, data) {
                    console.debug('[MiracleBridge] Event received:', eventName);
                    const listeners = eventListeners.get(eventName);
                    let parsedData;
                    try {
                        parsedData = JSON.parse(data);
                    } catch (e) {
                        parsedData = data;
                    }
                    
                    if (listeners) {
                        listeners.forEach(callback => {
                            try {
                                callback(parsedData);
                            } catch (e) {
                                console.error('[MiracleBridge] Event handler error:', e);
                            }
                        });
                    }
                    
                    // 同时触发 DOM 事件
                    window.dispatchEvent(new CustomEvent(eventName, { detail: parsedData }));
                };
                
                // 检查桥接状态 - 现在永远返回 true 因为请求队列机制始终可用
                MiracleBridge.isReady = function() {
                    return window.__miracleBridgeReady === true;
                };
                
                // 等待桥接就绪
                MiracleBridge.whenReady = function() {
                    return new Promise((resolve) => {
                        if (MiracleBridge.isReady()) {
                            resolve();
                        } else {
                            const check = setInterval(() => {
                                if (MiracleBridge.isReady()) {
                                    clearInterval(check);
                                    resolve();
                                }
                            }, 50);
                        }
                    });
                };
                
                // 配置（供前端调用）
                MiracleBridge.configure = function(options) {
                    console.log('[MiracleBridge] Configure:', options);
                };
                
                // 启用调试
                MiracleBridge.enableDebug = function() {
                    console.log('[MiracleBridge] Debug enabled');
                };
                
                // 获取状态
                MiracleBridge.getStatus = function() {
                    return {
                        ready: MiracleBridge.isReady(),
                        pendingRequests: pendingRequests.size,
                        queueSize: window.__miracleBridgeRequestQueue.length,
                        eventListeners: Array.from(eventListeners.keys()),
                        config: { timeout: 30000, debug: false, pollInterval: 50 }
                    };
                };
                
                // 便捷方法 - getPlayerInfo
                MiracleBridge.getPlayerInfo = function() {
                    return MiracleBridge.call('getPlayerInfo');
                };
                
                // 便捷方法 - getInventory
                MiracleBridge.getInventory = function() {
                    return MiracleBridge.call('getInventory');
                };
                
                // 便捷方法 - teleport
                MiracleBridge.teleport = function(x, y, z) {
                    return MiracleBridge.call('teleport', { x, y, z });
                };
                
                // 便捷方法 - sendChat
                MiracleBridge.sendChat = function(message) {
                    return MiracleBridge.call('sendChat', { message });
                };
                
                // 便捷方法 - executeCommand
                MiracleBridge.executeCommand = function(command) {
                    return MiracleBridge.call('executeCommand', { command });
                };
                
                console.log('[MiracleBridge] JS SDK v2 initialized - polling mode');
            })();
            """;
    }
}
