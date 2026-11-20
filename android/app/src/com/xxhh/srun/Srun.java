package com.xxhh.srun;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 深澜门户：探测在线状态 + 登录 */
final class Srun {
    static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    static class Result {
        boolean ok;
        String msg;
        String kind;

        Result(boolean ok, String msg, String kind) {
            this.ok = ok;
            this.msg = msg;
            this.kind = kind;
        }
    }

    static String lastBase = "http://10.33.0.2";

    static String base(String portal) {
        if (portal == null || portal.isEmpty()) return lastBase = "http://10.33.0.2";
        int i = portal.indexOf("://");
        if (i < 0) return lastBase = "http://10.33.0.2";
        int j = portal.indexOf('/', i + 3);
        return lastBase = (j < 0 ? portal : portal.substring(0, j));
    }

    static String host(String portal) {
        try {
            return new URL(base(portal)).getHost();
        } catch (Exception e) {
            return "10.33.0.2";
        }
    }

    /** 门户会下发 Cookie，认证接口要用同一份 */
    static String cookies = "";

    static String cookieOf(String setCookie) {
        return Diag.cookieOf(setCookie);
    }

    static void remember(String url, HttpURLConnection c) {
        try {
            if (url == null) return;
            try {
                if (!new URL(url).getHost().equals(new URL(lastBase).getHost())) return;
            } catch (Exception e) {
                return;
            }
            StringBuilder ck = new StringBuilder();
            int i = 1;
            String k;
            while ((k = c.getHeaderFieldKey(i)) != null) {
                if (k != null && k.toLowerCase().contains("set-cookie")) {
                    String one = c.getHeaderField(i);
                    if (one != null) {
                        one = one.split(";", 2)[0].trim();
                        if (!one.isEmpty() && one.contains("=")) {
                            if (ck.length() > 0) ck.append("; ");
                            ck.append(one);
                        }
                    }
                }
                i++;
            }
            if (ck.length() > 0) cookies = ck.toString();
        } catch (Exception ignored) {
        }
    }

