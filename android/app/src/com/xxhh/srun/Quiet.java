package com.xxhh.srun;

import java.util.Calendar;

/** 夜间时段：这段时间里不去撞认证接口（宿舍半夜断网，登录也没用） */
final class Quiet {
    static int parse(String v) {
        if (v == null) return -1;
        String s = v.trim();
        int i = s.indexOf(':');
        if (i <= 0) return -1;
        try {
            int h = Integer.parseInt(s.substring(0, i).trim());
            int m = Integer.parseInt(s.substring(i + 1).trim());
            if (h < 0 || h > 23 || m < 0 || m > 59) return -1;
            return h * 60 + m;
        } catch (Exception e) {
            return -1;
        }
    }

    static boolean enabled(String start, String end) {
        int a = parse(start), b = parse(end);
        return a >= 0 && b >= 0 && a != b;
    }

    /** 当前是否落在夜间时段 */
    static boolean active(String start, String end) {
        int a = parse(start), b = parse(end);
        if (a < 0 || b < 0 || a == b) return false;
        Calendar c = Calendar.getInstance();
        int cur = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        return a < b ? (cur >= a && cur < b) : (cur >= a || cur < b);
    }

    /** 离解除夜间时段还有多少秒 */
    static long secondsUntilEnd(String end) {
        int b = parse(end);
        if (b < 0) return 0;
        Calendar c = Calendar.getInstance();
        int cur = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        int sec = c.get(Calendar.SECOND);
        return (((b - cur) % 1440 + 1440) % 1440) * 60L - sec;
    }

    static String label(String start, String end) {
        return start + "-" + end;
    }
}
