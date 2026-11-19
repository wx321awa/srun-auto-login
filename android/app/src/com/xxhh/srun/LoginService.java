package com.xxhh.srun;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 常驻前台服务：每 N 秒探一次在线状态，掉线就自动登录 */
public class LoginService extends Service {
    static final String CH = "srun";
    static final int NID = 1001;
    private volatile boolean running = false;
    private Thread worker;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel c = new NotificationChannel(CH, "校园网状态", NotificationManager.IMPORTANCE_LOW);
            c.setShowBadge(false);
            nm.createNotificationChannel(c);
        }
    }

    private Notification build(String text) {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH)
                : new Notification.Builder(this);
        b.setContentTitle("校园网自登录 · XXHH.").setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setOngoing(true).setContentIntent(pi);
        return b.build();
    }

    private void notify(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        try {
            nm.notify(NID, build(text));
        } catch (Exception ignored) {
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "stop".equals(intent.getAction())) {
            Prefs.setRunning(this, false);
            running = false;
            Status.text = "已停止";
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (Build.VERSION.SDK_INT >= 26) startForeground(NID, build("正在启动…"));
        else startForeground(NID, build("正在启动…"));
        if (!running) {
            running = true;
            Prefs.setRunning(this, true);
            worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    loop();
                }
            }, "srun-worker");
            worker.setDaemon(true);
            worker.start();
        }
        return START_STICKY;
    }

    private void loop() {
        while (running) {
            try {
                String user = Prefs.get(this, "user", "");
                String pass = Prefs.get(this, "pass", "");
                String portal = Prefs.get(this, "portal", "http://10.33.0.2");
                String acId = Prefs.get(this, "acid", "1");
                String check = Prefs.get(this, "check", "");
                String qs = Prefs.get(this, "quiet_start", "00:30");
                String qe = Prefs.get(this, "quiet_end", "06:00");
                long interval = 120;
                try {
                    interval = Long.parseLong(Prefs.get(this, "interval", "120"));
                } catch (Exception ignored) {
                }

                if (Quiet.active(qs, qe)) {
                    long wait = Math.min(Math.max(60, Quiet.secondsUntilEnd(qe)), Math.max(60, interval) * 10);
                    Status.text = "夜间时段 " + Quiet.label(qs, qe) + "，暂停登录";
                    Status.checkedAt = System.currentTimeMillis();
                    notify(Status.text);
                    Thread.sleep(wait * 1000L);
                    continue;
                }

                boolean ok = Srun.online(check);
                Status.checkedAt = System.currentTimeMillis();
                if (ok) {
                    Status.online = true;
                    Status.text = "在线";
                    if (user.isEmpty() || pass.isEmpty()) Status.text = "在线（未填账号，只能看状态）";
                } else if (user.isEmpty() || pass.isEmpty()) {
                    Status.online = false;
                    Status.text = "离线：还没填账号密码";
                } else {
                    Status.text = "离线，正在登录…";
                    Srun.Result r = Srun.doLogin(portal, acId, user, pass);
                    Status.lastError = r.ok ? "" : r.msg;
                    if (r.ok) {
                        Status.online = true;
                        Status.text = "已自动登录";
                        Status.lastLoginAt = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date());
                    } else {
                        Status.online = Srun.online(check);
                        Status.text = "登录失败：" + r.msg;
                    }
                    if ("password".equals(r.kind)) {
                        Status.text = "密码不对，已停手（免得锁号）";
                        notify("密码错误，请检查账号密码");
                        Thread.sleep(600000);
                        continue;
                    }
                }
                notify(Status.text + (Status.lastLoginAt.isEmpty() ? "" : " · 上次 " + Status.lastLoginAt));
                Thread.sleep(Math.max(30, interval) * 1000L);
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                Status.text = "出错：" + t.getClass().getSimpleName();
                Log.w("srun", "loop error", t);
                try {
                    Thread.sleep(30000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
