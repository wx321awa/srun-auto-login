$ErrorActionPreference = "Continue"
$root = "C:\android"
$sdk = "C:\android-sdk"
New-Item -ItemType Directory -Force $root | Out-Null
$btZip = "$root\build-tools.zip"
$pfZip = "$root\platform-33.zip"

Write-Output "== downloading build-tools =="
if (-not (Test-Path "$btZip.ok")) {
  & curl.exe -L --retry 3 --connect-timeout 20 -o $btZip "https://mirrors.cloud.tencent.com/AndroidSDK/build-tools_r34-windows.zip"
  if ($LASTEXITCODE -eq 0) { New-Item -ItemType File "$btZip.ok" -Force | Out-Null }
}
Write-Output ("build-tools zip: " + (Get-Item $btZip).Length)

Write-Output "== downloading platform-33 =="
& curl.exe -L --retry 3 --connect-timeout 20 -o $pfZip "https://mirrors.cloud.tencent.com/AndroidSDK/platform-33_r02.zip"
Write-Output ("platform zip: " + (Get-Item $pfZip).Length)

Write-Output "== extracting =="
Remove-Item "$root\x1" -Recurse -Force -ErrorAction SilentlyContinue
Expand-Archive $btZip -DestinationPath "$root\x1" -Force
Get-ChildItem "$root\x1" | Select-Object -ExpandProperty Name
Remove-Item "$root\x2" -Recurse -Force -ErrorAction SilentlyContinue
Expand-Archive $pfZip -DestinationPath "$root\x2" -Force
Get-ChildItem "$root\x2" | Select-Object -ExpandProperty Name

Write-Output "== installing =="
New-Item -ItemType Directory -Force "$sdk\build-tools\34.0.0" | Out-Null
$btSrc = (Get-ChildItem "$root\x1" -Directory | Select-Object -First 1).FullName
Copy-Item "$btSrc\*" "$sdk\build-tools\34.0.0" -Recurse -Force
New-Item -ItemType Directory -Force "$sdk\platforms\android-33" | Out-Null
$pfSrc = (Get-ChildItem "$root\x2" -Directory | Select-Object -First 1).FullName
Copy-Item "$pfSrc\*" "$sdk\platforms\android-33" -Recurse -Force

Write-Output "== check =="
foreach ($p in @("$sdk\build-tools\34.0.0\aapt2.exe", "$sdk\build-tools\34.0.0\d8.bat", "$sdk\build-tools\34.0.0\zipalign.exe", "$sdk\build-tools\34.0.0\apksigner.bat", "$sdk\platforms\android-33\android.jar")) {
  Write-Output ((Test-Path $p).ToString() + "  " + $p)
}
Write-Output "DONE"
