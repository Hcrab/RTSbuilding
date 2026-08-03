@echo off
setlocal
cd /d "%~dp0"

echo [RTSBuilding] Starting the Gradle-managed Forge 1.12.2 development client...
echo [RTSBuilding] Game directory: %CD%\run\dev-client
echo [RTSBuilding] Runtime mods are declared in gradle\client-development.gradle.
call "%CD%\gradlew.bat" -PrtsClientInstance=dev-client runClient --no-daemon --no-configuration-cache %*
set "RTS_EXIT_CODE=%ERRORLEVEL%"

if not "%RTS_EXIT_CODE%"=="0" pause
exit /b %RTS_EXIT_CODE%
