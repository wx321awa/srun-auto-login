#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
深澜(SRun)校园网自动登录 —— 单文件，只用 Python3 标准库，不需要 pip 装任何东西。

用法：
  python3 srun_login.py                # 检查网络，掉线才登录（默认）
  python3 srun_login.py --force        # 不管在不在线，强制登录一次
  python3 srun_login.py --status       # 只查在线状态
  python3 srun_login.py --watch 120    # 守护模式：每 120 秒查一次，掉线自动登录
  python3 srun_login.py --probe        # 只探测门户用的是哪一套接口
  python3 srun_login.py --debug        # 打印每一跳原始响应（排查用）
  python3 srun_login.py --selftest     # 自检验证加密实现（对照参考实现）

配置 config.json（与脚本同目录）：
  {
    "portal":   "http://10.33.0.2",
    "ac_id":    "1",
    "username": "你的账号",
    "password": "你的密码",
    "online_check": "http://connect.rom.miui.com/generate_204"
  }

接口说明（脚本会自动判断用哪套）：
  A. srun_bx1 经典版：/cgi-bin/get_challenge → /cgi-bin/srun_portal
     info = {SRBX1} + 自定义base64(xencode(json, token))
     password = {MD5} + HMAC-MD5(key=token, msg=password)
     chksum = sha1(token+用户名 + token+md5 + token+ac_id + token+ip
                   + token+n + token+type + token+info)
  B. 新版/{SRunPortalXEncode}：password = md5(password+token)，chksum 无分隔符
  C. 更老的版本：直接表单 POST /srun_portal_pc
