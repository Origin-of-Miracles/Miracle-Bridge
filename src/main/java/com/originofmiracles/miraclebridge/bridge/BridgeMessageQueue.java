package com.originofmiracles.miraclebridge.bridge;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 双向消息队列
 * 
 * 提供 JS ↔ Java 的异步消息传递机制。
 * 由于 MCEF 的 SchemeHandler 注册时机限制，采用轮询模式替代。
 * 
 * 工作流程：
 * 1. JS 调用 submitRequest() 提交请求到请求队列
 * 2. Java 主线程轮询 pollRequest() 处理请求
 * 3. Java 处理完成后调用 submitResponse() 提交响应到响应队列
 * 4. JS 轮询 pollResponse() 获取响应
 */
public class BridgeMessageQueue {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    
    /**
     * 消息类型
     */
    public enum MessageType {
        REQUEST,
        RESPONSE,
        EVENT
    }
    
    /**
     * 消息包装类
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
     * JS → Java 请求队列
     */
    private final Queue<Message> requestQueue = new ConcurrentLinkedQueue<>();
    
    /**
     * Java → JS 响应队列
     */
    private final Queue<Message> responseQueue = new ConcurrentLinkedQueue<>();
    
    /**
     * Java → JS 事件队列
     */
    private final Queue<Message> eventQueue = new ConcurrentLinkedQueue<>();
    
    /**
     * 最大队列长度（防止内存溢出）
     */
    private static final int MAX_QUEUE_SIZE = 1000;
    
    /**
     * 提交请求（JS 调用）
     * 
     * @param action 动作名称
     * @param payload JSON 负载
     * @return 消息 ID，用于匹配响应
     */
    public long submitRequest(String action, String payload) {
        if (requestQueue.size() >= MAX_QUEUE_SIZE) {
            LOGGER.warn("请求队列已满，丢弃请求: action={}", action);
            return -1;
        }
        
        long messageId = messageIdGenerator.incrementAndGet();
        Message message = new Message(messageId, MessageType.REQUEST, action, payload);
        requestQueue.offer(message);
        
        LOGGER.debug("请求已入队: {}", message);
        return messageId;
    }
    
    /**
     * 轮询请求（Java 调用）
     * 
     * @return 下一个待处理的请求，队列为空时返回 null
     */
    @Nullable
    public Message pollRequest() {
        return requestQueue.poll();
    }
    
    /**
     * 查看请求（不移除）
     */
    @Nullable
    public Message peekRequest() {
        return requestQueue.peek();
    }
    
    /**
     * 提交响应（Java 调用）
     * 
     * @param requestId 对应的请求 ID
     * @param action 动作名称
     * @param payload JSON 负载
     */
    public void submitResponse(long requestId, String action, String payload) {
        if (responseQueue.size() >= MAX_QUEUE_SIZE) {
            LOGGER.warn("响应队列已满，丢弃响应: requestId={}", requestId);
            return;
        }
        
        Message message = new Message(requestId, MessageType.RESPONSE, action, payload);
        responseQueue.offer(message);
        
        LOGGER.debug("响应已入队: {}", message);
    }
    
    /**
     * 轮询响应（JS 调用）
     * 
     * @return 下一个响应，队列为空时返回 null
     */
    @Nullable
    public Message pollResponse() {
        return responseQueue.poll();
    }
    
    /**
     * 轮询所有响应（JS 调用）
     * 
     * @param maxCount 最大获取数量
     * @return JSON 数组字符串
     */
    public String pollAllResponses(int maxCount) {
        StringBuilder sb = new StringBuilder("[");
        int count = 0;
        Message message;
        
        while (count < maxCount && (message = responseQueue.poll()) != null) {
            if (count > 0) {
                sb.append(",");
            }
            sb.append(GSON.toJson(message.toJson()));
            count++;
        }
        
        sb.append("]");
        return sb.toString();
    }
    
    /**
     * 推送事件（Java 调用）
     * 
     * @param eventName 事件名称
     * @param payload JSON 负载
     */
    public void pushEvent(String eventName, String payload) {
        if (eventQueue.size() >= MAX_QUEUE_SIZE) {
            LOGGER.warn("事件队列已满，丢弃事件: event={}", eventName);
            return;
        }
        
        long messageId = messageIdGenerator.incrementAndGet();
        Message message = new Message(messageId, MessageType.EVENT, eventName, payload);
        eventQueue.offer(message);
        
        LOGGER.debug("事件已入队: {}", message);
    }
    
    /**
     * 轮询事件（JS 调用）
     * 
     * @return 下一个事件，队列为空时返回 null
     */
    @Nullable
    public Message pollEvent() {
        return eventQueue.poll();
    }
    
    /**
     * 轮询所有事件（JS 调用）
     * 
     * @param maxCount 最大获取数量
     * @return JSON 数组字符串
     */
    public String pollAllEvents(int maxCount) {
        StringBuilder sb = new StringBuilder("[");
        int count = 0;
        Message message;
        
        while (count < maxCount && (message = eventQueue.poll()) != null) {
            if (count > 0) {
                sb.append(",");
            }
            sb.append(GSON.toJson(message.toJson()));
            count++;
        }
        
        sb.append("]");
        return sb.toString();
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
     */
    public static String generateBridgeScript() {
        return """
            (function() {
                // MiracleBridge JS SDK
                window.MiracleBridge = window.MiracleBridge || {};
                
                const pendingRequests = new Map();
                const eventListeners = new Map();
                let pollInterval = null;
                
                // 生成唯一请求 ID
                let requestIdCounter = 0;
                function generateRequestId() {
                    return 'req_' + Date.now() + '_' + (++requestIdCounter);
                }
                
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
                        
                        // 发送请求（由 Java 注入实现）
                        if (window.__miracleBridgeSubmitRequest) {
                            window.__miracleBridgeSubmitRequest(requestId, action, JSON.stringify(payload));
                        } else {
                            reject(new Error('Bridge not initialized'));
                        }
                    });
                };
                
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
                
                // 处理响应（由 Java 调用）
                window.__miracleBridgeHandleResponse = function(requestId, success, data) {
                    const pending = pendingRequests.get(requestId);
                    if (pending) {
                        if (success) {
                            pending.resolve(JSON.parse(data));
                        } else {
                            pending.reject(new Error(data));
                        }
                    }
                };
                
                // 处理事件（由 Java 调用）
                window.__miracleBridgeHandleEvent = function(eventName, data) {
                    const listeners = eventListeners.get(eventName);
                    if (listeners) {
                        const parsedData = JSON.parse(data);
                        listeners.forEach(callback => {
                            try {
                                callback(parsedData);
                            } catch (e) {
                                console.error('Event handler error:', e);
                            }
                        });
                    }
                    
                    // 同时触发 DOM 事件
                    window.dispatchEvent(new CustomEvent(eventName, { detail: JSON.parse(data) }));
                };
                
                // 检查桥接状态
                MiracleBridge.isReady = function() {
                    return typeof window.__miracleBridgeSubmitRequest === 'function';
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
                            }, 100);
                        }
                    });
                };
                
                console.log('[MiracleBridge] JS SDK initialized');
            })();
            """;
    }
}
