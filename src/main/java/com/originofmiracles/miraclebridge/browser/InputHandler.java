package com.originofmiracles.miraclebridge.browser;

import org.lwjgl.glfw.GLFW;

/**
 * 输入事件处理器
 * 
 * 负责：
 * - 坐标转换（屏幕坐标 → 浏览器坐标）
 * - 修饰键状态管理（Ctrl, Shift, Alt）
 * - 按键映射
 */
public class InputHandler {
    
    /**
     * 当前按下的修饰键
     */
    private int currentModifiers = 0;
    
    /**
     * 修饰键常量（与 CEF 兼容）
     */
    public static final int EVENTFLAG_NONE = 0;
    public static final int EVENTFLAG_CAPS_LOCK_ON = 1;
    public static final int EVENTFLAG_SHIFT_DOWN = 1 << 1;
    public static final int EVENTFLAG_CONTROL_DOWN = 1 << 2;
    public static final int EVENTFLAG_ALT_DOWN = 1 << 3;
    public static final int EVENTFLAG_LEFT_MOUSE_BUTTON = 1 << 4;
    public static final int EVENTFLAG_MIDDLE_MOUSE_BUTTON = 1 << 5;
    public static final int EVENTFLAG_RIGHT_MOUSE_BUTTON = 1 << 6;
    public static final int EVENTFLAG_COMMAND_DOWN = 1 << 7; // macOS
    public static final int EVENTFLAG_NUM_LOCK_ON = 1 << 8;
    
    public InputHandler() {
    }
    
    /**
     * 将屏幕 X 坐标转换为浏览器坐标
     * 
     * @param screenX 屏幕 X 坐标
     * @param screenWidth 屏幕宽度
     * @param browserWidth 浏览器宽度
     * @return 浏览器 X 坐标
     */
    public int scaleX(double screenX, int screenWidth, int browserWidth) {
        if (screenWidth <= 0) return 0;
        return (int) (screenX * browserWidth / screenWidth);
    }
    
    /**
     * 将屏幕 Y 坐标转换为浏览器坐标
     */
    public int scaleY(double screenY, int screenHeight, int browserHeight) {
        if (screenHeight <= 0) return 0;
        return (int) (screenY * browserHeight / screenHeight);
    }
    
    /**
     * 更新修饰键状态
     * 
     * @param keyCode GLFW 键码
     * @param pressed 是否按下
     */
    public void updateModifiers(int keyCode, boolean pressed) {
        int flag = getModifierFlag(keyCode);
        if (flag != EVENTFLAG_NONE) {
            if (pressed) {
                currentModifiers |= flag;
            } else {
                currentModifiers &= ~flag;
            }
        }
    }
    
    /**
     * 从 GLFW modifiers 转换为 CEF modifiers
     */
    public int fromGlfwModifiers(int glfwMods) {
        int cefMods = EVENTFLAG_NONE;
        
        if ((glfwMods & GLFW.GLFW_MOD_SHIFT) != 0) {
            cefMods |= EVENTFLAG_SHIFT_DOWN;
        }
        if ((glfwMods & GLFW.GLFW_MOD_CONTROL) != 0) {
            cefMods |= EVENTFLAG_CONTROL_DOWN;
        }
        if ((glfwMods & GLFW.GLFW_MOD_ALT) != 0) {
            cefMods |= EVENTFLAG_ALT_DOWN;
        }
        if ((glfwMods & GLFW.GLFW_MOD_SUPER) != 0) {
            cefMods |= EVENTFLAG_COMMAND_DOWN;
        }
        if ((glfwMods & GLFW.GLFW_MOD_CAPS_LOCK) != 0) {
            cefMods |= EVENTFLAG_CAPS_LOCK_ON;
        }
        if ((glfwMods & GLFW.GLFW_MOD_NUM_LOCK) != 0) {
            cefMods |= EVENTFLAG_NUM_LOCK_ON;
        }
        
        return cefMods;
    }
    
    /**
     * 获取键码对应的修饰键标志
     */
    private int getModifierFlag(int keyCode) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> EVENTFLAG_SHIFT_DOWN;
            case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> EVENTFLAG_CONTROL_DOWN;
            case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> EVENTFLAG_ALT_DOWN;
            case GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER -> EVENTFLAG_COMMAND_DOWN;
            case GLFW.GLFW_KEY_CAPS_LOCK -> EVENTFLAG_CAPS_LOCK_ON;
            case GLFW.GLFW_KEY_NUM_LOCK -> EVENTFLAG_NUM_LOCK_ON;
            default -> EVENTFLAG_NONE;
        };
    }
    
    /**
     * 获取当前修饰键状态
     */
    public int getCurrentModifiers() {
        return currentModifiers;
    }
    
    /**
     * 重置修饰键状态
     */
    public void resetModifiers() {
        currentModifiers = EVENTFLAG_NONE;
    }
    
    /**
     * 检查是否按下了 Shift
     */
    public boolean isShiftDown() {
        return (currentModifiers & EVENTFLAG_SHIFT_DOWN) != 0;
    }
    
    /**
     * 检查是否按下了 Ctrl
     */
    public boolean isCtrlDown() {
        return (currentModifiers & EVENTFLAG_CONTROL_DOWN) != 0;
    }
    
    /**
     * 检查是否按下了 Alt
     */
    public boolean isAltDown() {
        return (currentModifiers & EVENTFLAG_ALT_DOWN) != 0;
    }
    
    /**
     * 将 GLFW 鼠标按钮转换为 CEF 按钮
     */
    public int toCefMouseButton(int glfwButton) {
        return switch (glfwButton) {
            case GLFW.GLFW_MOUSE_BUTTON_LEFT -> 0;
            case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> 2;
            case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> 1;
            default -> glfwButton;
        };
    }
    
    /**
     * 获取鼠标按钮对应的修饰键标志
     */
    public int getMouseButtonFlag(int button) {
        return switch (button) {
            case GLFW.GLFW_MOUSE_BUTTON_LEFT -> EVENTFLAG_LEFT_MOUSE_BUTTON;
            case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> EVENTFLAG_RIGHT_MOUSE_BUTTON;
            case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> EVENTFLAG_MIDDLE_MOUSE_BUTTON;
            default -> EVENTFLAG_NONE;
        };
    }
}
