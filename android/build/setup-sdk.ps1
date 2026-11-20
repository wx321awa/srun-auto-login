$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
$root = "C:\android"
New-Item -ItemType Directory -Force $root | Out-Null
$zip = "$root\clt.zip"
if (-not (Test-Path $zip)) {
  Write-Output "downloading commandline-tools..."
  Invoke-WebRequest -Uri "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" -OutFile $zip -TimeoutSec 600
}
if (-not (Test-Path "$root\cmdline-tools\latest\bin\sdkmanager.bat")) {
  New-Item -ItemType Directory -Force "$root\tmp" | Out-Null
  Expand-Archive $zip -DestinationPath "$root\tmp" -Force
  New-Item -ItemType Directory -Force "$root\cmdline-tools\latest" | Out-Null
  Copy-Item "$root\tmp\cmdline-tools\*" "$root\cmdline-tools\latest" -Recurse -Force
}
$sdk = "C:\android-sdk"
$sdkmanager = "$root\cmdline-tools\latest\bin\sdkmanager.bat"
Write-Output "accepting licenses..."
$yes = ("y`r`n" * 100)
$yes | & $sdkmanager --sdk_root=$sdk --licenses 2>&1 | Select-Object -Last 3
Write-Output "installing packages..."
$yes | & $sdkmanager --sdk_root=$sdk "platform-tools" "platforms;android-34" "build-tools;34.0.0" 2>&1 | Select-Object -Last 5
Write-Output "--- installed ---"
Get-ChildItem "$sdk\build-tools","$sdk\platforms" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Name
Test-Path "$sdk\build-tools\34.0.0\aapt2.exe"
Test-Path "$sdk\platforms\android-34\android.jar"
