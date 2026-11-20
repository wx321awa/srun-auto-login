package com.xxhh.srun;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** 开机 / 更新后自动把服务拉起来 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Prefs.running(context)) return;
        if (Prefs.get(context, "user", "").isEmpty()) return;
        try {
            Intent s = new Intent(context, LoginService.class);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(s);
            else context.startService(s);
        } catch (Throwable t) {
            // 某些系统不允许开机起前台服务，忽略即可，用户点开 App 会重新拉起
        }
    }
}
