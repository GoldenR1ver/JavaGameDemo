@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

rem ============================================================
rem  Hulu Battle - single launcher for Windows
rem
rem  Interactive:   run.bat
rem  Direct usage:  run.bat server [port]
rem                 run.bat client [host] [port]
rem                 run.bat replay [file]
rem                 run.bat test
rem ============================================================

set "PORT=7776"
set "MAIN=com.gameengine.example.Game"
set "REPLAY=com.gameengine.example.ReplayLauncher"

where mvn >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Maven was not found in PATH.
    echo         Install Maven 3.8+ and a JDK 11+, then try again.
    pause
    exit /b 1
)

if /i "%~1"=="server" goto :run_server
if /i "%~1"=="client" goto :run_client
if /i "%~1"=="replay" goto :run_replay
if /i "%~1"=="test"   goto :run_test

:menu
cls
echo.
echo   ==============================================
echo      HULU BATTLE  -  launcher
echo   ==============================================
echo      1  Single player
echo      2  Host a server
echo      3  Join a server
echo      4  Host + join   (two windows, local demo)
echo      5  Replay latest match
echo      6  Run tests
echo      0  Exit
echo   ==============================================
echo.
set "choice="
set /p "choice=  choose ^> "

if "%choice%"=="1" goto :single
if "%choice%"=="2" goto :host
if "%choice%"=="3" goto :join
if "%choice%"=="4" goto :demo
if "%choice%"=="5" goto :replay_menu
if "%choice%"=="6" goto :test
if "%choice%"=="0" exit /b 0
goto :menu

rem ------------------------------------------------------------
rem  actions
rem ------------------------------------------------------------
:single
call :build
if errorlevel 1 goto :build_failed
java -cp "%CP%" %MAIN%
goto :done

:host
call :build
if errorlevel 1 goto :build_failed
echo.
echo   Hosting on port %PORT%. Press Ctrl+C to stop.
echo.
java -cp "%CP%" %MAIN% server 0.0.0.0 %PORT%
goto :done

:join
call :build
if errorlevel 1 goto :build_failed
set "HOST="
set /p "HOST=  server address [127.0.0.1]: "
if "%HOST%"=="" set "HOST=127.0.0.1"
java -cp "%CP%" %MAIN% client %HOST% %PORT%
goto :done

:demo
call :build
if errorlevel 1 goto :build_failed
echo.
echo   Opening a local server and a local client...
start "Hulu Battle - Server" /D "%~dp0" cmd /k run.bat server %PORT%
timeout /t 4 /nobreak >nul
start "Hulu Battle - Client" /D "%~dp0" cmd /k run.bat client 127.0.0.1 %PORT%
echo   Started. Close the two new windows to stop them.
goto :done

:replay_menu
call :build
if errorlevel 1 goto :build_failed
java -cp "%CP%" %REPLAY%
goto :done

:test
call mvn test
goto :done

rem ------------------------------------------------------------
rem  direct argument mode
rem ------------------------------------------------------------
:run_server
if not "%~2"=="" set "PORT=%~2"
call :build
if errorlevel 1 exit /b 1
java -cp "%CP%" %MAIN% server 0.0.0.0 %PORT%
exit /b %errorlevel%

:run_client
set "HOST=%~2"
if "%HOST%"=="" set "HOST=127.0.0.1"
if not "%~3"=="" set "PORT=%~3"
call :build
if errorlevel 1 exit /b 1
java -cp "%CP%" %MAIN% client %HOST% %PORT%
exit /b %errorlevel%

:run_replay
call :build
if errorlevel 1 exit /b 1
java -cp "%CP%" %REPLAY% %~2
exit /b %errorlevel%

:run_test
call mvn test
exit /b %errorlevel%

rem ------------------------------------------------------------
rem  helpers
rem ------------------------------------------------------------
:build
if not exist "target\classes" (
    echo   [build] first run, resolving dependencies...
    call mvn -q compile dependency:build-classpath "-Dmdep.outputFile=target\classpath.txt"
) else (
    call mvn -q compile
    if not exist "target\classpath.txt" (
        call mvn -q dependency:build-classpath "-Dmdep.outputFile=target\classpath.txt"
    )
)
if errorlevel 1 exit /b 1
set /p DEPS=<target\classpath.txt
set "CP=target\classes;%DEPS%"
exit /b 0

:build_failed
echo.
echo   [ERROR] Build failed. See the Maven output above.
pause
goto :menu

:done
echo.
pause
goto :menu
