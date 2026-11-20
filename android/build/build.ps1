$ErrorActionPreference = "Continue"
$env:JAVA_HOME = "C:\Java\jdk-21.0.13+9"
$env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH
$sdk = "C:\android-sdk"
$bt = "$sdk\build-tools\34.0.0"
$aapt2 = "$bt\aapt2.exe"
$d8 = "$bt\d8.bat"
$zipalign = "$bt\zipalign.exe"
$apksigner = "$bt\apksigner.bat"
$androidJar = "$sdk\platforms\android-33\android.jar"
$proj = "C:\srun-app"
$out = "$proj\out"

if (-not (Test-Path $androidJar)) { throw "android.jar missing: $androidJar" }
if (-not (Test-Path $aapt2)) { throw "aapt2 missing" }

Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $out | Out-Null

Write-Output "[1/6] aapt2 compile + link"
$flat = @()
if (Test-Path "$proj\res") {
  New-Item -ItemType Directory -Force "$out\res" | Out-Null
  & $aapt2 compile --dir "$proj\res" -o "$out\res" 2>&1 | Select-Object -Last 5
  if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }
  $flat = (Get-ChildItem "$out\res" -Recurse -Filter *.flat).FullName
}
& $aapt2 link -o "$out\base.apk" --manifest "$proj\AndroidManifest.xml" -I $androidJar --auto-add-overlay $flat 2>&1 | Select-Object -Last 5
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Write-Output "[2/6] javac"
$srcs = Get-ChildItem "$proj\src" -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$classes = "$out\classes"
New-Item -ItemType Directory -Force $classes | Out-Null
$srcArg = "@$out\sources.txt"
$lines = $srcs | ForEach-Object { '"' + ($_ -replace '\\', '/') + '"' }
[System.IO.File]::WriteAllLines("$out\\sources.txt", $lines, [System.Text.Encoding]::ASCII)
& javac -encoding UTF-8 --release 11 -nowarn -classpath $androidJar -d $classes $srcArg 2>&1 | Select-Object -Last 25
if ($LASTEXITCODE -ne 0) { throw "javac failed" }
$classFiles = Get-ChildItem $classes -Recurse -Filter *.class
Write-Output ("classes: " + $classFiles.Count)

Write-Output "[3/6] d8"
New-Item -ItemType Directory -Force "$out\dex" | Out-Null
$classArgs = $classFiles | ForEach-Object { $_.FullName }
& java -cp C:\r8\r8.jar com.android.tools.r8.D8 --min-api 21 --lib $androidJar --output "$out\dex" $classArgs 2>&1 | Select-Object -Last 15
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }
if (-not (Test-Path "$out\dex\classes.dex")) { throw "classes.dex not produced" }

Write-Output "[4/6] pack dex into apk"
Copy-Item "$out\base.apk" "$out\packed.apk" -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open("$out\packed.apk", "Update")
[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, "$out\dex\classes.dex", "classes.dex", "Optimal") | Out-Null
$zip.Dispose()

Write-Output "[5/6] zipalign"
& $zipalign -f 4 "$out\packed.apk" "$out\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

Write-Output "[6/6] sign"
$ks = "$proj\debug.keystore"
if (-not (Test-Path $ks)) {
  & keytool -genkeypair -keystore $ks -storepass android -keypass android -alias androiddebugkey `
    -dname "CN=Android Debug,O=Android,C=US" -keyalg RSA -keysize 2048 -validity 10000 2>&1 | Select-Object -Last 3
}
& $apksigner sign --ks $ks --ks-pass pass:android --key-pass pass:android --min-sdk-version 21 --v1-signing-enabled true --v2-signing-enabled true --out "$proj\srun-login.apk" "$out\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }
& $apksigner verify --print-certs "$proj\srun-login.apk" 2>&1 | Select-Object -First 4

$apk = Get-Item "$proj\srun-login.apk"
Write-Output ("APK OK: " + $apk.FullName + " " + $apk.Length + " bytes")