"""

import argparse
import base64
import hashlib
import hmac
import json
import math
import os
import re
import socket
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
CONFIG_PATH = os.path.join(HERE, "config.json")
LOG_PATH = os.path.join(HERE, "srun_login.log")

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")

DEFAULT_CONFIG = {
    "portal": "http://10.33.0.2",
    "ac_id": "1",
    "username": "",
    "password": "",
    "online_check": "http://connect.rom.miui.com/generate_204",
    # 夜间不自动登录的时段（本机时间）；起止时间相同 = 关闭这个功能
    "quiet_start": "00:30",
    "quiet_end": "06:00",
}

# 绕开系统代理：手机上如果设了 HTTP_PROXY，会让校园网请求被代理拦掉
_OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


# ==========================================================================
# 日志
# ==========================================================================
def log(msg, echo=True):
    line = "%s %s" % (time.strftime("%Y-%m-%d %H:%M:%S"), msg)
    if echo:
        print(line, flush=True)
    try:
        if os.path.exists(LOG_PATH) and os.path.getsize(LOG_PATH) > 2 * 1024 * 1024:
            with open(LOG_PATH, "r", encoding="utf-8", errors="replace") as f:
                keep = f.read()[-512 * 1024:]
            with open(LOG_PATH, "w", encoding="utf-8") as f:
                f.write(keep)
        with open(LOG_PATH, "a", encoding="utf-8") as f:
            f.write(line + "\n")
    except Exception:
        pass


# ==========================================================================
# HTTP
# ==========================================================================
def http_get(url, timeout=8, headers=None):
    h = {"User-Agent": UA}
    if headers:
        h.update(headers)
    return _OPENER.open(urllib.request.Request(url, headers=h), timeout=timeout).read().decode("utf-8", "replace")


def http_post(url, data, timeout=8):
    if isinstance(data, dict):
        data = urllib.parse.urlencode(data).encode()
    req = urllib.request.Request(url, data=data, headers={
        "User-Agent": UA,
        "Content-Type": "application/x-www-form-urlencoded",
        "X-Requested-With": "XMLHttpRequest",
    })
    return _OPENER.open(req, timeout=timeout).read().decode("utf-8", "replace")


# ==========================================================================
# 深澜加密三件套：xencode / 自定义 base64 / HMAC-MD5
# ==========================================================================
_ALPHA = "LVoJPiCN2R8G90yg+hmFHuacZ1OWMnrsSTXkYpUq/3dlbfKwv6xztjI7DeBE45QA"
_M32 = 0xFFFFFFFF


def srun_base64(s):
    """深澜自己的 base64：字母表跟标准 base64 不一样，直接用标准库会失败"""
    if not s:
        return ""
    out = []
    imax = len(s) - len(s) % 3
    for i in range(0, imax, 3):
        b = (ord(s[i]) << 16) | (ord(s[i + 1]) << 8) | ord(s[i + 2])
        out.append(_ALPHA[b >> 18])
        out.append(_ALPHA[(b >> 12) & 63])
        out.append(_ALPHA[(b >> 6) & 63])
        out.append(_ALPHA[b & 63])
    rest = len(s) - imax
    if rest == 1:
        b = ord(s[imax]) << 16
        out.append(_ALPHA[b >> 18] + _ALPHA[(b >> 12) & 63] + "==")
    elif rest == 2:
        b = (ord(s[imax]) << 16) | (ord(s[imax + 1]) << 8)
        out.append(_ALPHA[b >> 18] + _ALPHA[(b >> 12) & 63] + _ALPHA[(b >> 6) & 63] + "=")
    return "".join(out)


def _ordat(s, i):
    return ord(s[i]) if i < len(s) else 0


def _sencode(s, with_len):
    out = []
    for i in range(0, len(s), 4):
        out.append(_ordat(s, i) | (_ordat(s, i + 1) << 8)
                   | (_ordat(s, i + 2) << 16) | (_ordat(s, i + 3) << 24))
    if with_len:
        out.append(len(s))
    return out


def _lencode(arr):
    buf = []
    for v in arr:
        v &= _M32
        buf.append(chr(v & 0xFF))
        buf.append(chr((v >> 8) & 0xFF))
        buf.append(chr((v >> 16) & 0xFF))
        buf.append(chr((v >> 24) & 0xFF))
    return "".join(buf)


def xencode(msg, key):
    """深澜的 XXTEA 变体（轮数 6+52/(n+1)），和网页里 xEncode 等价"""
    if not msg:
        return ""
    v = _sencode(msg, True)
    k = _sencode(key, False)
    while len(k) < 4:
        k.append(0)
    n = len(v) - 1
    z, y = v[n], v[0]
    c = 0x9E3779B9
    d = 0
    q = 6 + 52 // (n + 1)
    while q > 0:
        q -= 1
        d = (d + c) & _M32
        e = (d >> 2) & 3
        p = 0
        while p < n:
            y = v[p + 1]
            m = (z >> 5) ^ (y << 2)
            m += ((y >> 3) ^ (z << 4)) ^ (d ^ y)
            m += k[(p & 3) ^ e] ^ z
            v[p] = (v[p] + m) & _M32
            z = v[p]
            p += 1
        y = v[0]
        m = (z >> 5) ^ (y << 2)
        m += ((y >> 3) ^ (z << 4)) ^ (d ^ y)
        m += k[(p & 3) ^ e] ^ z
        v[n] = (v[n] + m) & _M32
        z = v[n]
    return _lencode(v)


def hmac_md5(key, msg):
    return hmac.new(key.encode("utf-8"), msg.encode("utf-8"), hashlib.md5).hexdigest()


def md5hex(s):
    return hashlib.md5(s.encode("utf-8")).hexdigest()


def sha1hex(s):
    return hashlib.sha1(s.encode("utf-8")).hexdigest()


def std_base64(s):
    return base64.b64encode(s.encode("utf-8")).decode()


# ==========================================================================
# 环境探测
# ==========================================================================
def portal_host(url):
    return urllib.parse.urlparse(url).hostname or "10.33.0.2"


def local_ip_toward(host, port=80):
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect((host, port))
        return s.getsockname()[0]
    except Exception:
        return ""
    finally:
        s.close()


def is_online(check_url, timeout=5):
    """拿到干净的 204 = 已在线；被劫持成登录页 = 离线"""
    for url in [check_url,
                "http://connect.rom.miui.com/generate_204",
                "http://www.msftconnecttest.com/connecttest.txt"]:
        try:
            r = _OPENER.open(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=timeout)
            body = r.read().decode("utf-8", "replace")
            if r.status == 204 or "Microsoft Connect Test" in body:
                return True
            if "<html" in body.lower():
                return False          # 门户劫持
            return True
        except urllib.error.HTTPError as e:
            if e.code == 204:
                return True
            if e.code in (301, 302, 307, 308, 401, 403):
                return False
        except Exception:
            continue
    return False


def fetch_portal_page(portal, ac_id, debug=False):
    url = "%s/srun_portal_pc?ac_id=%s&theme=pro" % (portal, ac_id)
    try:
        html = http_get(url)
    except Exception as e:
        if debug:
            print("[debug] 拉门户页面失败:", type(e).__name__, e)
        return "", url

    if debug:
        print("[debug] 门户页面 %d 字节" % len(html))
    return html, url


def parse_ip_from_page(html, portal):
    """老版页面 `<input id="user_ip" value="10.x.x.x">`；新版页面写在 JS 变量里"""
    m = re.search(r"""user_ip[^0-9]{0,40}?(\d{1,3}(?:\.\d{1,3}){3})""", html, re.I)
    if m:
        return m.group(1)
    m = re.search(r"""(?:online_ip|client_ip|["']ip["'])\s*[=:]\s*["'](\d{1,3}(?:\.\d{1,3}){3})["']""", html)
    if m:
        return m.group(1)
    return local_ip_toward(portal_host(portal))


def detect_variant(html, portal, debug=False):
    """从门户页面（含内联 js）判断用哪套接口；判断不出来就返回 auto"""
    text = html
    if "{SRBX1}" not in text and "{SRunPortalXEncode}" not in text and "get_challenge" not in text:
        for src in re.findall(r"""<script[^>]+src=["']([^"']+)["']""", html)[:4]:
            if src.startswith("//"):
                src = "http:" + src
            elif src.startswith("/"):
                src = portal.rstrip("/") + src
            elif not src.startswith("http"):
                continue
            try:
                text += http_get(src, timeout=6)
            except Exception:
                pass
        if debug:
            print("[debug] 扫描 js 完成，共 %d 字节" % len(text))

    if "{SRunPortalXEncode}" in text:
        return "B"
    if "{SRBX1}" in text:
        return "A"
    return "auto"


# ==========================================================================
# 认证流程
# ==========================================================================
ERROR_CODES = {
    "E2901": "账号或密码不对",
    "E2553": "账号密码不对 / 账号被停用",
    "E2620": "已在线，无需重复登录",
    "E2806": "需要验证码",
    "E6518": "参数错误（多半是加密串不对）",
    "E6306": "接口地址不对",
    "E2616": "账户已停用",
}


def parse_result(raw):
    """返回 (是否成功, 说明, 失败类别)"""
    if any(k in raw for k in ('"error":"ok"', '"result":"ok"', '"res":"ok"', "login_ok")) \
            or re.search(r'"suc_msg"\s*:\s*"[^"]+"', raw):
        return True, raw.strip()[:300], ""
    for code, meaning in ERROR_CODES.items():
        if code in raw:
            if code == "E2620":
                return True, "%s %s" % (code, meaning), ""
            kind = "password" if "password" in meaning or code in ("E2901", "E2553") else "param"
            return False, "%s %s" % (code, meaning), kind
    return False, raw.strip()[:300], "unknown"


def get_challenge(portal, username, ip, debug=False):
    url = "%s/cgi-bin/get_challenge?callback=jsonp_%d&username=%s&ip=%s&_=%d" % (
        portal, int(time.time() * 1000) % 100000,
        urllib.parse.quote(username), urllib.parse.quote(ip),
        int(time.time() * 1000))
    raw = http_get(url)
    if debug:
        print("[debug] get_challenge ->", raw[:300])
    m = re.search(r'"challenge"\s*:\s*"([^"]*)"', raw) or re.search(r'"token"\s*:\s*"([^"]*)"', raw)
    if not m:
        return None
    if '"res":"fail"' in raw.replace(" ", "") or '"error":"' in raw.replace(" ", "") and '"res":"ok"' not in raw:
        if debug:
            print("[debug] challenge 返回异常")
    return m.group(1)


def login_variant_A(portal, ac_id, username, password, ip, token, debug=False):
    """经典 srun_bx1"""
    info_json = json.dumps({"username": username, "password": password, "ip": ip,
                            "acid": str(ac_id), "enc_ver": "srun_bx1"},
                           separators=(",", ":"), ensure_ascii=False)
    info = "{SRBX1}" + srun_base64(xencode(info_json, token))
    hmd5 = hmac_md5(token, password)
    n, typ = "200", "1"
    chksum = sha1hex("%s%s%s%s%s%s%s%s%s%s%s%s%s%s" % (
        token, username, token, hmd5, token, ac_id, token, ip,
        token, n, token, typ, token, info))
    params = {
        "callback": "jsonp_%d" % (int(time.time() * 1000) % 100000),
        "action": "login",
        "username": username,
        "password": "{MD5}" + hmd5,
        "ac_id": ac_id,
        "ip": ip,
        "info": info,
        "chksum": chksum,
        "n": n,
        "type": typ,
        "os": "Windows 10",
        "name": "Windows",
        "double_stack": "0",
        "_": str(int(time.time() * 1000)),
    }
    return http_get("%s/cgi-bin/srun_portal?%s" % (portal, urllib.parse.urlencode(params)))


def login_variant_B(portal, ac_id, username, password, ip, token, debug=False):
    """新版 {SRunPortalXEncode}"""
    info_json = json.dumps({"username": username, "password": password, "ip": ip,
                            "acid": str(ac_id), "enc_ver": "srun_bx1"},
                           separators=(",", ":"), ensure_ascii=False)
    info = "{SRunPortalXEncode}" + std_base64(xencode(info_json, token))
    hmd5 = md5hex(password + token)
    n, typ = "200", "1"
    chksum = sha1hex(hmd5 + token + ac_id + ip + n + typ + info)
    params = {
        "callback": "jsonp_%d" % (int(time.time() * 1000) % 100000),
        "action": "login",
        "username": username,
        "password": "{MD5}" + hmd5,
        "ac_id": ac_id,
        "ip": ip,
        "info": info,
        "chksum": chksum,
        "n": n,
        "type": typ,
        "os": "Windows 10",
        "name": "Windows",
        "double_stack": "0",
        "_": str(int(time.time() * 1000)),
    }
    return http_get("%s/cgi-bin/srun_portal?%s" % (portal, urllib.parse.urlencode(params)))


def login_legacy(portal, ac_id, username, password, ip, debug=False):
    """1.x~3.x 老门户：表单提交"""
    data = {
        "action": "login",
        "username": username,
        "password": "{MD5}" + md5hex(password),
        "ac_id": ac_id,
        "user_ip": ip,
        "nas_ip": "",
        "user_mac": "",
        "save_me": "1",
        "ajax": "1",
        "double_stack": "0",
    }
    return http_post("%s/srun_portal_pc?ac_id=%s&theme=pro" % (portal, ac_id), data)


def do_login(cfg, debug=False, forced_variant=None):
    portal = cfg["portal"].rstrip("/")
    ac_id = str(cfg.get("ac_id", "1"))
    username = (cfg.get("username") or "").strip()
    password = cfg.get("password") or ""

    if not username or not password or username == "你的账号":
        log("config.json 里的 username / password 还没填。")
        return False

    html, _ = fetch_portal_page(portal, ac_id, debug)
    ip = parse_ip_from_page(html, portal)
    variant = forced_variant or detect_variant(html, portal, debug)
    log("目标 portal=%s ac_id=%s ip=%s 账号=%s 接口=%s" % (portal, ac_id, ip, username, variant))

    order = ["A", "B"] if variant == "auto" else [variant]
    if "C" not in order:
        order.append("C")

    t0 = time.time()
    try:
        token = get_challenge(portal, username, ip, debug)
    except Exception as e:
        token = None
        if debug:
            print("[debug] get_challenge 异常:", type(e).__name__, e)

    tried = []
    for v in order:
        try:
            if v in ("A", "B"):
                if not token:
                    continue
                fn = login_variant_A if v == "A" else login_variant_B
                raw = fn(portal, ac_id, username, password, ip, token, debug)
            else:
                raw = login_legacy(portal, ac_id, username, password, ip)
        except Exception as e:
            log("接口 %s 请求异常：%s: %s" % (v, type(e).__name__, e))
            tried.append((v, "异常"))
            continue

        if debug:
            print("[debug] 接口 %s 返回 -> %s" % (v, raw[:400]))

        ok, msg, kind = parse_result(raw)
        tried.append((v, msg))
        if ok:
            log("接口 %s 登录成功（耗时 %.1fs）：%s" % (v, time.time() - t0, msg))
            return True
        log("接口 %s 失败：%s" % (v, msg))
        if kind == "password":
            log("看起来是账号密码问题，不再尝试其它接口，免得把账号试锁。")
            break
        if kind == "param" and v == "A" and "B" in order:
            log("参数类错误，继续试新版接口…")

    log("全部接口都失败：%s" % tried)
    return False


# ==========================================================================
# 自检：拿参考实现（北京理工 coffeehat 版）逐位比对
# ==========================================================================
_REF_XENCODE = '''
def _r_ordat(msg, idx):
    return ord(msg[idx]) if len(msg) > idx else 0
def _r_sencode(msg, key):
    pwd = []
    for i in range(0, len(msg), 4):
        pwd.append(_r_ordat(msg, i) | _r_ordat(msg, i+1) << 8
                   | _r_ordat(msg, i+2) << 16 | _r_ordat(msg, i+3) << 24)
    if key:
        pwd.append(len(msg))
    return pwd
def _r_lencode(msg, key):
    l = len(msg); ll = (l - 1) << 2
    if key:
        m = msg[l-1]
        if m < ll - 3 or m > ll:
            return
        ll = m
    for i in range(0, l):
        msg[i] = chr(msg[i] & 0xff) + chr(msg[i] >> 8 & 0xff) + chr(msg[i] >> 16 & 0xff) + chr(msg[i] >> 24 & 0xff)
    if key:
        return "".join(msg)[0:ll]
    return "".join(msg)
def _r_xencode(msg, key):
    if msg == "":
        return ""
    pwd = _r_sencode(msg, True); pwdk = _r_sencode(key, False)
    if len(pwdk) < 4:
        pwdk = pwdk + [0] * (4 - len(pwdk))
    n = len(pwd) - 1; z = pwd[n]; y = pwd[0]
    c = 0x86014019 | 0x183639A0; m = 0; e = 0; p = 0
    q = __import__("math").floor(6 + 52 / (n + 1)); d = 0
    while 0 < q:
        d = d + c & (0x8CE0D9BF | 0x731F2640)
        e = d >> 2 & 3; p = 0
        while p < n:
            y = pwd[p + 1]
            m = z >> 5 ^ y << 2
            m = m + ((y >> 3 ^ z << 4) ^ (d ^ y))
            m = m + (pwdk[(p & 3) ^ e] ^ z)
            pwd[p] = pwd[p] + m & (0xEFB8D130 | 0x10472ECF)
            z = pwd[p]; p = p + 1
        y = pwd[0]
        m = z >> 5 ^ y << 2
        m = m + ((y >> 3 ^ z << 4) ^ (d ^ y))
        m = m + (pwdk[(p & 3) ^ e] ^ z)
        pwd[n] = pwd[n] + m & (0xBB390742 | 0x44C6F8BD)
        z = pwd[n]; q = q - 1
    return _r_lencode(pwd, False)
'''


def selftest():
    ns = {}
    exec(_REF_XENCODE, ns)
    ref = ns["_r_xencode"]

    import random
    random.seed(20241120)
    bad = 0
    cases = [
        '{"username":"20240001","password":"test_pass_2024","ip":"10.0.0.1","acid":"1","enc_ver":"srun_bx1"}',
        '{"username":"a","password":"b","ip":"10.0.0.1","acid":"1","enc_ver":"srun_bx1"}',
        "中文测试", "", "x" * 200,
    ]
    for _ in range(12):
        n = random.randint(0, 80)
        cases.append("".join(chr(random.randint(32, 126)) for _ in range(n)))
    keys = ["e6843f26b8544327a3a25978dd3c5f89e6b745df1732993b88fe082c13a34cb9", "abc123"]

    for c in cases:
        for k in keys:
            mine, theirs = xencode(c, k), ref(c, k)
            if mine != theirs:
                bad += 1
                print("MISMATCH len=%d key=%s" % (len(c), k[:8]))

    # base64 交叉验证（用参考字母表反推）
    import string
    alpha = "LVoJPiCN2R8G90yg+hmFHuacZ1OWMnrsSTXkYpUq/3dlbfKwv6xztjI7DeBE45QA"
    std = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    for s in ["132456", "hello world", "\x00\x01\x02"]:
        b = srun_base64(s).rstrip("=")
        idx = [alpha.index(ch) for ch in b]
        back = "".join(std[i] for i in idx)
        if base64.b64decode(back + "=" * (-len(back) % 4)) != s.encode("utf-8", "replace"):
            bad += 1
            print("BASE64 MISMATCH", repr(s))

    print("自检：%s（%d 组 xencode 对照 + base64 反向解码）"
          % ("全部一致" if bad == 0 else "%d 项不一致" % bad, len(cases) * len(keys)))
    return 0 if bad == 0 else 1


# ==========================================================================
# 主入口
# ==========================================================================
# ==========================================================================
# 夜间时段（宿舍/学校夜里断网时别去撞认证接口）
# ==========================================================================
def parse_hhmm(v):
    try:
        h, m = str(v).strip().split(":")
        h, m = int(h), int(m)
        if 0 <= h <= 23 and 0 <= m <= 59:
            return h * 60 + m
    except Exception:
        pass
    return None


def quiet_window(cfg):
    """返回 (start_min, end_min)；功能关闭时返回 None"""
    a = parse_hhmm(cfg.get("quiet_start"))
    b = parse_hhmm(cfg.get("quiet_end"))
    if a is None or b is None or a == b:
        return None
    return a, b


def in_quiet(cfg, now=None):
    win = quiet_window(cfg)
    if not win:
        return False
    a, b = win
    t = now or time.localtime()
    cur = t.tm_hour * 60 + t.tm_min
    return (a <= cur < b) if a < b else (cur >= a or cur < b)


def seconds_until_quiet_end(cfg):
    win = quiet_window(cfg)
    if not win:
        return 0
    a, b = win
    t = time.localtime()
    cur = t.tm_hour * 60 + t.tm_min
    return ((b - cur) % 1440) * 60 - t.tm_sec


def load_config():
    cfg = dict(DEFAULT_CONFIG)
    if os.path.exists(CONFIG_PATH):
        try:
            with open(CONFIG_PATH, "r", encoding="utf-8") as f:
                cfg.update(json.load(f))
        except Exception as e:
            log("配置文件读不了：%s" % e)
    return cfg


def main():
    ap = argparse.ArgumentParser(description="深澜 SRun 校园网自动登录")
    ap.add_argument("--force", action="store_true", help="强制登录，不检查是否在线")
    ap.add_argument("--status", action="store_true", help="只检查在线状态")
    ap.add_argument("--watch", type=int, metavar="SEC", help="守护模式，每 N 秒检查一次")
    ap.add_argument("--probe", action="store_true", help="只探测门户接口类型")
    ap.add_argument("--variant", choices=["A", "B", "C"], help="强制指定接口")
    ap.add_argument("--debug", action="store_true", help="打印原始响应")
    ap.add_argument("--selftest", action="store_true", help="运行加密自检")
    ap.add_argument("--ignore-quiet", action="store_true", help="无视夜间时段强行登录")
    args = ap.parse_args()

    if args.selftest:
        return selftest()

    cfg = load_config()
    check_url = cfg.get("online_check", DEFAULT_CONFIG["online_check"])

    if args.probe:
        portal = cfg["portal"].rstrip("/")
        html, _ = fetch_portal_page(portal, str(cfg.get("ac_id", "1")), args.debug)
        ip = parse_ip_from_page(html, portal)
        v = detect_variant(html, portal, args.debug)
        log("门户：%s" % portal)
        log("本机被识别 IP：%s" % ip)
        log("接口类型：%s" % {"A": "A 老版 srun_bx1", "B": "B 新版 SRunPortalXEncode",
                            "auto": "判断不出来，登录时两套都试（先 A 后 B）"}[v])
        return 0

    if args.status:
        on = is_online(check_url)
        log("当前网络：%s" % ("在线" if on else "离线"))
        return 0 if on else 1

    if args.watch:
        log("守护模式启动，每 %d 秒检查一次（Ctrl+C 退出）" % args.watch)
        win = quiet_window(cfg)
        if win:
            log("夜间时段：%02d:%02d - %02d:%02d 之间不自动登录" % (win[0] // 60, win[0] % 60, win[1] // 60, win[1] % 60))
        while True:
            try:
                if in_quiet(cfg):
                    wait = max(30, min(seconds_until_quiet_end(cfg), args.watch * 10))
                    log("现在是夜间时段，暂停尝试登录，%d 秒后再看" % wait)
                    time.sleep(wait)
                    continue
                if not is_online(check_url):
                    log("检测到断网，开始登录…")
                    if do_login(cfg, args.debug, args.variant):
                        time.sleep(3)
                        log("复查：%s" % ("已在线" if is_online(check_url) else "仍未在线"))
                time.sleep(args.watch)
            except KeyboardInterrupt:
                log("守护模式已退出")
                return 0
            except Exception as e:
                log("循环异常：%s: %s" % (type(e).__name__, e))
                time.sleep(args.watch)

    if in_quiet(cfg) and not args.ignore_quiet:
        win = quiet_window(cfg)
        log("现在是夜间时段（%02d:%02d-%02d:%02d），不尝试登录；要强行登录加 --ignore-quiet"
            % (win[0] // 60, win[0] % 60, win[1] // 60, win[1] % 60))
        return 3

    if not args.force and is_online(check_url):
        log("网络已在线，无需登录（要强制登录加 --force）")
        return 0

    if not do_login(cfg, args.debug, args.variant):
        log("登录失败，日志：%s" % LOG_PATH)
        return 1

    time.sleep(2)
    on = is_online(check_url)
    log("复查网络：%s" % ("在线，搞定" if on else "接口报成功但仍不通，可能还要在网页上点一下"))
    return 0 if on else 2


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        sys.exit(130)