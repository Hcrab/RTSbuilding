@echo off
setlocal
cd /d "%~dp0"

echo [RTSBuilding] Running the automated Forge 1.12.2 client-world smoke test...
call "%CD%\gradlew.bat" runClientSmoke --no-daemon --no-configuration-cache %*
set "RTS_EXIT_CODE=%ERRORLEVEL%"

if not "%RTS_EXIT_CODE%"=="0" pause
exit /b %RTS_EXIT_CODE%
