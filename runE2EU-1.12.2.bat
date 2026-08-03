@echo off
setlocal
cd /d "%~dp0"

echo [RTSBuilding] Preparing and starting E2EU 1.3.9.2...
echo [RTSBuilding] Game directory: %CD%\run\e2eu-client
echo [RTSBuilding] Gradle Daemon JVM is pinned to Java 25 by the repository.
call "%CD%\gradlew.bat" -PrtsClientInstance=e2eu-client runClient --no-daemon --no-configuration-cache %*
set "RTS_EXIT_CODE=%ERRORLEVEL%"

if not "%RTS_EXIT_CODE%"=="0" (
    echo [RTSBuilding] E2EU runClient failed with exit code %RTS_EXIT_CODE%.
    pause
)
exit /b %RTS_EXIT_CODE%
