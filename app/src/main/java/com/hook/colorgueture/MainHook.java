package com.hook.colorgueture;

import android.annotation.SuppressLint;
import android.app.AndroidAppHelper;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.util.Log;
import android.view.Gravity;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import androidx.annotation.RequiresApi;
import org.json.JSONArray;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 主钩子类 - 实现 Xposed 模块的核心功能
 * 监听系统 UI 中的边缘手势事件，实现边缘悬停提示功能
 * killall com.android.systemui
 * @noinspection SameReturnValue
 */
public class MainHook implements IXposedHookLoadPackage {
    // Android Handler，用于处理延迟任务
    private static Handler handler;
    // 悬停提示的 Runnable 任务
    private static Runnable hoverRunnable;
    // 标记是否在屏幕边缘按下
    private static boolean isEdgeDown = false;
    // 保存最新的触摸坐标
    private static float lastTouchX = 0;
    private static float lastTouchY = 0;
    // 保存是否在左侧边缘
    private static boolean isLeftEdge = false;
    // 标记是否有弹窗正在显示
    private static boolean isPopupShowing = false;
    // 存储当前显示的弹窗视图
    private static final List<View> currentPopupViews = new ArrayList<>();
    // 存储窗口管理器引用
    private static WindowManager currentWindowManager = null;
    // 存储当前悬停的应用索引
    private static int hoveredAppIndex = -1;
    // 标记是否正在向应用图标滑动
    private static boolean isSlidingToApp = false;
    // 上次悬停检测的时间戳，用于防抖
    private static long lastHoverDetectionTime = 0;
    // 悬停检测的最小时间间隔（毫秒）
    private static final long HOVER_DETECTION_INTERVAL = 100;
    // 模块包名
    private static final String MODULE_PACKAGE = "com.hook.colorgueture";
    // SharedPreferences 名称
    private static final String PREF_NAME = "ColorGueture";
    // 存储当前显示的应用列表
    private static List<AppInfo> currentApps = new ArrayList<>();
    // 缓存：包名→AppInfo (仅用于 TYPE_APP)
    private static final Map<String, AppInfo> appInfoCache = new HashMap<>();
    private static long lastCacheTime = 0;
    private static final long CACHE_EXPIRY_TIME = 60 * 60 * 1000; // 1小时
    private static ClassLoader systemUIClassLoader = null;


