# 校园网（深澜 SRun）自动登录

掉线后自动重新登录 `http://10.33.0.2/srun_portal_pc?ac_id=1&theme=pro`，每天凌晨断网后不用再手动点。

## 1. 先填账号密码

打开 `config.json`，把 `username` / `password` 换成你的学号密码（`portal` 和 `ac_id` 一般不用动）：

```json
{
  "portal": "http://10.33.0.2",
  "ac_id": "1",
  "username": "你的账号",
  "password": "你的密码"
}
```

## 2. 先手动跑一次看通不通

```
python srun_login.py --probe    # 看它识别到哪套接口、本机 IP 是多少
python srun_login.py            # 真登录一次
```

成功了会打印 `接口 A 登录成功` 并复查网络「在线」。
失败就加 `--debug` 再跑一次，把输出发给作者，一次就能定位。

## 3. 让它每天自动跑

### A. Windows 电脑

双击 `install_task.bat` → 装好一个叫 `SRunAutoLogin` 的计划任务，**每 5 分钟**检查一次网络，掉线就自动登录（在线时脚本 1 秒内退出，不占资源）。

卸载：`schtasks /delete /tn "SRunAutoLogin" /f`

需要 Python 3（`pythonw` 或 `pyw` 在 PATH 里）。

### B. 手机 / Linux

```bash
nohup python3 srun_login.py --watch 120 > /dev/null 2>&1 &
```

每 120 秒检查一次，掉线自动登录。

### B2. 安卓另一台手机（Termux，不用 root）

```bash
pkg install python -y
mkdir -p ~/srun && cp config.json srun_login.py ~/srun/
cd ~/srun && nano config.json          # 填 username / password
python3 srun_login.py --probe          # 连上校园 WiFi 后先探测
python3 srun_login.py --force          # 真登录一次
```

常驻 + 开机自启（Termux 会自己拉起 boot 脚本）：

```bash
termux-wake-lock                      # 防后台被清理
mkdir -p ~/.termux/boot
cat > ~/.termux/boot/srun.sh <<'EOF'
#!/data/data/com.termux/files/usr/bin/sh
termux-wake-lock
cd ~/srun && python3 srun_login.py --watch 120 >> srun_login.log 2>&1 &
EOF
chmod +x ~/.termux/boot/srun.sh
# 还需要装 Termux:Boot 这个附加 App，开机才会跑 boot 脚本
```

- 手机 Only：脚本纯标准库，Termux、Debian、Ubuntu 都能跑，不需要装别的包。
- Termux 里没有 `HTTP_PROXY` 之类的变量，所以不会像 DSH 环境那样被代理拦成 403。

### C. 只想「打开网页就自动填好」

把 `auto_login.user.js` 装进手机/电脑浏览器（Tampermonkey），打开认证页会自动填账号密码并点登录——不用算加密，页面怎么改都不影响。

## 参数速查

| 命令 | 作用 |
|---|---|
| `python3 srun_login.py` | 掉线才登录（默认） |
| `--force` | 不管在不在线都登录一次 |
| `--status` | 只看在线/离线 |
| `--probe` | 探测门户接口类型 |
| `--watch 120` | 守护模式，每 120 秒检查 |
| `--debug` | 打印原始响应，排查用 |
| `--selftest` | 加密算法自检 |

日志在同目录 `srun_login.log`。

## 原理（万一以后门户改版）

脚本走深澜原生接口 `get_challenge → srun_portal`，自己算加密：

- `info = {SRBX1} + 自定义base64(xencode(json, token))`
- `password = {MD5} + HMAC-MD5(key=token, msg=密码)`
- `chksum = sha1(token+账号 + token+md5 + token+ac_id + token+ip + token+n + token+type + token+info)`

新版门户（`{SRunPortalXEncode}`）和老表单版（`/srun_portal_pc`）也都实现了，会自动依次尝试。加密实现已对照开源参考实现逐位校验（`--selftest` 全绿）。
---

