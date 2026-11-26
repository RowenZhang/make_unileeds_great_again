@echo off
setlocal
chcp 65001 >nul
pushd "%~dp0"
if not defined PORT set "PORT=8080"
set "LF_URL=http://127.0.0.1:%PORT%/lost-found"
if /i "%~1"=="--wait-for-board" goto wait_for_board
set "LF_OPEN=1"
if /i "%~1"=="--no-browser" set "LF_OPEN="
call :is_board
if not errorlevel 1 goto already_running
curl.exe -q --noproxy "*" --silent --max-time 2 --output NUL "%LF_URL%" 2>nul
if not errorlevel 1 (
  call :is_board
  if not errorlevel 1 goto already_running
  echo Another website is using port %PORT%. From PowerShell choose another port, for example:
  echo   $env:PORT='8085'; .\Start.cmd
  if defined LF_OPEN pause
  goto failed
)
set "LFJAVA="
for /d %%D in ("%ProgramFiles%\JetBrains\IntelliJ IDEA*") do if exist "%%~fD\jbr\bin\java.exe" set "LFJAVA=%%~fD\jbr\bin\java.exe"
if not defined LFJAVA if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "LFJAVA=%JAVA_HOME%\bin\java.exe"
if not defined LFJAVA for /d %%D in ("%USERPROFILE%\.jdks\*") do if exist "%%~fD\bin\java.exe" set "LFJAVA=%%~fD\bin\java.exe"
if not defined LFJAVA set "LFJAVA=java"
if not exist "app\lost-found.jar" (
  echo Missing app\lost-found.jar. Build it using the instructions in README.md.
  if defined LF_OPEN pause
  goto failed
)
echo Lost and found: %LF_URL%
echo Leave this window open. Press Ctrl+C to stop the server.
if defined LF_OPEN start "" /b "%ComSpec%" /d /c Start.cmd --wait-for-board
"%LFJAVA%" -jar "app\lost-found.jar"
if errorlevel 1 (
  call :is_board
  if not errorlevel 1 goto already_running
  echo Start failed. See the error above. This app requires Java 17 or newer and a free port.
  if defined LF_OPEN pause
  goto failed
)
goto success

:already_running
echo Already running: %LF_URL%
if defined LF_OPEN start "" "%LF_URL%"
goto success

:is_board
curl.exe -q --noproxy "*" --fail --silent --max-time 2 "%LF_URL%" 2>nul | findstr.exe /R /C:"/lost-found/assets/main[.]css.*/lost-found/assets/main[.]js" >nul
exit /b %errorlevel%

:wait_for_board
for /l %%I in (1,1,25) do (
  call :is_board
  if not errorlevel 1 (
    start "" "%LF_URL%"
    goto success
  )
  ping.exe -n 2 127.0.0.1 >nul
)
:failed
popd
exit /b 1

:success
popd
exit /b 0
