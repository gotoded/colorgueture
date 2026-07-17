package com.hook.colorgueture;

/**
 * 偏好设置键枚举类
 * <p>
 * 统一管理 SharedPreferences 和 XSharedPreferences 的键名，确保配置读写的一致性
 * 每个枚举值对应一个配置项的键名
 */
public enum PrefKeys {
    /**
     * 选中的应用列表
     * 存储格式：JSON 数组，包含应用包名
     */
    SELECTED_APPS("selected_apps"),
    
    /**
     * 手势延迟时间
     * 单位：毫秒
     */
    DELAY_TIME("delay_time"),
    
    /**
     * 应用图标大小
     * 单位：dp
     */
    ICON_SIZE("icon_size"),
    
    /**
     * 应用图标的夹角
     * 单位：度
     */
    APP_ANGLE("app_angle"),
    
    /**
     * 第二圈应用图标的半径
     * 单位：dp
     */
    SECOND_CIRCLE_RADIUS("second_circle_radius"),
    
    /**
     * 是否启用震动
     * 值：true/false
     */
    ENABLE_VIBRATION("enable_vibration"),

    /**
     * 是否启用通知点击悬浮窗启动
     * 值：true/false
     */
    NOTIFICATION_CLICK_ENABLED("notification_click_enabled");

    /**
     * 配置键名
     */
    private final String key;
    
    /**
     * 构造方法
     * @param key 配置键名
     */
    PrefKeys(String key) {
        this.key = key;
    }
    
    /**
     * 获取配置键名
     * @return 配置键名
     */
    public String getKey() {
        return key;
    }
}