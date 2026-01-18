package com.originofmiracles.miraclebridge.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * 共享 Gson 实例工具类
 * 
 * Gson 是线程安全的，应该在整个模组中复用同一个实例，
 * 避免反复创建造成不必要的内存开销。
 */
public final class GsonHelper {
    
    /**
     * 标准 Gson 实例（用于大多数场景）
     */
    private static final Gson STANDARD = new Gson();
    
    /**
     * 格式化 Gson 实例（用于调试输出）
     */
    private static final Gson PRETTY = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    
    /**
     * 宽松 Gson 实例（允许非标准 JSON）
     */
    private static final Gson LENIENT = new GsonBuilder()
            .setLenient()
            .create();
    
    private GsonHelper() {
        // 禁止实例化
    }
    
    /**
     * 获取标准 Gson 实例
     * 适用于：序列化/反序列化正常数据
     */
    public static Gson standard() {
        return STANDARD;
    }
    
    /**
     * 获取格式化 Gson 实例
     * 适用于：日志输出、调试
     */
    public static Gson pretty() {
        return PRETTY;
    }
    
    /**
     * 获取宽松 Gson 实例
     * 适用于：解析可能不规范的外部数据
     */
    public static Gson lenient() {
        return LENIENT;
    }
    
    /**
     * 快捷方法：对象转 JSON 字符串
     */
    public static String toJson(Object obj) {
        return STANDARD.toJson(obj);
    }
    
    /**
     * 快捷方法：JSON 字符串转对象
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        return STANDARD.fromJson(json, clazz);
    }
}