## C. 安卓 APK（推荐给备用机用这个）

产物：`/sdcard/Download/DSH-Go/校园网自登录-v1.0.apk`（约 25 KB，包名 `com.xxhh.srun`）

在 VPS（Windows Server，`C:\srun-app`）上用 aapt2 + javac + R8/D8 + apksigner 手工构建，
不依赖 Gradle / Android Studio。工程源码就在本目录 `android/` 下。

### 安装与首次使用
1. 把 APK 装到**那台连宿舍 WiFi 的备用机**上（微信/QQ 传过去，或文件管理器打开）。
2. 允许「安装未知来源应用」。
3. 打开 App，填：账号、密码、门户地址（默认 `http://10.33.0.2`）、ac_id（默认 1）、检查间隔（默认 120 秒）。
4. 点「保存并开始」→ 同意通知权限 → 点「关闭电池优化（重要）」并在系统弹窗里选「允许」。
5. 之后它会常驻一个前台通知，显示「在线 / 已自动登录 / 登录失败：原因」。

### 它做什么
- 每 120 秒访问一次 `generate_204` / `connecttest.txt` 判断有没有网；
- 断网（凌晨断网后）就自动跑深澜握手：`get_challenge` → 加密 → `srun_portal`，接口变体 A/B/C 自动依次尝试；
- 密码错误会**立刻停手并等 10 分钟**，避免试到锁号；
- 开机自启（BootReceiver），被杀掉后重开 App 会自动续上。

### 排错
| 现象 | 处理 |
| --- | --- |
| 通知一直是「离线，正在登录…」失败 | 点开 App 用「立即登录一次」看具体报错；`拿不到本机 IP` ＝ 没连校园 WiFi |
| 提示「密码不对，已停手」 | 账号密码错了，重新填一次再保存 |
| 提示需要验证码（E2806） | 这就要手动在那台手机上打开门户输一次 |
| 重启后没自动跑 | 系统的「自启动管理」里给这个 App 开白名单 |

### 构建命令（VPS 上）
```powershell
powershell -NoProfile -File C:\srun-app\build.ps1   # 产出 C:\srun-app\srun-login.apk
```
依赖：`C:\android-sdk`（build-tools 34.0.0 + platforms/android-33）、JDK 21（`C:\Java\jdk-21.0.13+9`）、
R8 8.9.35（`C:\r8\r8.jar`）。注意 build-tools 自带的旧 d8 8.2.2 带 `--lib` 时会 NPE，所以改用新版 R8 的 D8。

---

## D. 夜间时段（默认 00:30 – 06:00 不登录）

宿舍夜里断网本来就连不上认证服务器，硬试只会刷日志、耗电，所以默认**凌晨 00:30 到早上 06:00 完全不发起登录**，
06:00 一到自动开始探测并登录。

可以改成任何时段，给别的学校的人用也很方便：

```jsonc
// config.json（Python 版）
"quiet_start": "00:30",   // 开始时间 HH:MM
"quiet_end":   "06:00"    // 结束时间 HH:MM
```

- **起止时间写一样 = 关闭该功能**（比如两个都写 `06:00`，就全天不限时段）
- 支持跨零点，比如 `23:30` → `07:00` 也能正确识别
- APK 里也是同样两个输入框（`夜间不登录：开始时间 / 结束时间`），默认 `00:30 / 06:00`
- 手动登录（`--force`）同样遵守夜间时段，确需强行登录加 `--ignore-quiet`
- 夜间时段通知/状态会显示「夜间时段 00:30-06:00，暂停登录」，到点自动恢复

### 分享给别人（其他学校）
别人用只要改三处：
1. `portal`：自己学校的认证地址（例如 `http://192.168.1.1` 之类）
2. `ac_id`：门户 URL 里的 `ac_id=` 值
3. `quiet_start / quiet_end`：自己宿舍的断网时段

接口变体（`{SRBX1}` / `{SRunPortalXEncode}` / 老式表单）脚本会自动识别并依次尝试，不用手改。
