package com.xxhh.srun;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

/** 一键诊断：把门户页面的真实响应摊开给你看 */
final class Diag {
    static class P {
        int code = -1;
        String body = "";
        String finalUrl = "";
        String setCookie = "";
        String err = "";
    }

    static P probe(String url, String method, String body, String cookie, String referer) {
        P p = new P();
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(6000);
            c.setReadTimeout(6000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", Srun.UA);
            if (cookie != null && !cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);
            if (referer != null && !referer.isEmpty()) c.setRequestProperty("Referer", referer);
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                c.setRequestProperty("X-Requested-With", "XMLHttpRequest");
                OutputStream os = c.getOutputStream();
                os.write(body.getBytes("UTF-8"));
                os.close();
            }
            p.code = c.getResponseCode();
            p.finalUrl = c.getURL().toString();
            StringBuilder ck = new StringBuilder();
            int i = 1;
            String k;
            while ((k = c.getHeaderFieldKey(i)) != null) {
                if (k != null && k.toLowerCase().contains("set-cookie")) {
                    if (ck.length() > 0) ck.append(" | ");
                    ck.append(c.getHeaderField(i));
                }
                i++;
            }
            p.setCookie = ck.toString();
            InputStream is = p.code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (is != null) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
                is.close();
                p.body = new String(bo.toByteArray(), "UTF-8");
            }
        } catch (Exception e) {
            p.err = e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            if (c != null) c.disconnect();
        }
        return p;
    }

    static String brief(String s) {
        if (s == null) return "(空)";
        String t = s.replaceAll("\\s+", " ").trim();
        if (t.length() > 240) t = t.substring(0, 240) + "…";
        return t.isEmpty() ? "(空)" : t;
    }

    static String run(String portal, String acId, String user) {
        if (acId == null || acId.trim().isEmpty()) acId = "1";
        String base = Srun.base(portal);
        String ip = Srun.localIp(portal);
        StringBuilder sb = new StringBuilder();
        sb.append("【诊断】").append(new java.text.SimpleDateFormat("MM-dd HH:mm:ss").format(new java.util.Date())).append('\n');
        sb.append("门户 base = ").append(base).append("\n");
        sb.append("ac_id = ").append(acId).append('\n');
        sb.append("1) 本机出口IP = ").append(ip.isEmpty() ? "拿不到" : ip).append('\n');

        String purl = base + "/srun_portal_pc?ac_id=" + acId + "&theme=pro";
        P pg = probe(purl, "GET", null, null, null);
        sb.append("2) 门户页 HTTP ").append(pg.code);
        if (!pg.err.isEmpty()) sb.append(" 错误 ").append(pg.err);
        sb.append('\n');
        if (!pg.finalUrl.equals(purl)) sb.append("   最终地址 ").append(pg.finalUrl).append('\n');
        sb.append("   长度 ").append(pg.body.length()).append('\n');
        sb.append("   片段 ").append(brief(pg.body)).append('\n');
        sb.append("   Set-Cookie ").append(pg.setCookie.isEmpty() ? "(无)" : pg.setCookie).append('\n');

        P g = probe("http://connect.rom.miui.com/generate_204", "GET", null, null, null);
        sb.append("3) 公网204探测 HTTP ").append(g.code).append(" 长度 ").append(g.body.length()).append('\n');

        String ck = cookieOf(pg.setCookie);
        String ref = purl;
        P c1 = probe(base + "/cgi-bin/get_challenge?callback=jsonp_a&username=" + Srun.enc(user)
                + "&ip=" + Srun.enc(ip) + "&_=" + System.currentTimeMillis(), "GET", null, ck, ref);
        sb.append("4) get_challenge(带参数) HTTP ").append(c1.code);
        if (!c1.err.isEmpty()) sb.append(" 错误 ").append(c1.err);
        sb.append(" 长度 ").append(c1.body.length()).append('\n');
        sb.append("   片段 ").append(brief(c1.body)).append('\n');

        P c2 = probe(base + "/cgi-bin/get_challenge?callback=jsonp_b&_=" + System.currentTimeMillis(), "GET", null, ck, ref);
        sb.append("5) get_challenge(不带参数) HTTP ").append(c2.code).append(" 长度 ").append(c2.body.length()).append('\n');
        sb.append("   片段 ").append(brief(c2.body)).append('\n');

        String conclusion;
        if (ip.isEmpty()) conclusion = "连不上门户所在网络：WiFi 没连上 / 门户地址填错 / 手机在用流量";
        else if (pg.code != 200) conclusion = "门户页打不开（HTTP " + pg.code + "），先确认地址和 ac_id";
        else if (hasChallenge(c1.body)) conclusion = "认证接口正常，拿 challenge 没问题 —— 之前的报错可能是旧版本";
        else if (hasChallenge(c2.body)) conclusion = "认证接口要空参数，代码会按这个路径走";
        else conclusion = "门户页能开，但认证接口返回异常 —— 把这整段发我";
        sb.append("结论：").append(conclusion).append('\n');
        return sb.toString();
    }

    static boolean hasChallenge(String s) {
        return s != null && (s.contains("\"challenge\"") || s.contains("\"token\""));
    }

    static String cookieOf(String setCookie) {
        if (setCookie == null || setCookie.isEmpty()) return "";
        ArrayList<String> out = new ArrayList<String>();
        for (String part : setCookie.split("\\s*\\|\\s*")) {
            String one = part.split(";", 2)[0].trim();
            if (!one.isEmpty() && one.contains("=")) out.add(one);
        }
        StringBuilder sb = new StringBuilder();
        for (String s : out) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(s);
        }
        return sb.toString();
    }
}
