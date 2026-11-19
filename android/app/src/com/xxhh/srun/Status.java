package com.xxhh.srun;

/** 进程内状态，服务写、界面读 */
final class Status {
    static volatile String text = "未启动";
    static volatile boolean online = false;
    static volatile String lastLoginAt = "";
    static volatile String lastError = "";
    static volatile long checkedAt = 0;
}
