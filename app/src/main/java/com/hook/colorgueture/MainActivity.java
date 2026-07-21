package com.hook.colorgueture;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Switch;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;

import java.util.Comparator;
import java.util.List;

@SuppressWarnings("ALL")
public class MainActivity extends AppCompatActivity {

    private EditText editText;
    private SharedPreferences prefs;
    private AppAdapter appAdapter;
    private List<AppInfo> installedApps;
    private List<AppInfo> selectedApps;
    private static final int MAX_APPS = 10;

    private int iconSize;
    private int appAngle;
    private int secondCircleRadius;
    private boolean enableVibration;
    private boolean notificationClickEnabled;
    private boolean editMode = false;
    private ItemTouchHelper itemTouchHelper;
    private TextView iconSizeValue;
    private TextView appAngleValue;
    private TextView secondCircleRadiusValue;
    private Switch vibrationSwitch;
    private Switch notificationClickSwitch;


    /**
     * 应用信息类，用于存储应用或 shell 命令的图标、名称和包名
     */
    private static class AppInfo {
        public static final int TYPE_APP = 0;
        public static final int TYPE_SHELL = 1;

        int type; // 0=应用, 1=shell命令
        String name; // 应用名 或 shell 备注
        String packageName; // 应用包名（type=APP）
        String shellCommand; // shell 命令内容（type=SHELL）
        Drawable icon; // 应用图标 或 shell 生成的图标

        AppInfo(String name, String packageName, Drawable icon) {
            this.type = TYPE_APP;
            this.name = name;
            this.packageName = packageName;
            this.icon = icon;
        }

        AppInfo(String name, String shellCommand) {
            this.type = TYPE_SHELL;
            this.name = name;
            this.shellCommand = shellCommand;
        }

        @NonNull
        @Override
        public String toString() {
            return name;
        }
    }

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;

    private static final int REQUEST_STORAGE_PERMISSION = 1002;

    @SuppressLint({"MissingInflatedId", "UnspecifiedRegisterReceiverFlag", "NewApi"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 隐藏 ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 检查并请求悬浮窗权限
        checkOverlayPermission();
        // 检查并请求存储权限
        checkStoragePermission();
        // 检查并请求 root 权限
        checkRootPermission();

        // 初始化控件
        editText = findViewById(R.id.editText);
        Button button = findViewById(R.id.button);
        Button helpButton = findViewById(R.id.helpButton);
        RecyclerView appList = findViewById(R.id.appList);
        SeekBar iconSizeSeekBar = findViewById(R.id.iconSizeSeekBar);
        iconSizeValue = findViewById(R.id.iconSizeValue);
        SeekBar appAngleSeekBar = findViewById(R.id.appSpacingSeekBar);
        appAngleValue = findViewById(R.id.appSpacingValue);
        SeekBar secondCircleRadiusSeekBar = findViewById(R.id.secondCircleRadiusSeekBar);
        secondCircleRadiusValue = findViewById(R.id.secondCircleRadiusValue);
        vibrationSwitch = findViewById(R.id.vibrationSwitch);
        notificationClickSwitch = findViewById(R.id.notificationClickSwitch);
        Button editOrderButton = findViewById(R.id.editOrderButton);

        // 初始化 SharedPreferences，使用 MODE_WORLD_READABLE
        @SuppressLint("WorldReadableFiles")
        SharedPreferences prefs = getSharedPreferences("ColorGueture", MODE_WORLD_READABLE);
        this.prefs = prefs;

        // 加载已保存的设置
        iconSize = prefs.getInt(PrefKeys.ICON_SIZE.getKey(), 100);
        appAngle = prefs.getInt(PrefKeys.APP_ANGLE.getKey(), 30);
        secondCircleRadius = prefs.getInt(PrefKeys.SECOND_CIRCLE_RADIUS.getKey(), 150);
        enableVibration = prefs.getBoolean(PrefKeys.ENABLE_VIBRATION.getKey(), true); // 默认启用震动
        notificationClickEnabled = prefs.getBoolean(PrefKeys.NOTIFICATION_CLICK_ENABLED.getKey(), true); // 默认启用通知点击悬浮窗

        // 设置控件初始值
        iconSizeSeekBar.setProgress(iconSize);
        iconSizeValue.setText(String.valueOf(iconSize));
        appAngleSeekBar.setProgress(appAngle);
        appAngleValue.setText(String.valueOf(appAngle));
        secondCircleRadiusSeekBar.setProgress(secondCircleRadius);
        secondCircleRadiusValue.setText(String.valueOf(secondCircleRadius));
        vibrationSwitch.setChecked(enableVibration);
        notificationClickSwitch.setChecked(notificationClickEnabled);

        // 震动开关监听器
        vibrationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            enableVibration = isChecked;
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(PrefKeys.ENABLE_VIBRATION.getKey(), isChecked);
            editor.apply();
        });