    /**
     * 处理应用包加载时的回调,hook开始
     *
     * @param lpparam 包加载参数，包含当前加载的应用信息
     */
    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        // 根据包名执行对应的 Hook
        if ("com.android.systemui".equals(lpparam.packageName)) {
            XposedBridge.log("开始执行hookSystemUI");
            hookSystemUI(lpparam);
            XposedBridge.log("开始执行hookNotificationClick");
            hookNotificationClick(lpparam);
        }
    }


    /**
     * 通用的配置读取方法
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    private static int getConfigInt(String key, int defaultValue) {
        try {
            XSharedPreferences prefs = new XSharedPreferences(MODULE_PACKAGE, PREF_NAME);
            prefs.reload();
            return prefs.getInt(key, defaultValue);
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }

    /**
     * 读取震动配置
     *
     * @return 是否启用震动
     */
    private static boolean getConfigBoolean(String key) {
        try {
            XSharedPreferences prefs = new XSharedPreferences(MODULE_PACKAGE, PREF_NAME);
            prefs.reload();
            return prefs.getBoolean(key, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    /**
     * 触发震动
     *
     * @param context 上下文
     */
    private static void vibrate(Context context) {
        try {
            boolean enableVibration = getConfigBoolean(PrefKeys.ENABLE_VIBRATION.getKey());
            if (enableVibration) {
                Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(100); // 震动100毫秒
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 日志输出方法
     *
     * @param message 日志消息
     */
    private static void log(String message) {
        Log.d("日志HOOK：", message);
    }

    /**
     * 重置弹窗状态
     */
    private static void resetPopupState() {
        isPopupShowing = false;
        currentPopupViews.clear();
        currentWindowManager = null;
        currentApps.clear();
    }

    /**
     * 关闭弹窗的通用方法
     *
     * @param view 要关闭的视图
     * @param wm   窗口管理器
     */
    private static void closePopupView(View view, WindowManager wm) {
        try {
            wm.removeView(view);
        } catch (Exception ignored) {
        }
    }


    /**
     * Hook 系统 UI
     */
    private static void hookSystemUI(XC_LoadPackage.LoadPackageParam lpparam) {
        handler = new Handler(Looper.getMainLooper());

        Context context = AndroidAppHelper.currentApplication();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            prepareFloatWindow(context);
        }

        Class<?> clazz = XposedHelpers.findClass("com.android.systemui.navigationbar.gestural.EdgeBackGestureHandler", lpparam.classLoader);

        // Hook OPPO设备控制磁贴，使其可用
        hookOplusDeviceControlsTile(lpparam);

        XposedHelpers.findAndHookMethod(clazz, "onInputEvent$1", InputEvent.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!(param.args[0] instanceof MotionEvent)) return;
                MotionEvent event = (MotionEvent) param.args[0];
                int action = event.getActionMasked();
                float upX = event.getRawX();
                float upY = event.getRawY();

                lastTouchX = upX;
                lastTouchY = upY;

                Context context = AndroidAppHelper.currentApplication();
                WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                android.view.Display display = wm.getDefaultDisplay();
                android.graphics.Point size = new android.graphics.Point();
                display.getRealSize(size);
                int screenWidth = size.x;
                int screenHeight = size.y;

                int edgeWidth = 400;
                boolean isInEdge = upX < edgeWidth || upX > screenWidth - edgeWidth;
                boolean currentIsLeft = upX < edgeWidth;

                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        if (isInEdge) {
                            isEdgeDown = true;
                            isLeftEdge = currentIsLeft;
                            hoverRunnable = () -> {
                                if (isEdgeDown) {
                                    int threshold = (int) (screenHeight * 8.5 / 10.0);
                                    if (lastTouchY <= threshold) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            vibrate(AndroidAppHelper.currentApplication());
                                            showPreparedPopup((int) lastTouchX, (int) lastTouchY, isLeftEdge);
                                        }
                                    } else {
                                        log("悬停检测失败 - Y轴高度超过阈值 (y: " + lastTouchY + ", 阈值: " + threshold + ")");
                                    }
                                }
                            };
                            int delay = getDelayTime();
                            handler.postDelayed(hoverRunnable, delay);
                        }
                        break;

                    case MotionEvent.ACTION_MOVE:
                        if (isEdgeDown) {
                            if (isInEdge) {
                                isLeftEdge = currentIsLeft;
                            } else {
                                isEdgeDown = false;
                                if (hoverRunnable != null) {
                                    handler.removeCallbacks(hoverRunnable);
                                }
                            }
                        } else if (isPopupShowing) {
                            if (isInEdge) {
                                hidePopup();
                                isSlidingToApp = false;
                                hoveredAppIndex = -1;
                            } else {
                                if (!isSlidingToApp) {
                                    isSlidingToApp = true;
                                }
                                int newHoveredIndex = detectHoveredAppIndex(upX, upY);
                                if (newHoveredIndex != hoveredAppIndex) {
                                    hoveredAppIndex = newHoveredIndex;
                                }
                            }
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (isPopupShowing) {
                            int upHoveredIndex = detectHoveredAppIndex(upX, upY);
                            if (upHoveredIndex != -1 && !currentApps.isEmpty() && upHoveredIndex < currentApps.size()) {
                                AppInfo appInfo = currentApps.get(upHoveredIndex);
                                boolean executed = false;
                                if (appInfo.type == AppInfo.TYPE_SHELL) {
                                    executed = executeShellCommand(appInfo.shellCommand);
                                } else {
                                    executed = startAppInFloatMode(appInfo.packageName);
                                }
                                if (executed) {
                                    hidePopup();
                                    param.setResult(null);
                                    try {
                                        MotionEvent cancelEvent = MotionEvent.obtain(
                                                event.getDownTime(),
                                                event.getEventTime(),
                                                MotionEvent.ACTION_CANCEL,
                                                event.getX(),
                                                event.getY(),
                                                event.getMetaState()
                                        );
                                        XposedBridge.invokeOriginalMethod(param.method, param.thisObject, new Object[]{cancelEvent});
                                        cancelEvent.recycle();
                                    } catch (Exception e) {
                                        log("发送 CANCEL 事件失败: " + e.getMessage());
                                    }
                                }
                            } else {
                                hidePopup();
                            }
                        }
                        isEdgeDown = false;
                        isSlidingToApp = false;
                        hoveredAppIndex = -1;
                        if (hoverRunnable != null) {
                            handler.removeCallbacks(hoverRunnable);
                        }
                        break;
                }
            }
        });
    }

    /**
     * Hook SystemUI 通知点击，拦截通知的 PendingIntent 并以浮窗模式启动
     *
     * @param lpparam 包加载参数
     */
    private static void hookNotificationClick(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> lambdaClass = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.phone.StatusBarNotificationActivityStarter$$ExternalSyntheticLambda8",
                    lpparam.classLoader
            );

            Method[] methods = lambdaClass.getDeclaredMethods();
            for (Method m : methods) {
                if ("startPendingIntent".equals(m.getName())) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            // 如果用户关闭了通知点击悬浮窗功能，则不拦截
                            if (!getConfigBoolean(PrefKeys.NOTIFICATION_CLICK_ENABLED.getKey())) {
                                return;
                            }
                            Object thisObj = param.thisObject;
                            Object entryObj = XposedHelpers.getObjectField(thisObj, "f$3");
                            if (entryObj != null) {
                                String pkgName = (String) XposedHelpers.callMethod(
                                        XposedHelpers.callMethod(entryObj, "getSbn"),
                                        "getPackageName"
                                );
                                if (pkgName != null && startAppInFloatMode(pkgName)) {
                                    param.setResult(0);
                                }
                            }
                        }
                    });
                    break;
                }
            }
        } catch (Throwable e) {
            XposedBridge.log("hookNotificationClick failed: " + e);
        }
    }


    /**
     * Hook OPPO设备控制磁贴，使其始终返回可用状态
     * 同时拦截收藏清空操作，并确保数据持久化
     *
     * @param lpparam 包加载参数
     */
    private static void hookOplusDeviceControlsTile(XC_LoadPackage.LoadPackageParam lpparam) {
        // 1. 在 SystemUI 加载时立即确保磁贴列表包含 controls
        try {
            Context context = AndroidAppHelper.currentApplication();
            if (context != null) {
                android.provider.Settings.Secure.putString(
                        context.getContentResolver(),
                        "sysui_qs_tiles",
                        ensureControlsTile(context)
                );
            }
        } catch (Exception e) {
            XposedBridge.log("hookOplusDeviceControlsTile: 写入 Settings.Secure 失败 - " + e.getMessage());
        }

        // 2. Hook isAvailable 方法，强制返回 true
        try {
            XposedHelpers.findAndHookMethod(
                    "com.oplus.systemui.qs.tiles.OplusDeviceControlsTile",
                    lpparam.classLoader,
                    "isAvailable",
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            return true;
                        }
                    }
            );
        } catch (Throwable e) {
            XposedBridge.log("hookOplusDeviceControlsTile: Hook isAvailable 失败 - " + e.getMessage());
        }

        // 3. Hook 收藏相关方法，阻止空列表清空
        hookControlsFavorites(lpparam.classLoader);
    }

    /**
     * Hook 收藏相关方法，阻止空列表清空内存和覆盖XML
     */
    private static void hookControlsFavorites(ClassLoader cl) {
        try {
            // =========================
            // 1. 阻止 storeFavorites(emptyList)
            // =========================
            Class<?> persistenceCls = XposedHelpers.findClass(
                    "com.android.systemui.controls.controller.ControlsFavoritePersistenceWrapper",
                    cl);

            XposedHelpers.findAndHookMethod(
                    persistenceCls,
                    "storeFavorites",
                    List.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param)
                                throws Throwable {
                            List<?> list = (List<?>) param.args[0];
                            if (list == null || !list.isEmpty()) {
                                return;
                            }
                            File file = (File) XposedHelpers.getObjectField(param.thisObject, "file");
                            if (file != null && file.exists() && file.length() > 200) {
                                XposedBridge.log("[Controls] 阻止 storeFavorites(empty)");
                                param.setResult(null);
                            }
                        }
                    });

            XposedBridge.log("[Controls] hook storeFavorites() success");

            // =========================
            // 2. 拦截 removeStructures - 保护所有 Provider
            // =========================
            Class<?> favoritesCls = XposedHelpers.findClass(
                    "com.android.systemui.controls.controller.Favorites",
                    cl);

            XposedBridge.hookAllMethods(
                    favoritesCls,
                    "removeStructures",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param)
                                throws Throwable {
                            ComponentName cn = (ComponentName) param.args[0];
                            if (cn == null) {
                                return;
                            }
                            String pkg = cn.getPackageName();
                            XposedBridge.log("[Controls] 阻止 removeStructures : " + pkg);
                            param.setResult(false);
                        }
                    });

            XposedBridge.log("[Controls] hook removeStructures() success");

            // =========================
            // 3. 拦截 onComponentRemoved - 保护所有 Provider
            // =========================
            try {
                Class<?> bindingCls = XposedHelpers.findClass(
                        "com.android.systemui.controls.controller.ControlsBindingControllerImpl",
                        cl);
                XposedBridge.hookAllMethods(
                        bindingCls,
                        "onComponentRemoved",
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param)
                                    throws Throwable {
                                ComponentName cn = (ComponentName) param.args[0];
                                if (cn != null) {
                                    XposedBridge.log("[Controls] 阻止 onComponentRemoved : " + cn.getPackageName());
                                    param.setResult(null);
                                }
                            }
                        });
                XposedBridge.log("[Controls] hook onComponentRemoved() success");
            } catch (Throwable e) {
                XposedBridge.log("[Controls] hook onComponentRemoved() failed: " + e.getMessage());
            }

        } catch (Throwable e) {
            XposedBridge.log("[Controls] hook error : " + android.util.Log.getStackTraceString(e));
        }
    }

    /**
     * 确保磁贴列表中包含 "controls"
     *
     * @param context 上下文
     * @return 包含 controls 的磁贴列表字符串
     */
    private static String ensureControlsTile(Context context) {
        String currentTiles = null;
        try {
            currentTiles = android.provider.Settings.Secure.getString(
                    context.getContentResolver(),
                    "sysui_qs_tiles"
            );
        } catch (Exception ignored) {}

        if (currentTiles == null || currentTiles.isEmpty()) {
            return "controls";
        }
        if (currentTiles.contains("controls")) {
            return currentTiles;
        }
        return currentTiles + ",controls";
    }

    /**
     * 获取用户配置的延迟时间
     *
     * @return 延迟时间（毫秒）
     */
    private static int getDelayTime() {
        return getConfigInt(PrefKeys.DELAY_TIME.getKey(), 1);
    }

    /**
     * 读取用户选择的应用和 shell 命令列表
     *
     * @return 用户选择的 AppInfo 列表
     */
    private static List<AppInfo> loadSelectedApps() {
        List<AppInfo> list = new ArrayList<>();
        loadFromSharedPreferences(list);
        return list;
    }

    /**
     * 从 SharedPreferences 加载应用和 shell 命令列表
     *
     * @param list 存储结果的列表
     */
    private static void loadFromSharedPreferences(List<AppInfo> list) {
        try {
            XSharedPreferences prefs = new XSharedPreferences(MODULE_PACKAGE, PREF_NAME);
            prefs.reload();
            String json = prefs.getString(PrefKeys.SELECTED_APPS.getKey(), null);
            if (json != null) {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    try {
                        Object element = arr.get(i);
                        if (element instanceof org.json.JSONObject) {
                            org.json.JSONObject obj = (org.json.JSONObject) element;
                            int type = obj.optInt("t", AppInfo.TYPE_APP);
                            if (type == AppInfo.TYPE_SHELL) {
                                String cmd = obj.optString("c", "");
                                String label = obj.optString("l", "");
                                if (!cmd.isEmpty()) {
                                    list.add(new AppInfo(label.isEmpty() ? "Shell" : label, cmd, null));
                                }
                            } else {
                                String pkg = obj.optString("p", "");
                                if (!pkg.isEmpty()) {
                                    list.add(new AppInfo(pkg, pkg, null));
                                }
                            }
                        } else if (element instanceof String) {
                            // 兼容旧格式：纯字符串包名
                            list.add(new AppInfo((String) element, (String) element, null));
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }


    /**
     * 预创建应用列表悬浮窗（优化性能）
     *
     * @param context 上下文
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private static void prepareFloatWindow(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            List<AppInfo> slots = loadSelectedApps();
            List<AppInfo> apps = new ArrayList<>();
            long currentTime = System.currentTimeMillis();
            boolean cacheExpired = currentTime - lastCacheTime > CACHE_EXPIRY_TIME;
            int maxApps = 10;
            int appCount = 0;
            for (AppInfo slot : slots) {
                if (appCount >= maxApps) break;
                try {
                    if (slot.type == AppInfo.TYPE_SHELL) {
                        // Shell 命令：生成终端风格图标
                        AppInfo shellInfo = new AppInfo(slot.name, slot.shellCommand,
                                generateShellIcon(context, slot.name));
                        apps.add(shellInfo);
                    } else {
                        // 应用：通过 PackageManager 加载
                        String pkg = slot.packageName;
                        AppInfo appInfo;
                        if (!cacheExpired && appInfoCache.containsKey(pkg)) {
                            appInfo = appInfoCache.get(pkg);
                        } else {
                            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                            String name = ai.loadLabel(pm).toString();
                            Drawable icon = ai.loadIcon(pm);
                            appInfo = new AppInfo(name, pkg, icon);
                            appInfoCache.put(pkg, appInfo);
                        }
                        apps.add(appInfo);
                    }
                    appCount++;
                } catch (Exception ignored) {
                }
            }
            lastCacheTime = currentTime;
            if (!apps.isEmpty()) {
                currentApps = apps;
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 显示预创建的悬浮窗（使用缓存的应用信息）
     *
     * @param x      X坐标
     * @param y      Y坐标
     * @param isLeft 是否在左侧
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private static void showPreparedPopup(int x, int y, boolean isLeft) {
        Context context = AndroidAppHelper.currentApplication();
        showFloatWindow(context, x, y, isLeft);
    }

    /**
     * 显示应用列表悬浮窗
     *
     * @param context 上下文
     * @param x       X坐标
     * @param y       Y坐标
     * @param isLeft  是否在左侧
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    @SuppressLint("RtlHardcoded")
    private static void showFloatWindow(Context context, int x, int y, boolean isLeft) {
        if (isPopupShowing) return;
        try {
            isPopupShowing = true;
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            List<AppInfo> slots = loadSelectedApps();
            PackageManager pm = context.getPackageManager();
            List<AppInfo> apps = new ArrayList<>();
            long currentTime = System.currentTimeMillis();
            boolean cacheExpired = currentTime - lastCacheTime > CACHE_EXPIRY_TIME;
            int maxApps = 10;
            int appCount = 0;
            for (AppInfo slot : slots) {
                if (appCount >= maxApps) break;
                try {
                    if (slot.type == AppInfo.TYPE_SHELL) {
                        AppInfo shellInfo = new AppInfo(slot.name, slot.shellCommand,
                                generateShellIcon(context, slot.name));
                        apps.add(shellInfo);
                    } else {
                        String pkg = slot.packageName;
                        AppInfo appInfo;
                        if (!cacheExpired && appInfoCache.containsKey(pkg)) {
                            appInfo = appInfoCache.get(pkg);
                        } else {
                            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                            String name = ai.loadLabel(pm).toString();
                            Drawable icon = ai.loadIcon(pm);
                            appInfo = new AppInfo(name, pkg, icon);
                            appInfoCache.put(pkg, appInfo);
                        }
                        apps.add(appInfo);
                    }
                    appCount++;
                } catch (Exception ignored) {
                }
            }
            lastCacheTime = currentTime;
            currentPopupViews.clear();
            currentWindowManager = wm;
            if (!apps.isEmpty()) {
                currentApps = apps;
                createAppListDialog(context, wm, apps, x, y, isLeft);
            } else {
                resetPopupState();
            }
        } catch (Exception e) {
            resetPopupState();
        }
    }

    /**
     * 应用/命令信息类，用于存储应用或 shell 命令的图标、名称和包名
     */
    private static class AppInfo {
        static final int TYPE_APP = 0;
        static final int TYPE_SHELL = 1;
        int type;
        String name;
        String packageName;
        String shellCommand;
        Drawable icon;

        AppInfo(String name, String packageName, Drawable icon) {
            this.type = TYPE_APP;
            this.name = name;
            this.packageName = packageName;
            this.icon = icon;
        }

        AppInfo(String name, String shellCommand, Drawable icon) {
            this.type = TYPE_SHELL;
            this.name = name;
            this.shellCommand = shellCommand;
            this.icon = icon;
        }
    }

    /**
     * 创建应用列表弹窗视图
     */
    @SuppressLint({"ClickableViewAccessibility", "InlinedApi", "RtlHardcoded", "ObsoleteSdkInt"})
    private static void createAppListDialog(Context context, WindowManager wm, List<AppInfo> apps, int hoverX, int hoverY, boolean isLeft) {
        android.widget.FrameLayout root = new android.widget.FrameLayout(context);
        root.setBackgroundColor(0x00000000);

        int iconSize = getIconSize();
        int radiusFirst = 260;
        int radiusSecondAdd = getSecondCircleRadius();
        int appsPerCircle = 5;

        class Pos { double x; double y; int idx; }
        List<Pos> positions = new ArrayList<>();

        for (int i = 0; i < apps.size(); i++) {
            int circleIndex = i / appsPerCircle;
            int circleAppIndex = i % appsPerCircle;
            int startIdx = circleIndex * appsPerCircle;
            int endIdx = Math.min(startIdx + appsPerCircle, apps.size());
            int circleAppCount = endIdx - startIdx;
            int radius = (circleIndex == 0) ? radiusFirst : (radiusFirst + radiusSecondAdd);
            double stepAngle = Math.toRadians(getAppAngle());
            double angle;
            if (circleAppCount % 2 == 1) {
                int centerIndex = circleAppCount / 2;
                angle = (circleAppIndex - centerIndex) * stepAngle;
            } else {
                double centerIndex = (circleAppCount - 1) / 2.0;
                angle = (circleAppIndex - centerIndex) * stepAngle;
            }
            double xPos = Math.cos(angle) * radius;
            double yPos = Math.sin(angle) * radius;
            if (!isLeft) xPos = -xPos;
            Pos p = new Pos();
            p.x = xPos;
            p.y = yPos;
            p.idx = i;
            positions.add(p);
        }

        double minLeft = Double.POSITIVE_INFINITY, maxRight = Double.NEGATIVE_INFINITY;
        double minTop = Double.POSITIVE_INFINITY, maxBottom = Double.NEGATIVE_INFINITY;
        for (Pos p : positions) {
            double left = p.x - iconSize / 2.0;
            double right = p.x + iconSize / 2.0;
            double top = p.y - iconSize / 2.0;
            double bottom = p.y + iconSize / 2.0;
            if (left < minLeft) minLeft = left;
            if (right > maxRight) maxRight = right;
            if (top < minTop) minTop = top;
            if (bottom > maxBottom) maxBottom = bottom;
        }
        if (positions.isEmpty()) {
            minLeft = -iconSize / 2.0;
            maxRight = iconSize / 2.0;
            minTop = -iconSize / 2.0;
            maxBottom = iconSize / 2.0;
        }
        int totalWidth = (int) Math.ceil(maxRight - minLeft);
        int totalHeight = (int) Math.ceil(maxBottom - minTop);
        int pad = 8;
        totalWidth += pad * 2;
        totalHeight += pad * 2;
        minLeft -= pad;
        minTop -= pad;

        for (Pos p : positions) {
            AppInfo app = apps.get(p.idx);
            ImageView iv = new ImageView(context);
            iv.setImageDrawable(app.icon);
            int leftPx = (int) Math.round(p.x - iconSize / 2.0 - minLeft);
            int topPx = (int) Math.round(p.y - iconSize / 2.0 - minTop);
            android.widget.FrameLayout.LayoutParams iconParams = new android.widget.FrameLayout.LayoutParams(iconSize, iconSize);
            iconParams.leftMargin = leftPx;
            iconParams.topMargin = topPx;
            iv.setLayoutParams(iconParams);
            android.graphics.drawable.GradientDrawable circleBackground = new android.graphics.drawable.GradientDrawable();
            circleBackground.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            circleBackground.setColor(0xFF444444);
            iv.setBackground(circleBackground);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                iv.setClipToOutline(true);
            }
            root.addView(iv);
            currentPopupViews.add(iv);
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        params.format = PixelFormat.RGBA_8888;
        params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        params.width = totalWidth;
        params.height = totalHeight;
        params.gravity = Gravity.LEFT | Gravity.TOP;
        int centerOffsetX = (int) Math.round(-minLeft);
        int centerOffsetY = (int) Math.round(-minTop);
        int rotation = wm.getDefaultDisplay().getRotation();
        int finalX = hoverX - centerOffsetX;
        int finalY = hoverY - centerOffsetY;
        if (rotation == android.view.Surface.ROTATION_0 || rotation == android.view.Surface.ROTATION_180) {
            int statusBar = getStatusBarHeight(context);
            finalY = hoverY - centerOffsetY - statusBar;
        }
        params.x = finalX;
        params.y = finalY;
        params.setTitle("AppListPopup");
        params.flags |= WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        params.windowAnimations = 0;
        try {
            wm.addView(root, params);
            currentWindowManager = wm;
        } catch (Exception e) {
            resetPopupState();
        }
    }

    /**
     * 为 Shell 命令生成带有文字 Bitmap 的 Drawable 图标
     */
    private static Drawable generateShellIcon(Context context, String label) {
        int size = getIconSize();
        String text = label.length() > 2 ? label.substring(0, 2) : label;
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);

        android.graphics.Paint bgPaint = new android.graphics.Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(0xFF2D2D2D);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, bgPaint);

        android.graphics.Paint textPaint = new android.graphics.Paint();
        textPaint.setAntiAlias(true);
        textPaint.setColor(0xFF00FF00);
        textPaint.setTextSize(size * 0.4f);
        textPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
        android.graphics.Paint.FontMetrics fm = textPaint.getFontMetrics();
        float y = size / 2f - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(text, size / 2f, y, textPaint);

        return new android.graphics.drawable.BitmapDrawable(context.getResources(), bitmap);
    }

    private static int getStatusBarHeight(Context ctx) {
        try {
            @SuppressLint({"InternalInsetResource", "DiscouragedApi"}) int resId = ctx.getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resId > 0) {
                return ctx.getResources().getDimensionPixelSize(resId);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private static int getIconSize() {
        return getConfigInt(PrefKeys.ICON_SIZE.getKey(), 100);
    }

    private static int getAppAngle() {
        return getConfigInt(PrefKeys.APP_ANGLE.getKey(), 30);
    }

    private static int getSecondCircleRadius() {
        return getConfigInt(PrefKeys.SECOND_CIRCLE_RADIUS.getKey(), 150);
    }

    private static void hidePopup() {
        if (!isPopupShowing || currentWindowManager == null) return;
        try {
            if (!currentPopupViews.isEmpty()) {
                View firstView = currentPopupViews.get(0);
                if (firstView != null && firstView.getParent() != null) {
                    View parent = (View) firstView.getParent();
                    closePopupView(parent, currentWindowManager);
                }
            }
            resetPopupState();
        } catch (Exception e) {
            resetPopupState();
        }
    }

    private static int detectHoveredAppIndex(float x, float y) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastHoverDetectionTime < HOVER_DETECTION_INTERVAL) {
            return hoveredAppIndex;
        }
        lastHoverDetectionTime = currentTime;
        if (currentPopupViews.isEmpty()) return -1;
        for (int i = 0; i < currentPopupViews.size(); i++) {
            View view = currentPopupViews.get(i);
            if (view != null) {
                int[] location = new int[2];
                view.getLocationOnScreen(location);
                int viewX = location[0];
                int viewY = location[1];
                int viewWidth = view.getWidth();
                int viewHeight = view.getHeight();
                int iconSize = getIconSize();
                int detectionRadius = iconSize / 2 + 30;
                int centerX = viewX + viewWidth / 2;
                int centerY = viewY + viewHeight / 2;
                float distanceX = Math.abs(x - centerX);
                float distanceY = Math.abs(y - centerY);
                if (distanceX <= detectionRadius && distanceY <= detectionRadius) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * 执行 Shell 命令（root 权限）
     */
    private static boolean executeShellCommand(String command) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            p.waitFor();
            log("Shell命令执行成功: " + command);
            return true;
        } catch (Exception e) {
            log("执行Shell命令失败: " + e.getMessage());
            return false;
        }
    }

    private static boolean startAppInFloatMode(String pkg) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "pm resolve-activity --components " + pkg + " | tail -n 1"});
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String cmp = br.readLine();
            br.close();
            p.waitFor();
            if (cmp == null || cmp.isEmpty()) return false;
            String startCmd = "am start --windowingMode 100 " + cmp;
            Process p2 = Runtime.getRuntime().exec(new String[]{"su", "-c", startCmd});
            p2.waitFor();
            return true;
        } catch (Exception e) {
            log("startAppInFloatMode 失败: " + e.getMessage());
            return false;
        }
    }
}