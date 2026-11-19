$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$root = "C:\android"
$sdk = "C:\android-sdk"
function Unzip($zip, $dest) {
  if (Test-Path $dest) { Remove-Item $dest -Recurse -Force }
  New-Item -ItemType Directory -Force $dest | Out-Null
  [System.IO.Compression.ZipFile]::ExtractToDirectory($zip, $dest)
}
Write-Output "unzip build-tools"
Unzip "$root\build-tools.zip" "$root\x1"
Write-Output "unzip platform"
Unzip "$root\platform-33.zip" "$root\x2"
Write-Output "x1:"; Get-ChildItem "$root\x1" | Select-Object -ExpandProperty Name
Write-Output "x2:"; Get-ChildItem "$root\x2" | Select-Object -ExpandProperty Name
$bt = (Get-ChildItem "$root\x1" -Directory | Select-Object -First 1).FullName
$pf = (Get-ChildItem "$root\x2" -Directory | Select-Object -First 1).FullName
New-Item -ItemType Directory -Force "$sdk\build-tools\34.0.0" | Out-Null
Copy-Item "$bt\*" "$sdk\build-tools\34.0.0" -Recurse -Force
New-Item -ItemType Directory -Force "$sdk\platforms\android-33" | Out-Null
Copy-Item "$pf\*" "$sdk\platforms\android-33" -Recurse -Force
Write-Output "== check =="
foreach ($p in @("$sdk\build-tools\34.0.0\aapt2.exe","$sdk\build-tools\34.0.0\d8.bat","$sdk\build-tools\34.0.0\zipalign.exe","$sdk\build-tools\34.0.0\apksigner.bat","$sdk\platforms\android-33\android.jar")) {
  Write-Output ((Test-Path $p).ToString() + "  " + $p)
}
Write-Output "DONE3"
