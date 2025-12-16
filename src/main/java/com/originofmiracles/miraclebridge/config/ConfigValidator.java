package com.originofmiracles.miraclebridge.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.List;

/**
 * 配置校验器
 * 
 * 负责验证配置文件的合法性，包括：
 * - 值类型校验
 * - 值范围校验
 * - 必填项检查
 */
public class ConfigValidator {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * 校验结果
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        private final String fieldPath;
        
        private ValidationResult(boolean valid, @Nullable String errorMessage, @Nullable String fieldPath) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.fieldPath = fieldPath;
        }
        
        public static ValidationResult success() {
            return new ValidationResult(true, null, null);
        }
        
        public static ValidationResult failure(String fieldPath, String errorMessage) {
            return new ValidationResult(false, errorMessage, fieldPath);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public String getFieldPath() {
            return fieldPath;
        }
        
        @Override
        public String toString() {
            if (valid) {
                return "ValidationResult{valid=true}";
            }
            return String.format("ValidationResult{valid=false, field='%s', error='%s'}", 
                    fieldPath, errorMessage);
        }
    }
    
    /**
     * 校验客户端配置文件
     * 
     * @param configPath 配置文件路径
     * @return 校验结果
     */
    public static ValidationResult validateClientConfig(Path configPath) {
        try {
            CommentedFileConfig config = CommentedFileConfig.builder(configPath)
                    .preserveInsertionOrder()
                    .build();
            config.load();
            
            // 校验 browser 部分
            ValidationResult browserResult = validateBrowserConfig(config);
            if (!browserResult.isValid()) {
                config.close();
                return browserResult;
            }
            
            // 校验 debug 部分
            ValidationResult debugResult = validateDebugConfig(config);
            if (!debugResult.isValid()) {
                config.close();
                return debugResult;
            }
            
            config.close();
            return ValidationResult.success();
            
        } catch (Exception e) {
            LOGGER.error("配置文件解析失败: {}", configPath, e);
            return ValidationResult.failure("(文件)", "配置文件格式错误: " + e.getMessage());
        }
    }
    
    /**
     * 校验服务端配置文件
     */
    public static ValidationResult validateServerConfig(Path configPath) {
        try {
            CommentedFileConfig config = CommentedFileConfig.builder(configPath)
                    .preserveInsertionOrder()
                    .build();
            config.load();
            
            // 校验 bridge 部分
            ValidationResult bridgeResult = validateBridgeConfig(config);
            if (!bridgeResult.isValid()) {
                config.close();
                return bridgeResult;
            }
            
            // 校验 security 部分
            ValidationResult securityResult = validateSecurityConfig(config);
            if (!securityResult.isValid()) {
                config.close();
                return securityResult;
            }
            
            config.close();
            return ValidationResult.success();
            
        } catch (Exception e) {
            LOGGER.error("配置文件解析失败: {}", configPath, e);
            return ValidationResult.failure("(文件)", "配置文件格式错误: " + e.getMessage());
        }
    }
    
    // ==================== 私有校验方法 ====================
    
    private static ValidationResult validateBrowserConfig(CommentedConfig config) {
        Object browserObj = config.get("browser");
        if (browserObj == null) {
            return ValidationResult.success(); // 使用默认值
        }
        
        if (!(browserObj instanceof CommentedConfig browser)) {
            return ValidationResult.failure("browser", "browser 必须是配置节");
        }
        
        // 校验 defaultWidth
        ValidationResult widthResult = validateIntRange(browser, "defaultWidth", 800, 3840);
        if (!widthResult.isValid()) {
            return ValidationResult.failure("browser." + widthResult.getFieldPath(), widthResult.getErrorMessage());
        }
        
        // 校验 defaultHeight
        ValidationResult heightResult = validateIntRange(browser, "defaultHeight", 600, 2160);
        if (!heightResult.isValid()) {
            return ValidationResult.failure("browser." + heightResult.getFieldPath(), heightResult.getErrorMessage());
        }
        
        // 校验 transparentBackground
        ValidationResult transparentResult = validateBoolean(browser, "transparentBackground");
        if (!transparentResult.isValid()) {
            return ValidationResult.failure("browser." + transparentResult.getFieldPath(), transparentResult.getErrorMessage());
        }
        
        // 校验 devServerUrl
        ValidationResult urlResult = validateString(browser, "devServerUrl");
        if (!urlResult.isValid()) {
            return ValidationResult.failure("browser." + urlResult.getFieldPath(), urlResult.getErrorMessage());
        }
        
        return ValidationResult.success();
    }
    
    private static ValidationResult validateDebugConfig(CommentedConfig config) {
        Object debugObj = config.get("debug");
        if (debugObj == null) {
            return ValidationResult.success();
        }
        
        if (!(debugObj instanceof CommentedConfig debug)) {
            return ValidationResult.failure("debug", "debug 必须是配置节");
        }
        
        // 校验布尔值
        for (String key : List.of("enabled", "logBridgeRequests", "logJsExecution")) {
            ValidationResult result = validateBoolean(debug, key);
            if (!result.isValid()) {
                return ValidationResult.failure("debug." + result.getFieldPath(), result.getErrorMessage());
            }
        }
        
        return ValidationResult.success();
    }
    
    private static ValidationResult validateBridgeConfig(CommentedConfig config) {
        Object bridgeObj = config.get("bridge");
        if (bridgeObj == null) {
            return ValidationResult.success();
        }
        
        if (!(bridgeObj instanceof CommentedConfig bridge)) {
            return ValidationResult.failure("bridge", "bridge 必须是配置节");
        }
        
        // 校验 maxRequestSize
        ValidationResult sizeResult = validateIntRange(bridge, "maxRequestSize", 1024, 10485760);
        if (!sizeResult.isValid()) {
            return ValidationResult.failure("bridge." + sizeResult.getFieldPath(), sizeResult.getErrorMessage());
        }
        
        // 校验 requestTimeout
        ValidationResult timeoutResult = validateIntRange(bridge, "requestTimeout", 1000, 120000);
        if (!timeoutResult.isValid()) {
            return ValidationResult.failure("bridge." + timeoutResult.getFieldPath(), timeoutResult.getErrorMessage());
        }
        
        return ValidationResult.success();
    }
    
    private static ValidationResult validateSecurityConfig(CommentedConfig config) {
        Object securityObj = config.get("security");
        if (securityObj == null) {
            return ValidationResult.success();
        }
        
        if (!(securityObj instanceof CommentedConfig security)) {
            return ValidationResult.failure("security", "security 必须是配置节");
        }
        
        // 校验 allowedOrigins 必须是列表
        Object originsObj = security.get("allowedOrigins");
        if (originsObj != null && !(originsObj instanceof List)) {
            return ValidationResult.failure("security.allowedOrigins", "allowedOrigins 必须是字符串列表");
        }
        
        // 校验 enableOriginCheck
        ValidationResult checkResult = validateBoolean(security, "enableOriginCheck");
        if (!checkResult.isValid()) {
            return ValidationResult.failure("security." + checkResult.getFieldPath(), checkResult.getErrorMessage());
        }
        
        return ValidationResult.success();
    }
    
    // ==================== 基础校验方法 ====================
    
    private static ValidationResult validateIntRange(CommentedConfig config, String key, int min, int max) {
        Object value = config.get(key);
        if (value == null) {
            return ValidationResult.success(); // 使用默认值
        }
        
        if (!(value instanceof Number)) {
            return ValidationResult.failure(key, String.format("%s 必须是整数", key));
        }
        
        int intValue = ((Number) value).intValue();
        if (intValue < min || intValue > max) {
            return ValidationResult.failure(key, 
                    String.format("%s 必须在 %d ~ %d 范围内，当前值: %d", key, min, max, intValue));
        }
        
        return ValidationResult.success();
    }
    
    private static ValidationResult validateBoolean(CommentedConfig config, String key) {
        Object value = config.get(key);
        if (value == null) {
            return ValidationResult.success();
        }
        
        if (!(value instanceof Boolean)) {
            return ValidationResult.failure(key, String.format("%s 必须是布尔值 (true/false)", key));
        }
        
        return ValidationResult.success();
    }
    
    private static ValidationResult validateString(CommentedConfig config, String key) {
        Object value = config.get(key);
        if (value == null) {
            return ValidationResult.success();
        }
        
        if (!(value instanceof String)) {
            return ValidationResult.failure(key, String.format("%s 必须是字符串", key));
        }
        
        return ValidationResult.success();
    }
    
    private ConfigValidator() {}
}
