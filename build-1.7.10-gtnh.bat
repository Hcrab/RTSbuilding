@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build-1.7.10-gtnh.ps1" %*
exit /b %ERRORLEVEL%
