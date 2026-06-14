package com.hook.colorgueture;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class OverlayActivity extends AppCompatActivity {

    private static final int DISPLAY_DURATION = 3000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 获取传递的参数
        float x = getIntent().getFloatExtra("x", 0);
        float y = getIntent().getFloatExtra("y", 0);
        String side = getIntent().getStringExtra("side");

        // 创建悬浮窗布局
        LinearLayout layout = createOverlayLayout();

        // 设置窗口参数
        WindowManager.LayoutParams params = getWindowParams(x, y, side);

        // 添加到窗口管理器
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        wm.addView(layout, params);

        // 延迟关闭
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            wm.removeView(layout);
            finish();
        }, DISPLAY_DURATION);
    }

    @SuppressLint("RtlHardcoded")
    private WindowManager.LayoutParams getWindowParams(float x, float y, String side) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
        params.format = android.graphics.PixelFormat.RGBA_8888;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.LEFT | Gravity.TOP;

        // 计算位置
        if ("left".equals(side)) {
            params.x = (int) x + 150;
        } else {
            params.x = (int) x - 400;
        }
        params.y = (int) y - 150;

        return params;
    }

    private LinearLayout createOverlayLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);
        layout.setBackgroundColor(0xCC000000);

        // 加载选择的应用
        List<AppInfo> apps = loadSelectedApps();

        if (apps.isEmpty() || allAppsNull(apps)) {
            TextView textView = new TextView(this);
            textView.setText("未选择应用");
            textView.setTextColor(0xFFFFFFFF);
            layout.addView(textView);
        } else {
            for (AppInfo app : apps) {
                if (app != null) {
                    LinearLayout appItem = new LinearLayout(this);
                    appItem.setOrientation(LinearLayout.HORIZONTAL);
                    appItem.setPadding(10, 10, 10, 10);

                    ImageView iconView = new ImageView(this);
                    iconView.setImageDrawable(app.icon);
                    iconView.setLayoutParams(new LinearLayout.LayoutParams(80, 80));

                    TextView nameView = new TextView(this);
                    nameView.setText(app.name);
                    nameView.setTextColor(0xFFFFFFFF);
                    nameView.setPadding(20, 0, 0, 0);

                    appItem.addView(iconView);
                    appItem.addView(nameView);
                    layout.addView(appItem);
                }
            }
        }

        return layout;
    }

    private List<AppInfo> loadSelectedApps() {
        List<AppInfo> apps = new ArrayList<>();
        SharedPreferences prefs = getSharedPreferences("ColorGueture", MODE_PRIVATE);
        String json = prefs.getString("selected_apps", null);

        if (json != null) {
            try {
                JSONArray jsonArray = new JSONArray(json);
                PackageManager pm = getPackageManager();

                for (int i = 0; i < jsonArray.length(); i++) {
                    String packageName = jsonArray.getString(i);
                    try {
                        ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                        String appName = appInfo.loadLabel(pm).toString();
                        Drawable icon = appInfo.loadIcon(pm);
                        apps.add(new AppInfo(appName, packageName, icon));
                    } catch (PackageManager.NameNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return apps;
    }

    private boolean allAppsNull(List<AppInfo> apps) {
        for (AppInfo app : apps) {
            if (app != null) {
                return false;
            }
        }
        return true;
    }

    private static class AppInfo {
        String name;
        String packageName;
        Drawable icon;

        AppInfo(String name, String packageName, Drawable icon) {
            this.name = name;
            this.packageName = packageName;
            this.icon = icon;
        }
    }
}