        // 通知点击悬浮窗开关监听器
        notificationClickSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            notificationClickEnabled = isChecked;
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(PrefKeys.NOTIFICATION_CLICK_ENABLED.getKey(), isChecked);
            editor.apply();
        });

        // 帮助按钮点击事件
        helpButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, HelpActivity.class);
            startActivity(intent);
        });

        // 编辑排序按钮
        editOrderButton.setOnClickListener(v -> toggleEditMode());

        // 设置图标大小滑块监听器
        iconSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                iconSize = progress;
                iconSizeValue.setText(String.valueOf(progress));

                // 显示预览弹窗
                showPreviewPopup();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // 隐藏预览弹窗
                hideAllPopups();

                // 保存图标大小到 SharedPreferences
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt(PrefKeys.ICON_SIZE.getKey(), iconSize);
                editor.apply();
            }
        });

        // 设置弹窗夹角滑块监听器
        appAngleSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                appAngle = progress;
                appAngleValue.setText(String.valueOf(progress));

                // 显示预览弹窗
                showPreviewPopup();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // 隐藏预览弹窗
                hideAllPopups();

                // 保存弹窗夹角到 SharedPreferences
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt(PrefKeys.APP_ANGLE.getKey(), appAngle);
                editor.apply();
            }
        });

        // 设置第二圈半径滑块监听器
        secondCircleRadiusSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                secondCircleRadius = progress;
                secondCircleRadiusValue.setText(String.valueOf(progress));

                // 显示预览弹窗
                showPreviewPopup();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // 隐藏预览弹窗
                hideAllPopups();

                // 保存第二圈半径到 SharedPreferences
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt(PrefKeys.SECOND_CIRCLE_RADIUS.getKey(), secondCircleRadius);
                editor.apply();
            }
        });

        // 加载已保存的延迟时间
        int savedDelay = prefs.getInt(PrefKeys.DELAY_TIME.getKey(), 200/*默认延迟触发时间200ms*/);
        editText.setText(String.valueOf(savedDelay));

        // 保存按钮点击事件
        button.setOnClickListener(v -> saveDelayTime());

        // 初始化应用列表
        loadAppsAsync();

    }

    /**
     * 检查并请求存储权限
     */
    @SuppressLint("ObsoleteSdkInt")
    private void checkStoragePermission() {
        // // android.util.Log.d("ColorGueture", "开始检查存储权限");

        // 对于 Android 11+，请求管理外部存储权限
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // // android.util.Log.d("ColorGueture", "Android 11+ 设备，检查管理外部存储权限");
            if (!android.os.Environment.isExternalStorageManager()) {
                // // android.util.Log.d("ColorGueture", "未获得管理外部存储权限，跳转到设置页面");
                android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            } else {
                // // android.util.Log.d("ColorGueture", "已获得管理外部存储权限");
            }
        } else {
            // 对于 Android 6.0-10，请求传统存储权限
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                // // android.util.Log.d("ColorGueture", "Android 6.0-10 设备，检查传统存储权限");
                if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED ||
                        checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    // // android.util.Log.d("ColorGueture", "未获得传统存储权限，请求权限");
                    requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
                } else {
                    // // android.util.Log.d("ColorGueture", "已获得传统存储权限");
                }
            } else {
                // Android 5.1 及以下，不需要运行时权限
                // // android.util.Log.d("ColorGueture", "Android 5.1 及以下设备，不需要运行时权限");
            }
        }
    }

    /**
     * 检查存储权限状态
     *
     * @return 是否有存储权限
     */
    @SuppressLint("ObsoleteSdkInt")
    private boolean hasStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            return android.os.Environment.isExternalStorageManager();
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            return checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } else {
            return true; // Android 5.1 及以下
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @androidx.annotation.NonNull String[] permissions, @androidx.annotation.NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                // // android.util.Log.d("ColorGueture", "存储权限获取成功");
            } else {
                // // android.util.Log.d("ColorGueture", "存储权限获取失败");
            }
        }
    }

    /**
     * 监听返回按钮，触发应用列表弹窗
     */
    @SuppressLint({"GestureBackNavigation", "MissingSuperCall"})
    @Override
    public void onBackPressed() {
        // 对 back 键的监听功能
//         showAppListDialog();
        // 执行默认的返回操作
        super.onBackPressed();
    }

    /**
     * 缩放视图
     *
     * @param view  要缩放的视图
     * @param scale 缩放比例
     */
    private void scaleView(View view, float scale) {
        // 使用属性动画实现平滑的缩放效果
        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(1.0f, scale);
        animator.setDuration(100); // 动画持续时间
        animator.addUpdateListener(animation -> {
            float currentScale = (float) animation.getAnimatedValue();
            view.setScaleX(currentScale);
            view.setScaleY(currentScale);
        });
        animator.start();
    }

    // 存储所有创建的弹窗视图
    private final List<View> popupViews = new ArrayList<>();

    /**
     * 隐藏所有应用弹窗
     */
    private void hideAllPopups() {
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        List<View> viewsToRemove = new ArrayList<>(popupViews);
        popupViews.clear();

        for (View view : viewsToRemove) {
            try {
                // 直接移除视图，无动画，确保立即消失
                wm.removeView(view);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 显示预览弹窗，用于实时预览图标大小和夹角变化
     */
    private void showPreviewPopup() {
        // 首先强制移除所有之前的弹窗，不使用动画，确保立即清除
        hideAllPopups();

        // 检查悬浮窗权限
        if (!Settings.canDrawOverlays(this)) {
            return;
        }

        // 加载用户选择的应用
        List<AppInfo> apps = selectedApps;

        if (!apps.isEmpty() && !allAppsNull(apps)) {
            // 创建主容器作为根View
            FrameLayout rootContainer = new FrameLayout(this);
            rootContainer.setBackgroundColor(0x00000000); // 透明背景

            // 计算总应用数，用于布局
            int appCount = 0;
            int totalApps = 0;
            for (AppInfo app : apps) {
                if (app != null) totalApps++;
            }

            // 计算每个应用的位置，弧形布局
            for (AppInfo app : apps) {
                if (app != null) {
                    try {
                        // 计算弧形布局的偏移量
                        int[] offsets = calculatePreviewPosition(appCount, totalApps);
                        int xOffset = offsets[0];
                        int yOffset = offsets[1];

                        // 创建应用图标视图
                        LinearLayout appLayout = new LinearLayout(this);
                        appLayout.setOrientation(LinearLayout.VERTICAL);
                        appLayout.setPadding(20, 20, 20, 20);
                        appLayout.setBackgroundColor(0x00000000);

                        // 添加图标
                        ImageView iv = new ImageView(this);
                        iv.setImageDrawable(app.icon);
                        iv.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
                        
                        // 设置圆形背景
                        android.graphics.drawable.GradientDrawable circleBackground = new android.graphics.drawable.GradientDrawable();
                        circleBackground.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                        circleBackground.setColor(0xFF444444); // 设置背景颜色为灰色
                        iv.setBackground(circleBackground);
                        
                        // 启用裁剪到轮廓
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                            iv.setClipToOutline(true);
                        }
                        
                        appLayout.addView(iv);



                        // 设置布局参数
                        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                FrameLayout.LayoutParams.WRAP_CONTENT
                        );

                        // 计算屏幕中心位置
                        DisplayMetrics metrics = getResources().getDisplayMetrics();
                        int screenWidth = metrics.widthPixels;
                        int screenHeight = metrics.heightPixels;

                        // 设置图标的位置，基于屏幕中心
                        layoutParams.leftMargin = screenWidth / 2 + xOffset - iconSize / 2;
                        layoutParams.topMargin = screenHeight / 2 + yOffset - iconSize / 2;

                        // 添加到主容器
                        rootContainer.addView(appLayout, layoutParams);

                        // 预览模式：立即显示，无动画
                        appLayout.setAlpha(1f);
                        appLayout.setScaleX(1f);
                        appLayout.setScaleY(1f);

                        appCount++;
                    } catch (Exception ignored) {
                    }
                }
            }

            // 设置悬浮窗参数
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            params.format = PixelFormat.RGBA_8888;
            params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            params.gravity = Gravity.LEFT | Gravity.TOP;
            params.flags |= WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            params.windowAnimations = 0; // 禁用动画，确保弹窗立即消失

            // 添加根View到窗口管理器
            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            try {
                wm.addView(rootContainer, params);
                popupViews.add(rootContainer);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 计算预览弹窗的弧形布局位置
     *
     * @param index     应用索引
     * @param totalApps 总应用数
     * @return 包含xOffset和yOffset的数组
     */
    private int[] calculatePreviewPosition(int index, int totalApps) {
        int radius;
        int circleIndex;
        int appsPerCircle = 5;

        // 计算当前应用属于第几圈
        circleIndex = index / appsPerCircle;

        // 根据圈数确定半径
        if (circleIndex == 0) {
            radius = 250; // 第一圈半径
        } else {
            radius = 250 + secondCircleRadius; // 第二圈及以上半径（第一圈半径+自定义范围）
        }

        // 计算当前圈的应用索引
        int circleAppIndex = index % appsPerCircle;
        int circleAppCount = Math.min(appsPerCircle, totalApps - circleIndex * appsPerCircle);

        double angleRad = Math.toRadians(appAngle);

        double angle;
        if (circleAppCount == 1) {
            angle = 0; // 只有一个应用时，放在水平方向
        } else if (circleAppCount == 2) {
            // 两个应用：第一个在上方，第二个在下方，夹角为自定义夹角的一半
            if (circleAppIndex == 0) {
                angle = -angleRad / 2; // 上方
            } else {
                angle = angleRad / 2; // 下方
            }
        } else if (circleAppIndex == 0) {
            angle = 0; // 每圈的第一个应用都放在水平方向
        } else if (circleAppCount % 2 == 1) {
            // 单数个应用：第一个应用在水平方向，其他对称分布
            if (circleAppIndex % 2 == 1) {
                // 奇数索引（从1开始）：在第一个应用的上方
                int upperIndex = (circleAppIndex + 1) / 2;
                angle = -upperIndex * angleRad; // 负角度表示上方
            } else {
                // 偶数索引（从2开始）：在第一个应用的下方
                int lowerIndex = circleAppIndex / 2;
                angle = lowerIndex * angleRad; // 正角度表示下方
            }
        } else {
            // 双数个应用（大于2）：第一个应用在水平方向，其他对称分布
            int halfCount = circleAppCount / 2;
            if (circleAppIndex < halfCount) {
                // 前半部分：在上方
                angle = -((circleAppIndex) * angleRad); // 负角度表示上方
            } else {
                // 后半部分：在下方
                int lowerIndex = circleAppIndex - halfCount;
                angle = (lowerIndex) * angleRad; // 正角度表示下方
            }
        }

        int xOffset = (int) (Math.cos(angle) * radius);
        int yOffset = (int) (Math.sin(angle) * radius);

        return new int[]{xOffset, yOffset};
    }

    /**
     * 显示应用列表弹窗
     */
    private void showAppListDialog() {
        // android.util.Log.d("ColorGueture", "显示应用列表弹窗");

        // 检查悬浮窗权限
        if (!Settings.canDrawOverlays(this)) {
            checkOverlayPermission();
            return;
        }

        // 先隐藏之前的弹窗
        hideAllPopups();

        // 加载用户选择的应用
        List<AppInfo> apps = selectedApps;

        if (!apps.isEmpty() && !allAppsNull(apps)) {
            // 创建主容器作为根View
            FrameLayout rootContainer = new FrameLayout(this);
            rootContainer.setBackgroundColor(0x00000000); // 透明背景

            // 计算总应用数，用于布局
            int appCount = 0;
            int totalApps = 0;
            for (AppInfo app : apps) {
                if (app != null) totalApps++;
            }

            // 计算每个应用的位置，弧形布局
            for (AppInfo app : apps) {
                if (app != null) {
                    try {
                        // 计算弧形布局的偏移量
                        int[] offsets = calculatePreviewPosition(appCount, totalApps);
                        int xOffset = offsets[0];
                        int yOffset = offsets[1];

                        // 创建应用图标视图
                        LinearLayout appLayout = new LinearLayout(this);
                        appLayout.setOrientation(LinearLayout.VERTICAL);
                        appLayout.setPadding(20, 20, 20, 20);
                        appLayout.setBackgroundColor(0x00000000);

                        // 添加图标
                        ImageView iv = new ImageView(this);
                        iv.setImageDrawable(app.icon);
                        iv.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
                        
                        // 设置圆形背景
                        android.graphics.drawable.GradientDrawable circleBackground = new android.graphics.drawable.GradientDrawable();
                        circleBackground.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                        circleBackground.setColor(0xFF444444); // 设置背景颜色为灰色
                        iv.setBackground(circleBackground);
                        
                        // 启用裁剪到轮廓
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                            iv.setClipToOutline(true);
                        }
                        
                        appLayout.addView(iv);

                        // 添加点击事件
                        String packageName = app.packageName;
                        appLayout.setOnClickListener(v -> {
                            try {
                                // 通过命令行以小窗模式启动应用
                                String command = "cmp=$(pm resolve-activity --components " + packageName + ") && am start --windowingMode 100 $cmp";
                                Runtime.getRuntime().exec(new String[]{"su", "-c", command});
                                // 隐藏所有应用弹窗
                                hideAllPopups();
                            } catch (Exception e) {
                                // android.util.Log.e("ColorGueture", "启动应用失败: " + e.getMessage());
                            }
                        });

                        // 添加触摸事件监听，实现悬停放大效果
                        appLayout.setOnTouchListener((v, event) -> {
                            switch (event.getAction()) {
                                case MotionEvent.ACTION_HOVER_ENTER:
                                case MotionEvent.ACTION_DOWN:
                                case MotionEvent.ACTION_MOVE:
                                    // 鼠标悬停或触摸时，整个弹窗放大到1.4倍
                                    scaleView(appLayout, 1.4f);
                                    break;
                                case MotionEvent.ACTION_HOVER_EXIT:
                                case MotionEvent.ACTION_UP:
                                case MotionEvent.ACTION_CANCEL:
                                    // 鼠标离开或触摸结束时，整个弹窗恢复原始大小
                                    scaleView(appLayout, 1.0f);
                                    break;
                            }
                            return false; // 返回 false，允许事件继续传递，确保点击事件可以被触发
                        });

                        // 设置布局参数
                        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                FrameLayout.LayoutParams.WRAP_CONTENT
                        );

                        // 计算屏幕中心位置
                        DisplayMetrics metrics = getResources().getDisplayMetrics();
                        int screenWidth = metrics.widthPixels;
                        int screenHeight = metrics.heightPixels;

                        // 设置图标的位置，基于屏幕中心
                        layoutParams.leftMargin = screenWidth / 2 + xOffset - iconSize / 2;
                        layoutParams.topMargin = screenHeight / 2 + yOffset - iconSize / 2;

                        // 添加到主容器
                        rootContainer.addView(appLayout, layoutParams);

                        // 设置初始状态：透明且缩小到0
                        appLayout.setAlpha(0f);
                        appLayout.setScaleX(0f);
                        appLayout.setScaleY(0f);

                        appCount++;
                    } catch (Exception ignored) {
                    }
                }
            }

            // 设置悬浮窗参数
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            params.format = PixelFormat.RGBA_8888;
            params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            params.gravity = Gravity.LEFT | Gravity.TOP;
            params.flags |= WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

            // 添加根View到窗口管理器
            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            try {
                wm.addView(rootContainer, params);
                popupViews.add(rootContainer);

                // 添加出现动画
                for (int i = 0; i < rootContainer.getChildCount(); i++) {
                    View child = rootContainer.getChildAt(i);
                    child.post(() -> {
                        child.animate()
                                .alpha(1f)
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(500)
                                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                                .start();
                    });
                }
            } catch (Exception ignored) {
            }
        }
    }


    /**
     * 检查并请求悬浮窗权限
     */
    private void checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
        }
    }

    /**
     * 检查并请求 root 权限
     */
    private void checkRootPermission() {
        new Thread(() -> {
            try {
                // 尝试执行一个简单的 su 命令来检查是否有 root 权限
                Process process = Runtime.getRuntime().exec("su");
                process.waitFor();
                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    // 没有 root 权限
                    // android.util.Log.d("ColorGueture", "需要 root 权限才能以小窗模式启动应用");
                }
            } catch (Exception e) {
                // 执行失败，可能没有 root 权限
                // android.util.Log.e("ColorGueture", "检查 root 权限失败: " + e.getMessage());
            }
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (!Settings.canDrawOverlays(this)) {
                // android.util.Log.d("ColorGueture", "需要悬浮窗权限才能显示应用列表");
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 修复 SharedPreferences 文件权限
        fixPermissionsAsync(this);
    }

    /**
     * 修复 SharedPreferences 文件权限
     */
    private static void fixPermissionsAsync(Context context) {
        new Thread(() -> {
            try {
                // 获取 SharedPreferences 文件路径
                File sharedPrefsFile = new File(context.getFilesDir().getParent() + "/shared_prefs/ColorGueture.xml");
                if (sharedPrefsFile.exists()) {
                    // 设置文件权限为可读
                    Process process = Runtime.getRuntime().exec(new String[]{"chmod", "644", sharedPrefsFile.getAbsolutePath()});
                    process.waitFor();
                    // android.util.Log.d("ColorGueture", "SharedPreferences 文件权限已修复");
                }
            } catch (Exception e) {
                // android.util.Log.e("ColorGueture", "修复权限失败: " + e.getMessage());
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /**
     * 异步加载应用列表
     */
    private void loadAppsAsync() {
        new Thread(() -> {
            // 加载已安装的应用
            List<AppInfo> apps = getInstalledApps();
            installedApps = apps;

            // 加载已选择的应用
            List<AppInfo> selected = loadSelectedApps();
            selectedApps = selected;

            // 在主线程中更新UI
            runOnUiThread(() -> {
                // 设置 RecyclerView 网格布局，2行6列
                GridLayoutManager layoutManager = new GridLayoutManager(MainActivity.this, 5);
                RecyclerView appList = findViewById(R.id.appList);
                appList.setLayoutManager(layoutManager);

                // 创建并设置适配器
                appAdapter = new AppAdapter(selectedApps);
                appList.setAdapter(appAdapter);

                // 初始化拖拽排序
                ItemTouchHelper.SimpleCallback dragCallback = new ItemTouchHelper.SimpleCallback(
                        ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {
                    @Override
                    public boolean onMove(@NonNull RecyclerView recyclerView,
                                          @NonNull RecyclerView.ViewHolder viewHolder,
                                          @NonNull RecyclerView.ViewHolder target) {
                        int from = viewHolder.getBindingAdapterPosition();
                        int to = target.getBindingAdapterPosition();
                        if (from >= 0 && from < selectedApps.size() && to >= 0 && to < selectedApps.size()) {
                            AppInfo fromApp = selectedApps.get(from);
                            AppInfo toApp = selectedApps.get(to);
                            selectedApps.set(from, toApp);
                            selectedApps.set(to, fromApp);
                            appAdapter.notifyItemMoved(from, to);
                            saveSelectedApps();
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}

                    @Override
                    public boolean isLongPressDragEnabled() {
                        return editMode;
                    }

                    @Override
                    public boolean isItemViewSwipeEnabled() {
                        return false;
                    }
                };
                itemTouchHelper = new ItemTouchHelper(dragCallback);
                itemTouchHelper.attachToRecyclerView(appList);
            });
        }).start();
    }

    /**
     * 获取设备上已安装的应用列表（包含系统应用）
     *
     * @return 应用列表
     */
    private List<AppInfo> getInstalledApps() {
        List<AppInfo> apps = new ArrayList<>();
        PackageManager pm = getPackageManager();

        // 获取所有已安装的应用（包括系统应用）
        List<ApplicationInfo> installedApps = pm.getInstalledApplications(PackageManager.MATCH_ALL);

        for (ApplicationInfo appInfo : installedApps) {
            String appName = appInfo.loadLabel(pm).toString();
            if (appName == null || appName.isEmpty()) {
                appName = appInfo.packageName;
            }
            String packageName = appInfo.packageName;
            Drawable icon = appInfo.loadIcon(pm);
            AppInfo app = new AppInfo(appName, packageName, icon != null ? icon : getDrawable(android.R.drawable.ic_menu_info_details));
            apps.add(app);
        }

        // 按应用名称排序
        apps.sort(Comparator.comparing(a -> a.name));
        // 包名相同的去重（系统应用可能有多个 entry）
        List<AppInfo> uniqueApps = new ArrayList<>();
        java.util.HashSet<String> seenPkgs = new java.util.HashSet<>();
        for (AppInfo app : apps) {
            if (!seenPkgs.contains(app.packageName)) {
                seenPkgs.add(app.packageName);
                uniqueApps.add(app);
            }
        }
        return uniqueApps;
    }

    /**
     * 显示应用选择对话框
     *
     * @param position 要设置的位置
     */
    @SuppressLint("NotifyDataSetChanged")
    private void showAppSelectionDialog(final int position) {
        // android.util.Log.d("ColorGueture", "显示应用选择对话框，位置: " + position);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择应用");

        // 过滤掉已经选择过的应用，但保留当前位置的应用（允许替换）
        List<AppInfo> availableApps = new ArrayList<>();
        for (AppInfo app : installedApps) {
            boolean isSelected = false;
            for (int i = 0; i < selectedApps.size(); i++) {
                AppInfo selectedApp = selectedApps.get(i);
                if (i != position && selectedApp != null && selectedApp.packageName != null && selectedApp.packageName.equals(app.packageName)) {
                    isSelected = true;
                    break;
                }
            }
            if (!isSelected) {
                availableApps.add(app);
            }
        }

        // 提取应用名称数组
        CharSequence[] appNames = new CharSequence[availableApps.size()];
        for (int i = 0; i < availableApps.size(); i++) {
            appNames[i] = availableApps.get(i).name;
        }

        builder.setSingleChoiceItems(appNames, -1, (dialog, which) -> {
            AppInfo app = availableApps.get(which);
            // android.util.Log.d("ColorGueture", "用户选择应用: " + app.name + " (" + app.packageName + ")");
            selectedApps.set(position, app);
            // android.util.Log.d("ColorGueture", "更新后 selectedApps 大小: " + selectedApps.size());
            for (int i = 0; i < selectedApps.size(); i++) {
                AppInfo a = selectedApps.get(i);
                // android.util.Log.d("ColorGueture", "selectedApps[" + i + "]: " + (a != null ? a.name + " (" + a.packageName + ")" : "null"));
            }
            appAdapter.notifyDataSetChanged();
            saveSelectedApps();
            dialog.dismiss();
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /**
     * 显示类型选择对话框：选择应用 或 自定义 Shell 命令
     */
    private void showSlotTypeChoiceDialog(final int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择操作类型");
        String[] choices = {"选择应用", "自定义 Shell 命令"};
        builder.setSingleChoiceItems(choices, -1, (dialog, which) -> {
            dialog.dismiss();
            if (which == 0) {
                showAppSelectionDialog(position);
            } else {
                showShellInputDialog(position);
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /**
     * 显示 Shell 命令输入对话框
     */
    private void showShellInputDialog(final int position) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 30);

        // 备注标签输入
        TextView labelHint = new TextView(this);
        labelHint.setText("备注名称");
        labelHint.setTextSize(14);
        layout.addView(labelHint);

        EditText labelInput = new EditText(this);
        labelInput.setHint("例如：打开手电筒");
        layout.addView(labelInput);

        // 命令输入
        TextView cmdHint = new TextView(this);
        cmdHint.setText("Shell 命令（root 执行）");
        cmdHint.setTextSize(14);
        LinearLayout.LayoutParams cmdMargin = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cmdMargin.topMargin = 30;
        cmdHint.setLayoutParams(cmdMargin);
        layout.addView(cmdHint);

        EditText cmdInput = new EditText(this);
        cmdInput.setHint("例如：cmd battery set status 2");
        layout.addView(cmdInput);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("自定义 Shell 命令");
        builder.setView(layout);
        builder.setPositiveButton("确定", (dialog, which) -> {
            String label = labelInput.getText().toString().trim();
            String cmd = cmdInput.getText().toString().trim();
            if (!cmd.isEmpty()) {
                if (label.isEmpty()) {
                    label = "Shell";
                }
                selectedApps.set(position, new AppInfo(label, cmd));
                appAdapter.notifyDataSetChanged();
                saveSelectedApps();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /**
     * 切换编辑排序模式：开启后图标晃动，可长按拖拽排列顺序
     */
    private void toggleEditMode() {
        editMode = !editMode;
        Button editBtn = findViewById(R.id.editOrderButton);
        editBtn.setText(editMode ? "完成" : "编辑排序");
        appAdapter.notifyDataSetChanged();
    }

    /**
     * 为 Shell 命令生成一个带文字的圆形图标
     */
    private static Drawable generateShellIcon(Context context, String label) {
        int size = 150;
        String text = label.length() > 2 ? label.substring(0, 2) : label;
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);

        // 画圆形背景（深色终端风格）
        android.graphics.Paint bgPaint = new android.graphics.Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(0xFF2D2D2D); // 深灰色
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, bgPaint);

        // 画文字
        android.graphics.Paint textPaint = new android.graphics.Paint();
        textPaint.setAntiAlias(true);
        textPaint.setColor(0xFF00FF00); // 绿色终端文字
        textPaint.setTextSize(size * 0.4f);
        textPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
        android.graphics.Paint.FontMetrics fm = textPaint.getFontMetrics();
        float y = size / 2f - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(text, size / 2f, y, textPaint);

        return new android.graphics.drawable.BitmapDrawable(context.getResources(), bitmap);
    }

    /**
     * 保存选择的应用到外部存储
     */
    private void saveSelectedApps() {
        // 检查存储权限
        if (!hasStoragePermission()) {
            // android.util.Log.d("ColorGueture", "存储权限获取失败，尝试重新请求权限");
            checkStoragePermission();
            return;
        }

        JSONArray jsonArray = new JSONArray();
        for (AppInfo app : selectedApps) {
            if (app != null) {
                org.json.JSONObject obj = new org.json.JSONObject();
                try {
                    if (app.type == AppInfo.TYPE_SHELL) {
                        obj.put("t", AppInfo.TYPE_SHELL);
                        obj.put("c", app.shellCommand != null ? app.shellCommand : "");
                        obj.put("l", app.name != null ? app.name : "");
                    } else {
                        obj.put("t", AppInfo.TYPE_APP);
                        obj.put("p", app.packageName != null ? app.packageName : "");
                    }
                    jsonArray.put(obj);
                } catch (org.json.JSONException ignored) {
                }
            }
        }
        final String jsonString = jsonArray.toString();

        // 保存到SharedPreferences（本地使用）
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PrefKeys.SELECTED_APPS.getKey(), jsonString);
        boolean spSuccess = editor.commit();
        // android.util.Log.d("ColorGueture", "SharedPreferences 保存结果: " + spSuccess);

        // 在后台线程中执行文件操作
        new Thread(() -> {
            // 保存的应用
            // android.util.Log.d("ColorGueture", "保存的应用列表 JSON: " + jsonString);

            // 保存到外部存储（供MainHook读取）
            boolean extSuccess = saveToExternalStorage(jsonString);
            // android.util.Log.d("ColorGueture", "外部存储保存结果: " + extSuccess);

            // 验证外部存储文件是否存在
            verifyExternalStorageFile();

            // 验证保存结果
            try {
                String savedJson = prefs.getString("selected_apps", null);
                // android.util.Log.d("ColorGueture", "验证保存结果 - 读取到的 JSON: " + savedJson);
            } catch (Exception e) {
                // android.util.Log.e("ColorGueture", "验证保存结果失败: " + e.getMessage());
            }
        }).start();
    }

    /**
     * 验证外部存储文件是否存在
     */
    private void verifyExternalStorageFile() {
        try {
            // 尝试多种外部存储路径
            @SuppressLint("SdCardPath") String[] possiblePaths = {
                    // 标准外部存储目录
                    getExternalFilesDir(null) + "/ColorGueture/selected_apps.json",
                    // 直接路径
                    "/storage/emulated/0/Android/data/com.hook.colorgueture/files/ColorGueture/selected_apps.json",
                    // sdcard 路径
                    "/sdcard/Android/data/com.hook.colorgueture/files/ColorGueture/selected_apps.json"
            };

            for (String filePath : possiblePaths) {
                File file = new File(filePath);
                // android.util.Log.d("ColorGueture", "验证外部存储文件: " + filePath);
                // android.util.Log.d("ColorGueture", "文件存在: " + file.exists());
                if (file.exists()) {
                    // android.util.Log.d("ColorGueture", "文件大小: " + file.length() + " 字节");
                    // android.util.Log.d("ColorGueture", "文件可读: " + file.canRead());
                    // 读取文件内容进行验证
                    try {
                        BufferedReader reader = new BufferedReader(new FileReader(file));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        reader.close();
                        String content = sb.toString();
                        // android.util.Log.d("ColorGueture", "文件内容: " + content);
                    } catch (Exception e) {
                        // android.util.Log.e("ColorGueture", "读取验证文件失败: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            // android.util.Log.e("ColorGueture", "验证外部存储文件失败: " + e.getMessage());
        }
    }

    /**
     * 保存应用选择到外部存储
     */
    private boolean saveToExternalStorage(String jsonString) {
        try {
            // 尝试多种外部存储路径
            @SuppressLint("SdCardPath") File[] possibleDirs = {
                    // 标准外部存储目录
                    new File(getExternalFilesDir(null), "ColorGueture"),
                    // 直接路径
                    new File("/storage/emulated/0/Android/data/com.hook.colorgueture/files/ColorGueture"),
                    // sdcard 路径
                    new File("/sdcard/Android/data/com.hook.colorgueture/files/ColorGueture")
            };

            for (File externalDir : possibleDirs) {
                // android.util.Log.d("ColorGueture", "尝试外部存储目录: " + externalDir.getAbsolutePath());

                // 确保目录存在
                if (!externalDir.exists()) {
                    // android.util.Log.d("ColorGueture", "创建外部存储目录");
                    boolean dirCreated = externalDir.mkdirs();
                    // android.util.Log.d("ColorGueture", "目录创建结果: " + dirCreated);
                    if (!dirCreated) {
                        // android.util.Log.d("ColorGueture", "目录创建失败，尝试下一个路径");
                        continue;
                    }
                }

                // 检查目录是否可写
                if (!externalDir.canWrite()) {
                    // android.util.Log.d("ColorGueture", "目录不可写，尝试下一个路径");
                    continue;
                }

                // 创建保存文件
                File appsFile = new File(externalDir, "selected_apps.json");
                // android.util.Log.d("ColorGueture", "外部存储文件路径: " + appsFile.getAbsolutePath());

                // 写入文件
                FileWriter writer = new FileWriter(appsFile);
                writer.write(jsonString);
                writer.close();

                // 验证文件是否创建成功
                if (appsFile.exists() && appsFile.length() > 0) {
                    // android.util.Log.d("ColorGueture", "外部存储文件写入成功，文件大小: " + appsFile.length() + " 字节");
                    // android.util.Log.d("ColorGueture", "文件内容: " + jsonString);
                    return true;
                } else {
                    // android.util.Log.d("ColorGueture", "文件创建失败，尝试下一个路径");
                }
            }

            // 所有路径都失败
            // android.util.Log.e("ColorGueture", "所有外部存储路径都失败");
            return false;
        } catch (Exception e) {
            // android.util.Log.e("ColorGueture", "保存到外部存储失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }


    /**
     * /.检查应用列表是否全为null
     */
    private boolean allAppsNull(List<AppInfo> apps) {
        for (AppInfo app : apps) {
            if (app != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * 从 SharedPreferences 加载选择的应用或 shell 命令
     *
     * @return 应用列表，确保有 MAX_APPS 个元素
     */
    private List<AppInfo> loadSelectedApps() {
        List<AppInfo> apps = new ArrayList<>();
        String json = prefs.getString("selected_apps", null);

        if (json != null) {
            try {
                JSONArray jsonArray = new JSONArray(json);

                // 创建包名到AppInfo的映射，提高查找效率
                java.util.HashMap<String, AppInfo> packageNameMap = new java.util.HashMap<>();
                for (AppInfo app : installedApps) {
                    if (app != null && app.packageName != null) {
                        packageNameMap.put(app.packageName, app);
                    }
                }

                // 最多加载MAX_APPS个应用
                int maxLoad = Math.min(jsonArray.length(), MAX_APPS);
                for (int i = 0; i < maxLoad; i++) {
                    try {
                        Object element = jsonArray.get(i);
                        if (element instanceof org.json.JSONObject) {
                            // 新格式：JSON对象
                            org.json.JSONObject obj = (org.json.JSONObject) element;
                            int type = obj.optInt("t", AppInfo.TYPE_APP);
                            if (type == AppInfo.TYPE_SHELL) {
                                String cmd = obj.optString("c", "");
                                String label = obj.optString("l", "");
                                if (!cmd.isEmpty()) {
                                    AppInfo shellApp = new AppInfo(label.isEmpty() ? "Shell" : label, cmd);
                                    apps.add(shellApp);
                                }
                            } else {
                                String pkg = obj.optString("p", "");
                                if (!pkg.isEmpty()) {
                                    AppInfo app = packageNameMap.get(pkg);
                                    if (app != null) {
                                        apps.add(app);
                                    }
                                }
                            }
                        } else if (element instanceof String) {
                            // 兼容旧格式：纯字符串包名
                            String packageName = (String) element;
                            AppInfo app = packageNameMap.get(packageName);
                            if (app != null) {
                                apps.add(app);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 确保列表中有 MAX_APPS 个元素，不足的用 null 填充
        while (apps.size() < MAX_APPS) {
            apps.add(null);
        }

        return apps;
    }

    /**
     * 保存延迟时间到 SharedPreferences
     */
    private void saveDelayTime() {
        try {
            String delayStr = editText.getText().toString();
            int delay = Integer.parseInt(delayStr);

            // 验证输入值
            if (delay < 0) {
                // android.util.Log.d("ColorGueture", "延迟时间不能为负数");
                return;
            }

            // 保存到 SharedPreferences
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(PrefKeys.DELAY_TIME.getKey(), delay);
            boolean success = editor.commit();
            if (!success) {
                // android.util.Log.d("ColorGueture", "保存失败");
            }

            // android.util.Log.d("ColorGueture", "保存成功");
        } catch (NumberFormatException e) {
            // android.util.Log.d("ColorGueture", "请输入有效的数字");
        }
    }

    /**
     * RecyclerView 适配器，用于横向显示已选择的应用
     */
    private class AppAdapter extends RecyclerView.Adapter<AppAdapter.AppViewHolder> {
        private final List<AppInfo> apps;

        AppAdapter(List<AppInfo> apps) {
            this.apps = apps;
        }

        @NonNull
        @Override
        public AppViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            // 使用简单的线性布局来显示图标和文字
            LinearLayout layout = new LinearLayout(parent.getContext());
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, // 宽度匹配父容器
                    RecyclerView.LayoutParams.WRAP_CONTENT // 高度自适应
            ));
            layout.setGravity(Gravity.CENTER); // 整个布局居中
            layout.setPadding(20, 20, 20, 20);

            // 创建图标视图
            ImageView imageView = new ImageView(parent.getContext());
            imageView.setLayoutParams(new LinearLayout.LayoutParams(
                    150,
                    150
            ));
            imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE); // 图标居中

            // 创建文字视图
            TextView textView = new TextView(parent.getContext());
            textView.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            textView.setGravity(Gravity.CENTER); // 文字居中
            textView.setMaxWidth(100);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setSingleLine(true);

            // 添加视图到布局
            layout.addView(imageView);
            layout.addView(textView);

            return new AppViewHolder(layout);
        }

        @SuppressLint("NotifyDataSetChanged")
        @Override
        public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
            AppInfo app = apps.get(position);

            if (app != null) {
                // 已选择的应用或 shell 命令
                if (app.type == AppInfo.TYPE_SHELL) {
                    holder.imageView.setImageDrawable(generateShellIcon(holder.itemView.getContext(), app.name));
                    holder.textView.setText(app.name);
                } else {
                    if (app.icon != null) {
                        holder.imageView.setImageDrawable(app.icon);
                    } else {
                        holder.imageView.setImageResource(android.R.drawable.ic_menu_info_details);
                    }
                    holder.textView.setText(app.name);
                }

                if (editMode) {
                    // 编辑模式：晃动动画，长按由 ItemTouchHelper 处理拖拽
                    holder.imageView.clearAnimation();
                    android.view.animation.RotateAnimation shake = new android.view.animation.RotateAnimation(
                            -3f, 3f,
                            android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                            android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f);
                    shake.setDuration(120);
                    shake.setRepeatMode(android.view.animation.Animation.REVERSE);
                    shake.setRepeatCount(android.view.animation.Animation.INFINITE);
                    shake.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
                    holder.imageView.startAnimation(shake);
                    holder.itemView.setOnLongClickListener(null);
                    holder.itemView.setOnClickListener(null);
                } else {
                    // 正常模式：清除晃动，长按删除
                    holder.imageView.clearAnimation();
                    holder.itemView.setOnLongClickListener(v -> {
                        apps.set(position, null);
                        notifyDataSetChanged();
                        saveSelectedApps();
                        return true;
                    });
                    holder.itemView.setOnClickListener(null);
                }
            } else {
                // 空白框
                holder.imageView.setImageResource(android.R.drawable.ic_input_add);
                holder.textView.setText("");

                if (editMode) {
                    // 编辑模式下空槽位不可点击
                    holder.itemView.setOnClickListener(null);
                    holder.itemView.setOnLongClickListener(null);
                } else {
                    // 正常模式：点击选择应用或自定义 Shell 命令
                    holder.itemView.setOnClickListener(v -> showSlotTypeChoiceDialog(position));
                    holder.itemView.setOnLongClickListener(null);
                }
            }
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }

        class AppViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;
            TextView textView;

            AppViewHolder(View itemView) {
                super(itemView);
                // 直接从 LinearLayout 中获取子视图
                if (itemView instanceof LinearLayout) {
                    LinearLayout layout = (LinearLayout) itemView;
                    for (int i = 0; i < layout.getChildCount(); i++) {
                        View child = layout.getChildAt(i);
                        if (child instanceof ImageView) {
                            imageView = (ImageView) child;
                        } else if (child instanceof TextView) {
                            textView = (TextView) child;
                        }
                    }
                }
            }
        }
    }
}