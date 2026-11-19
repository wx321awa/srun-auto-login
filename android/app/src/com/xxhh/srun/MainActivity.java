package com.xxhh.srun;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private EditText etUser, etPass, etPortal, etAcid, etInterval, etQuietStart, etQuietEnd;
    private TextView tvStatus;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView title = new TextView(this);
        title.setText("校园网自登录 · XXHH.");
        title.setTextSize(20);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title);

        etUser = input("账号", InputType.TYPE_CLASS_TEXT);
        etPass = input("密码", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etPortal = input("门户地址", InputType.TYPE_CLASS_TEXT);
        etAcid = input("ac_id", InputType.TYPE_CLASS_NUMBER);
        etInterval = input("检查间隔（秒）", InputType.TYPE_CLASS_NUMBER);
        etQuietStart = input("00:30", InputType.TYPE_CLASS_DATETIME);
        etQuietEnd = input("06:00", InputType.TYPE_CLASS_DATETIME);
        root.addView(label("账号"));
        root.addView(etUser);
        root.addView(label("密码"));
        root.addView(etPass);
        root.addView(label("门户地址"));
        root.addView(etPortal);
        root.addView(label("ac_id"));
        root.addView(etAcid);
        root.addView(label("检查间隔（秒）"));
        root.addView(etInterval);
        root.addView(label("夜间不登录：开始时间（HH:MM，起止相同＝关闭）"));
        root.addView(etQuietStart);
        root.addView(label("夜间不登录：结束时间（HH:MM）"));
        root.addView(etQuietEnd);

        Context c = this;
        etUser.setText(Prefs.get(c, "user", ""));
        etPass.setText(Prefs.get(c, "pass", ""));
        etPortal.setText(Prefs.get(c, "portal", "http://10.33.0.2"));
        etAcid.setText(Prefs.get(c, "acid", "1"));
        etInterval.setText(Prefs.get(c, "interval", "120"));
        etQuietStart.setText(Prefs.get(c, "quiet_start", "00:30"));
        etQuietEnd.setText(Prefs.get(c, "quiet_end", "06:00"));

        Button start = button("保存并开始");
        Button once = button("立即登录一次");
        Button stop = button("停止");
        Button batt = button("关闭电池优化（重要）");
        root.addView(start);
        root.addView(once);
        root.addView(stop);
        root.addView(batt);

        tvStatus = new TextView(this);
        tvStatus.setTextSize(14);
        tvStatus.setPadding(0, dp(12), 0, 0);
        root.addView(tvStatus);

        TextView tip = new TextView(this);
        tip.setText("提示：这个 App 必须装在那台连宿舍 WiFi 的手机上；"
                + "它每 N 秒查一次网络，掉线后自动登录；夜间时段只待命不重试。"
                + "别在最近任务里划掉它。");
        tip.setTextSize(12);
        tip.setPadding(0, dp(12), 0, 0);
        root.addView(tip);

        TextView sign = new TextView(this);
        sign.setText("作者：XXHH.  ·  v1.2  ·  github.com/wx321awa");
        sign.setTextSize(12);
        sign.setPadding(0, dp(16), 0, dp(4));
        root.addView(sign);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);

        start.setOnClickListener(v -> saveAndStart());
        once.setOnClickListener(v -> loginOnce());
        stop.setOnClickListener(v -> {
            Prefs.setRunning(this, false);
            stopService(new Intent(this, LoginService.class));
            Status.text = "已停止";
            Toast.makeText(this, "已停止", Toast.LENGTH_SHORT).show();
        });
        batt.setOnClickListener(v -> requestIgnoreBattery());

        askNotification();
        ui.post(tick);
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(12);
        t.setPadding(0, dp(10), 0, 0);
        return t;
    }

    private EditText input(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(true);
        e.setTextSize(15);
        e.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    private void save() {
        Prefs.set(this, "user", etUser.getText().toString().trim());
        Prefs.set(this, "pass", etPass.getText().toString());
        Prefs.set(this, "portal", etPortal.getText().toString().trim());
        Prefs.set(this, "acid", etAcid.getText().toString().trim().isEmpty() ? "1" : etAcid.getText().toString().trim());
        Prefs.set(this, "interval", etInterval.getText().toString().trim().isEmpty() ? "120" : etInterval.getText().toString().trim());
        String qs = etQuietStart.getText().toString().trim();
        String qe = etQuietEnd.getText().toString().trim();
        if (qs.isEmpty()) qs = "00:30";
        if (qe.isEmpty()) qe = "06:00";
        Prefs.set(this, "quiet_start", qs);
        Prefs.set(this, "quiet_end", qe);
    }

    private void saveAndStart() {
        save();
        if (etUser.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "先填账号", Toast.LENGTH_SHORT).show();
            return;
        }
        Prefs.setRunning(this, true);
        Intent i = new Intent(this, LoginService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
        Toast.makeText(this, "已开始后台自动登录", Toast.LENGTH_SHORT).show();
    }

    private void loginOnce() {
        save();
        final String u = etUser.getText().toString().trim();
        final String p = etPass.getText().toString();
        final String portal = etPortal.getText().toString().trim();
        final String ac = etAcid.getText().toString().trim();
        final String qs0 = Prefs.get(this, "quiet_start", "00:30");
        final String qe0 = Prefs.get(this, "quiet_end", "06:00");
        if (Quiet.active(qs0, qe0)) {
            Toast.makeText(this, "现在是夜间时段 " + Quiet.label(qs0, qe0)
                    + "，认证服务一般不响应，本次仍会试一次", Toast.LENGTH_LONG).show();
        }
        Status.text = "正在手动登录…";
        new Thread(() -> {
            Srun.Result r = Srun.doLogin(portal, ac.isEmpty() ? "1" : ac, u, p);
            Status.lastError = r.ok ? "" : r.msg;
            if (r.ok) {
                Status.text = "手动登录成功";
                Status.lastLoginAt = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date());
            } else if (Quiet.active(qs0, qe0)) {
                Status.text = "夜间时段 " + Quiet.label(qs0, qe0) + " 认证不响应，06:00 后自动恢复";
            } else {
                Status.text = "手动登录失败：" + r.msg;
            }
        }, "manual-login").start();
    }

    private void requestIgnoreBattery() {
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
                    Toast.makeText(this, "已经在白名单里了", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
            }
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) {
            }
        }
    }

    private void askNotification() {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
                    requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            String s = Status.text;
            if (Status.checkedAt > 0) {
                s += " · " + new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date(Status.checkedAt));
            }
            if (!Status.lastLoginAt.isEmpty()) s += "\n上次登录：" + Status.lastLoginAt;
            if (!Status.lastError.isEmpty()) s += "\n最近错误：" + Status.lastError;
            s += Prefs.running(MainActivity.this) ? "\n后台：运行中" : "\n后台：未启动";
            tvStatus.setText(s);
            tvStatus.setGravity(Gravity.START);
            ui.postDelayed(this, 1000);
        }
    };
}
