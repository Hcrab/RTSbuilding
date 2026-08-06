@echo off
setlocal EnableExtensions
chcp 65001 >nul

rem Always run from the 1.19.2 project directory beside this script.
cd /d "%~dp0"

set "RTS_JAVA_HOME="

rem Prefer the verified Microsoft JDK, then search common Java 21 locations.
if exist "%ProgramFiles%\Microsoft\jdk-21.0.11.10-hotspot\bin\java.exe" (
    set "RTS_JAVA_HOME=%ProgramFiles%\Microsoft\jdk-21.0.11.10-hotspot"
)

if not defined RTS_JAVA_HOME (
    for /d %%D in ("%ProgramFiles%\Microsoft\jdk-21*") do (
        if exist "%%~fD\bin\java.exe" set "RTS_JAVA_HOME=%%~fD"
    )
)

if not defined RTS_JAVA_HOME (
    for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\jdk-21*") do (
        if exist "%%~fD\bin\java.exe" set "RTS_JAVA_HOME=%%~fD"
    )
)

rem Fall back to an explicitly configured and valid JAVA_HOME.
if not defined RTS_JAVA_HOME if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" (
    set "RTS_JAVA_HOME=%JAVA_HOME%"
)

if not defined RTS_JAVA_HOME goto missingJava

set "JAVA_HOME=%RTS_JAVA_HOME%"
set "PATH=%JAVA_HOME%\bin;%PATH%"

rem Reuse the sibling Forge cache when this development workspace provides it.
if not defined GRADLE_USER_HOME if exist "%~dp0..\RTSbuilding-1.20.1-forge\.gradle-user-home" (
    for %%D in ("%~dp0..\RTSbuilding-1.20.1-forge\.gradle-user-home") do set "GRADLE_USER_HOME=%%~fD"
)

echo [RTSBuilding] Minecraft 1.19.2 Forge client
echo [RTSBuilding] JAVA_HOME=%JAVA_HOME%
if defined GRADLE_USER_HOME echo [RTSBuilding] GRADLE_USER_HOME=%GRADLE_USER_HOME%
echo.

call "%~dp0gradlew.bat" runClient --no-daemon --no-configuration-cache %*
set "RTS_EXIT_CODE=%ERRORLEVEL%"

if not "%RTS_EXIT_CODE%"=="0" (
    echo.
    echo [RTSBuilding] Client launch failed with exit code %RTS_EXIT_CODE%.
    pause
)

exit /b %RTS_EXIT_CODE%

:missingJava
echo [RTSBuilding] Java 21 was not found.
echo Install Java 21 or set JAVA_HOME to a valid JDK directory, then run this file again.
pause
exit /b 1
