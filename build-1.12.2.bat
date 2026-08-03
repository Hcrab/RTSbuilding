@echo off
setlocal
cd /d "%~dp0"

echo [RTSBuilding] Building the Forge 1.12.2 release JAR and running the real client-world smoke...
echo [RTSBuilding] Gradle Daemon JVM is pinned to Java 25 by the repository.
call "%CD%\gradlew.bat" build clientCheck --no-daemon --no-configuration-cache %*
set "RTS_EXIT_CODE=%ERRORLEVEL%"

if not "%RTS_EXIT_CODE%"=="0" pause
exit /b %RTS_EXIT_CODE%
