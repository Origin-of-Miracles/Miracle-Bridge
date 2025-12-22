/**
 * MiracleBridge JS SDK
 * 
 * 提供 JavaScript 与 Minecraft Java 的通信接口。
 * 
 * @example
 * // 初始化
 * await MiracleBridge.whenReady();
 * 
 * // 调用 Java 方法
 * const playerInfo = await MiracleBridge.call('getPlayerInfo', {});
 * console.log(playerInfo);
 * 
 * // 监听事件
 * MiracleBridge.on('entity:spawn', (data) => {
 *   console.log('Entity spawned:', data);
 * });
 * 
 * @version 1.0.0
 * @license AGPL-3.0
 */
(function(global) {
    'use strict';

    // 如果已经初始化，则跳过
    if (global.MiracleBridge && global.MiracleBridge._initialized) {
        return;
    }

    const VERSION = '1.0.0';
    
    // 请求管理
    const pendingRequests = new Map();
    const eventListeners = new Map();
    let requestIdCounter = 0;
    
    // 配置
    const config = {
        timeout: 30000,
        debug: false,
        pollInterval: 50
    };
    
    // 轮询定时器
    let pollTimer = null;

    /**
     * 生成唯一请求 ID
     */
    function generateRequestId() {
        return `req_${Date.now()}_${++requestIdCounter}`;
    }

    /**
     * 日志输出
     */
    function log(...args) {
        if (config.debug) {
            console.log('[MiracleBridge]', ...args);
        }
    }

    /**
     * 错误输出
     */
    function logError(...args) {
        console.error('[MiracleBridge]', ...args);
    }

    /**
     * MiracleBridge 主对象
     */
    const MiracleBridge = {
        _initialized: false,
        VERSION,

        /**
         * 配置选项
         */
        configure(options) {
            Object.assign(config, options);
            log('Configuration updated:', config);
        },

        /**
         * 检查桥接是否就绪
         */
        isReady() {
            return typeof global.__miracleBridgeSubmitRequest === 'function';
        },

        /**
         * 等待桥接就绪
         */
        whenReady() {
            return new Promise((resolve) => {
                if (this.isReady()) {
                    resolve();
                    return;
                }
                
                const checkInterval = setInterval(() => {
                    if (this.isReady()) {
                        clearInterval(checkInterval);
                        resolve();
                    }
                }, 100);
                
                // 10秒超时
                setTimeout(() => {
                    clearInterval(checkInterval);
                    resolve(); // 即使未就绪也 resolve，让调用方处理
                }, 10000);
            });
        },

        /**
         * 调用 Java 方法
         * @param {string} action - 动作名称
         * @param {object} payload - 请求参数
         * @returns {Promise<object>} 响应数据
         */
        call(action, payload = {}) {
            return new Promise((resolve, reject) => {
                if (!this.isReady()) {
                    reject(new Error('Bridge not ready'));
                    return;
                }

                const requestId = generateRequestId();
                
                // 设置超时
                const timeout = setTimeout(() => {
                    pendingRequests.delete(requestId);
                    reject(new Error(`Request timeout: ${action}`));
                }, config.timeout);
                
                // 保存回调
                pendingRequests.set(requestId, {
                    action,
                    resolve: (data) => {
                        clearTimeout(timeout);
                        pendingRequests.delete(requestId);
                        resolve(data);
                    },
                    reject: (error) => {
                        clearTimeout(timeout);
                        pendingRequests.delete(requestId);
                        reject(error);
                    },
                    timestamp: Date.now()
                });
                
                // 发送请求
                try {
                    global.__miracleBridgeSubmitRequest(requestId, action, JSON.stringify(payload));
                    log('Request sent:', action, requestId);
                } catch (e) {
                    clearTimeout(timeout);
                    pendingRequests.delete(requestId);
                    reject(e);
                }
            });
        },

        /**
         * 调用需要服务端处理的方法
         * @param {string} action - 动作名称
         * @param {object} payload - 请求参数
         * @returns {Promise<object>} 响应数据
         */
        callServer(action, payload = {}) {
            return this.call(`server:${action}`, payload);
        },

        /**
         * 监听事件
         * @param {string} eventName - 事件名称
         * @param {function} callback - 回调函数
         */
        on(eventName, callback) {
            if (!eventListeners.has(eventName)) {
                eventListeners.set(eventName, new Set());
            }
            eventListeners.get(eventName).add(callback);
            log('Event listener added:', eventName);
        },

        /**
         * 取消监听事件
         * @param {string} eventName - 事件名称
         * @param {function} callback - 回调函数
         */
        off(eventName, callback) {
            if (eventListeners.has(eventName)) {
                eventListeners.get(eventName).delete(callback);
                log('Event listener removed:', eventName);
            }
        },

        /**
         * 监听事件（只触发一次）
         * @param {string} eventName - 事件名称
         * @param {function} callback - 回调函数
         */
        once(eventName, callback) {
            const onceWrapper = (data) => {
                this.off(eventName, onceWrapper);
                callback(data);
            };
            this.on(eventName, onceWrapper);
        },

        /**
         * 触发事件（内部使用）
         */
        _emit(eventName, data) {
            const listeners = eventListeners.get(eventName);
            if (listeners) {
                listeners.forEach(callback => {
                    try {
                        callback(data);
                    } catch (e) {
                        logError('Event handler error:', e);
                    }
                });
            }
            
            // 同时触发 DOM 事件
            const event = new CustomEvent(eventName, { detail: data });
            global.dispatchEvent(event);
        },

        // ==================== 便捷方法 ====================

        /**
         * 获取玩家信息
         */
        getPlayerInfo() {
            return this.callServer('getPlayerInfo', {});
        },

        /**
         * 获取背包信息
         */
        getInventory() {
            return this.callServer('getInventory', {});
        },

        /**
         * 传送玩家
         */
        teleport(x, y, z) {
            return this.callServer('teleport', { x, y, z });
        },

        /**
         * 发送聊天消息
         */
        sendChat(message) {
            return this.callServer('sendChat', { message });
        },

        /**
         * 执行命令
         */
        executeCommand(command) {
            return this.callServer('executeCommand', { command });
        },

        // ==================== 调试工具 ====================

        /**
         * 启用调试模式
         */
        enableDebug() {
            config.debug = true;
            log('Debug mode enabled');
        },

        /**
         * 获取当前状态
         */
        getStatus() {
            return {
                ready: this.isReady(),
                pendingRequests: pendingRequests.size,
                eventListeners: Array.from(eventListeners.keys()),
                config
            };
        }
    };

    // ==================== 内部回调（由 Java 调用）====================

    /**
     * 处理响应（由 Java 调用）
     */
    global.__miracleBridgeHandleResponse = function(requestId, success, data) {
        const pending = pendingRequests.get(requestId);
        if (pending) {
            log('Response received:', requestId, success);
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
            log('Unknown response:', requestId);
        }
    };

    /**
     * 处理事件（由 Java 调用）
     */
    global.__miracleBridgeHandleEvent = function(eventName, data) {
        log('Event received:', eventName);
        try {
            const parsedData = typeof data === 'string' ? JSON.parse(data) : data;
            MiracleBridge._emit(eventName, parsedData);
        } catch (e) {
            MiracleBridge._emit(eventName, data);
        }
    };

    // 标记已初始化
    MiracleBridge._initialized = true;

    // 导出到全局
    global.MiracleBridge = MiracleBridge;

    log('SDK loaded, version:', VERSION);

})(typeof window !== 'undefined' ? window : this);



