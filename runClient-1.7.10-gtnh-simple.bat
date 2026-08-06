@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\runClient-1.7.10-gtnh-simple.ps1" %*
exit /b %ERRORLEVEL%
