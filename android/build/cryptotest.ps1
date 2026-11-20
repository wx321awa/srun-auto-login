$env:JAVA_HOME = "C:\Java\jdk-21.0.13+9"
$env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH
$dir = "C:\cryptotest"
New-Item -ItemType Directory -Force $dir | Out-Null
$src = [System.IO.File]::ReadAllText("C:\srun-app\src\com\xxhh\srun\Crypto.java")
$src = $src -replace "(?m)^package com\.xxhh\.srun;\s*$", ""
$src = $src -replace "return android\.util\.Base64\.encodeToString\(b, android\.util\.Base64\.NO_WRAP\);", "return java.util.Base64.getEncoder().encodeToString(b);"
[System.IO.File]::WriteAllText("$dir\Crypto.java", $src, (New-Object System.Text.UTF8Encoding($false)))
$test = @'
public class Test {
    static void chk(String name, String msg, String key, String want) {
        String got = Crypto.srunBase64(Crypto.xencode(msg, key));
        System.out.println((got.equals(want) ? "OK   " : "DIFF ") + name + " -> " + got + (got.equals(want) ? "" : "  want " + want));
    }
    public static void main(String[] a) {
        chk("v1", "{\"username\":\"20240001\",\"password\":\"abc123\",\"ip\":\"10.7.7.2\",\"acid\":\"1\",\"enc_ver\":\"srun_bx1\"}", "tok123",
            "FYzrPpNvejCJm1n1zbwmXno3mboHV6mqv8nxKlHwOJ5W1eNF+2PxyCp5KlluDf4u0MsjPJ3AkNv81QBD3tFALtXvTLGuGAxDvVZlKHD2x02usiJ+m1BGI2teBARV5NZH");
        chk("v2-empty", "", "k", "");
        chk("v3", "{\"a\":1}", "", "aGj+3rIRrmb4wm6t");
        chk("v4-long", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx", "keykeykey",
            "VlIbsGf/5w//kMD2lqdBffsbXmUl1HHMl2YQy6ZSuU542IRugi2OkCOQXFb=");
        chk("v5", "{\"a\":\"b\"}", "0123456789abcdef", "iNeQIEdXxDCEEXRC9qVGrv==");
        System.out.println("hmac_md5 -> " + Crypto.hmacMd5("tok123", "abc123") + "  want 37bc2702b6575382baebe5b424ac9f8d");
        System.out.println("md5      -> " + Crypto.md5("abc123") + "  want e99a18c428cb38d5f260853678922e03");
        System.out.println("sha1     -> " + Crypto.sha1("abc") + "  want a9993e364706816aba3e25717850c26c9cd0d89d");
    }
}
'@
[System.IO.File]::WriteAllText("$dir\Test.java", $test, (New-Object System.Text.UTF8Encoding($false)))
& javac -encoding UTF-8 -d $dir "$dir\Crypto.java" "$dir\Test.java"
& java -cp $dir Test
