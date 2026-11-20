package com.xxhh.srun;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;

/** 深澜加密：XXTEA 变体 + 自定义 base64 + HMAC-MD5 + SHA1 */
final class Crypto {
    static final String ALPHA = "LVoJPiCN2R8G90yg+hmFHuacZ1OWMnrsSTXkYpUq/3dlbfKwv6xztjI7DeBE45QA";

    static int ordat(String s, int i) {
        return i < s.length() ? s.charAt(i) : 0;
    }

    static int[] sencode(String s, boolean withLen) {
        int n = s.length();
        int[] out = new int[(n + 3) / 4 + (withLen ? 1 : 0)];
        int idx = 0;
        for (int i = 0; i < n; i += 4) {
            out[idx++] = ordat(s, i) | (ordat(s, i + 1) << 8) | (ordat(s, i + 2) << 16) | (ordat(s, i + 3) << 24);
        }
        if (withLen) out[idx] = n;
        return out;
    }

    static byte[] xencode(String msg, String key) {
        if (msg == null || msg.length() == 0) return new byte[0];
        int[] v = sencode(msg, true);
        int[] k = sencode(key, false);
        int[] kk = new int[4];
        for (int i = 0; i < 4; i++) kk[i] = i < k.length ? k[i] : 0;
        int n = v.length - 1;
        int z = v[n], y = v[0], d = 0;
        final int c = 0x9E3779B9;
        int q = 6 + 52 / (n + 1);
        while (q-- > 0) {
            d += c;
            int e = (d >>> 2) & 3;
            int p = 0;
            for (; p < n; p++) {
                y = v[p + 1];
                int m = (z >>> 5) ^ (y << 2);
                m += ((y >>> 3) ^ (z << 4)) ^ (d ^ y);
                m += kk[(p & 3) ^ e] ^ z;
                v[p] += m;
                z = v[p];
            }
            y = v[0];
            int m = (z >>> 5) ^ (y << 2);
            m += ((y >>> 3) ^ (z << 4)) ^ (d ^ y);
            m += kk[(n & 3) ^ e] ^ z;
            v[n] += m;
            z = v[n];
        }
        byte[] out = new byte[v.length * 4];
        for (int i = 0; i < v.length; i++) {
            int x = v[i];
            out[i * 4] = (byte) x;
            out[i * 4 + 1] = (byte) (x >>> 8);
            out[i * 4 + 2] = (byte) (x >>> 16);
            out[i * 4 + 3] = (byte) (x >>> 24);
        }
        return out;
    }

    static String srunBase64(byte[] b) {
        if (b == null || b.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        int len = b.length;
        int imax = len - len % 3;
        for (int i = 0; i < imax; i += 3) {
            int x = (b[i] & 0xff) << 16 | (b[i + 1] & 0xff) << 8 | (b[i + 2] & 0xff);
            sb.append(ALPHA.charAt((x >>> 18) & 63)).append(ALPHA.charAt((x >>> 12) & 63))
              .append(ALPHA.charAt((x >>> 6) & 63)).append(ALPHA.charAt(x & 63));
        }
        int rest = len - imax;
        if (rest == 1) {
            int x = (b[imax] & 0xff) << 16;
            sb.append(ALPHA.charAt((x >>> 18) & 63)).append(ALPHA.charAt((x >>> 12) & 63)).append("==");
        } else if (rest == 2) {
            int x = (b[imax] & 0xff) << 16 | (b[imax + 1] & 0xff) << 8;
            sb.append(ALPHA.charAt((x >>> 18) & 63)).append(ALPHA.charAt((x >>> 12) & 63))
              .append(ALPHA.charAt((x >>> 6) & 63)).append("=");
        }
        return sb.toString();
    }

    static String stdBase64(byte[] b) {
        return android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP);
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x & 0xff));
        return sb.toString();
    }

    static String md5(String s) {
        try {
            return hex(MessageDigest.getInstance("MD5").digest(s.getBytes("UTF-8")));
        } catch (Exception e) {
            return "";
        }
    }

    static String sha1(String s) {
        try {
            return hex(MessageDigest.getInstance("SHA-1").digest(s.getBytes("UTF-8")));
        } catch (Exception e) {
            return "";
        }
    }

    static String hmacMd5(String key, String msg) {
        try {
            Mac mac = Mac.getInstance("HmacMD5");
            mac.init(new SecretKeySpec(key.getBytes("UTF-8"), "HmacMD5"));
            return hex(mac.doFinal(msg.getBytes("UTF-8")));
        } catch (Exception e) {
            return "";
        }
    }

    static String bytesToHex(byte[] b) {
        return hex(b);
    }
}