    static String http(String url, String method, String body, int timeout) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            if (cookies != null && !cookies.isEmpty()) c.setRequestProperty("Cookie", cookies);
            c.setRequestMethod(method);
            c.setConnectTimeout(timeout);
            c.setReadTimeout(timeout);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", UA);
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                c.setRequestProperty("X-Requested-With", "XMLHttpRequest");
                OutputStream os = c.getOutputStream();
                os.write(body.getBytes("UTF-8"));
                os.close();
            }
            int code = c.getResponseCode();
            remember(url, c);
            InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (is == null) return "##" + code;
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
            is.close();
            String text = new String(bo.toByteArray(), "UTF-8");
            return "##" + code + "\n" + text;
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    static int codeOf(String raw) {
        if (raw == null || !raw.startsWith("##")) return -1;
        int nl = raw.indexOf('\n');
        try {
            return Integer.parseInt(raw.substring(2, nl < 0 ? raw.length() : nl));
        } catch (Exception e) {
            return -1;
        }
    }

    static String bodyOf(String raw) {
        if (raw == null) return "";
        int nl = raw.indexOf('\n');
        return nl < 0 ? "" : raw.substring(nl + 1);
    }

    static boolean online(String extraCheck) {
        String[] urls = new String[]{
                extraCheck == null || extraCheck.isEmpty() ? null : extraCheck,
                "http://connect.rom.miui.com/generate_204",
                "http://www.msftconnecttest.com/connecttest.txt"
        };
        for (String u : urls) {
            if (u == null) continue;
            String raw = http(u, "GET", null, 5000);
            if (raw == null) continue;
            int code = codeOf(raw);
            String body = bodyOf(raw);
            if (code == 204) return true;
            if (body.contains("Microsoft Connect Test")) return true;
            String low = body.toLowerCase();
            if (low.contains("<html")) return false;
            if (code >= 200 && code < 300) return true;
            if (code == 301 || code == 302 || code == 307 || code == 308 || code == 401 || code == 403) return false;
        }
        return false;
    }

    static String localIp(String portal) {
        Socket s = new Socket();
        try {
            s.connect(new java.net.InetSocketAddress(InetAddress.getByName(host(portal)), 80), 1500);
            return s.getLocalAddress().getHostAddress();
        } catch (Exception e) {
            return "";
        } finally {
            try {
                s.close();
            } catch (Exception ignored) {
            }
        }
    }

    static String detectVariant(String portal, String acId) {
        String raw = http(base(portal) + "/srun_portal_pc?ac_id=" + acId + "&theme=pro", "GET", null, 6000);
        String html = bodyOf(raw);
        if (html.contains("{SRunPortalXEncode}")) return "B";
        if (html.contains("{SRBX1}")) return "A";
        return "auto";
    }

    static String pick(String body) {
        if (body == null) return null;
        Matcher m = Pattern.compile("\"challenge\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
        if (m.find()) return m.group(1);
        m = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
        return m.find() ? m.group(1) : null;
    }

    static String challenge(String portal, String user, String ip) {
        String b = base(portal);
        String ref = b + "/srun_portal_pc?ac_id=1&theme=pro";
        String[] eps = {
                b + "/cgi-bin/get_challenge?callback=jsonp_1&username=" + enc(user) + "&ip=" + enc(ip) + "&_=" + System.currentTimeMillis(),
                b + "/cgi-bin/get_challenge?callback=jsonp_2&_=" + System.currentTimeMillis(),
                b + "/cgi-bin/get_challenge?callback=jsonp_3&username=" + enc(user) + "&ip=" + enc(ip),
        };
        for (String url : eps) {
            String token = pick(bodyOf(http(url, "GET", ref, 6000)));
            if (token != null && !token.isEmpty()) return token;
        }
        return null;
    }

    static String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    static String qs(Map<String, String> p) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : p.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
        }
        return sb.toString();
    }

    static String infoJson(String user, String pass, String ip, String acId) {
        return "{\"username\":\"" + user + "\",\"password\":\"" + pass + "\",\"ip\":\"" + ip
                + "\",\"acid\":\"" + acId + "\",\"enc_ver\":\"srun_bx1\"}";
    }

    static String loginA(String portal, String acId, String user, String pass, String ip, String token) {
        String info = "{SRBX1}" + Crypto.srunBase64(Crypto.xencode(infoJson(user, pass, ip, acId), token));
        String hmd5 = Crypto.hmacMd5(token, pass);
        String n = "200", type = "1";
        String join = token + user + token + hmd5 + token + acId + token + ip + token + n + token + type + token + info;
        Map<String, String> p = new LinkedHashMap<>();
        p.put("callback", "jsonp_" + (System.currentTimeMillis() % 100000));
        p.put("action", "login");
        p.put("username", user);
        p.put("password", "{MD5}" + hmd5);
        p.put("ac_id", acId);
        p.put("ip", ip);
        p.put("info", info);
        p.put("chksum", Crypto.sha1(join));
        p.put("n", n);
        p.put("type", type);
        p.put("os", "Windows 10");
        p.put("name", "Windows");
        p.put("double_stack", "0");
        p.put("_", String.valueOf(System.currentTimeMillis()));
        return bodyOf(http(base(portal) + "/cgi-bin/srun_portal?" + qs(p), "GET", base(portal) + "/srun_portal_pc?ac_id=" + acId + "&theme=pro", 8000));
    }

    static String loginB(String portal, String acId, String user, String pass, String ip, String token) {
        String info = "{SRunPortalXEncode}" + Crypto.stdBase64(Crypto.xencode(infoJson(user, pass, ip, acId), token));
        String hmd5 = Crypto.md5(pass + token);
        String n = "200", type = "1";
        String chk = Crypto.sha1(hmd5 + token + acId + ip + n + type + info);
        Map<String, String> p = new LinkedHashMap<>();
        p.put("callback", "jsonp_" + (System.currentTimeMillis() % 100000));
        p.put("action", "login");
        p.put("username", user);
        p.put("password", "{MD5}" + hmd5);
        p.put("ac_id", acId);
        p.put("ip", ip);
        p.put("info", info);
        p.put("chksum", chk);
        p.put("n", n);
        p.put("type", type);
        p.put("os", "Windows 10");
        p.put("name", "Windows");
        p.put("double_stack", "0");
        p.put("_", String.valueOf(System.currentTimeMillis()));
        return bodyOf(http(base(portal) + "/cgi-bin/srun_portal?" + qs(p), "GET", base(portal) + "/srun_portal_pc?ac_id=" + acId + "&theme=pro", 8000));
    }

    static String loginC(String portal, String acId, String user, String pass, String ip) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("action", "login");
        p.put("username", user);
        p.put("password", "{MD5}" + Crypto.md5(pass));
        p.put("ac_id", acId);
        p.put("user_ip", ip);
        p.put("nas_ip", "");
        p.put("user_mac", "");
        p.put("save_me", "1");
        p.put("ajax", "1");
        p.put("double_stack", "0");
        return bodyOf(http(base(portal) + "/srun_portal_pc?ac_id=" + acId + "&theme=pro", "POST", qs(p), 8000));
    }

    static Result parse(String body) {
        if (body == null) return new Result(false, "网络请求失败", "net");
        if (body.matches("(?s).*\"(error|result|res)\"\\s*:\\s*\"ok\".*")
                || body.contains("login_ok")
                || body.matches("(?s).*\"suc_msg\"\\s*:\\s*\"[^\"]+\".*")) {
            return new Result(true, "登录成功", "");
        }
        String[][] codes = {
                {"E2901", "账号或密码不对", "password"},
                {"E2553", "账号密码不对 / 账号被停用", "password"},
                {"E2620", "已在线，无需重复登录", "online"},
                {"E2806", "需要验证码", "param"},
                {"E6518", "参数错误（加密串不对）", "param"},
                {"E6306", "接口地址不对", "param"},
                {"E2616", "账户已停用", "param"},
        };
        for (String[] c : codes) {
            if (body.contains(c[0])) {
                boolean ok = c[0].equals("E2620");
                return new Result(ok, c[0] + " " + c[1], ok ? "" : c[2]);
            }
        }
        String short1 = body.replaceAll("\\s+", " ").trim();
        if (short1.length() > 120) short1 = short1.substring(0, 120);
        return new Result(false, short1.isEmpty() ? "没有返回内容" : short1, "unknown");
    }

    /** 完整登录：拿 ip → challenge → 按 A/B/C 顺序试 */
    static Result doLogin(String portal, String acId, String user, String pass) {
        if (user == null || user.isEmpty() || pass == null || pass.isEmpty()) {
            return new Result(false, "还没填账号密码", "config");
        }
        String ip = localIp(portal);
        if (ip.isEmpty()) return new Result(false, "拿不到本机 IP（没连校园 WiFi？）", "net");
        String token = challenge(portal, user, ip);
        if (token == null) {
            // 老门户没有 challenge 接口，直接按老式表单登录
            Result c = parse(loginC(portal, acId, user, pass, ip.isEmpty() ? "" : ip));
            if (c.ok) return new Result(true, c.msg + "（C，无 challenge）", "C:");
            return new Result(false, "拿不到 challenge，且老式接口也没通：" + c.msg, "net");
        }
        String variant = detectVariant(portal, acId);
        String[] order = variant.equals("B") ? new String[]{"B", "A", "C"} : new String[]{"A", "B", "C"};
        Result last = new Result(false, "没试任何接口", "unknown");
        for (String v : order) {
            String body;
            if (v.equals("A")) body = loginA(portal, acId, user, pass, ip, token);
            else if (v.equals("B")) body = loginB(portal, acId, user, pass, ip, token);
            else body = loginC(portal, acId, user, pass, ip);
            last = parse(body);
            if (last.ok) return new Result(true, last.msg + "（" + v + "）", v + ":" + last.kind);
            if ("password".equals(last.kind)) return last;
        }
        return last;
    }
}
