package com.xxhh.srun;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("srun", Context.MODE_PRIVATE);
    }

    static String get(Context c, String k, String def) {
        return sp(c).getString(k, def);
    }

    static boolean running(Context c) {
        return sp(c).getBoolean("running", false);
    }

    static void set(Context c, String k, String v) {
        sp(c).edit().putString(k, v).apply();
    }

    static void setRunning(Context c, boolean v) {
        sp(c).edit().putBoolean("running", v).apply();
    }
}
