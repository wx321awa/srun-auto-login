@echo off
setlocal
set "DIR=%~dp0"
echo ============================================
echo  SRun auto login - register scheduled task
echo  (check network every 5 minutes)
echo ============================================
schtasks /create /f /tn "SRunAutoLogin" /sc minute /mo 5 /tr "wscript.exe \"%DIR%run_hidden.vbs\""
if errorlevel 1 (
    echo.
    echo FAILED. Right-click this file and "Run as administrator" if needed.
) else (
    echo.
    echo OK. Task name: SRunAutoLogin
    echo To remove: schtasks /delete /tn "SRunAutoLogin" /f
)
pause