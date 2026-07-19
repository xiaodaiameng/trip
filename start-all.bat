@echo off
setlocal EnableExtensions
title Scenic AI - Start All

set "ROOT=%~dp0"
set "SERVER_DIR=%ROOT%scenic-ai-server"
set "WEB_DIR=%ROOT%scenic-ai-web"
set "MOBILE_DIR=%WEB_DIR%\mobile"

echo [Scenic AI] Starting full development stack...
echo Root: %ROOT%
echo.

call :require java.exe "Java"
if errorlevel 1 goto :fail

call :require mvn.cmd "Maven"
if errorlevel 1 goto :fail

call :require npm.cmd "Node.js/npm"
if errorlevel 1 goto :fail

if not exist "%SERVER_DIR%\pom.xml" (
  echo [ERROR] Backend project not found: %SERVER_DIR%
  goto :fail
)

if not exist "%WEB_DIR%\package.json" (
  echo [ERROR] Desktop web project not found: %WEB_DIR%
  goto :fail
)

if not exist "%MOBILE_DIR%\package.json" (
  echo [ERROR] Mobile web project not found: %MOBILE_DIR%
  goto :fail
)

if not exist "%WEB_DIR%\node_modules" (
  call :npm_install "%WEB_DIR%" "desktop web"
  if errorlevel 1 goto :fail
)

if not exist "%MOBILE_DIR%\node_modules" (
  call :npm_install "%MOBILE_DIR%" "mobile web"
  if errorlevel 1 goto :fail
)

if not exist "%SERVER_DIR%\data" mkdir "%SERVER_DIR%\data"

rem Local demo defaults. Pre-set these variables before running if you want MySQL or custom credentials.
if not defined SERVER_PORT set "SERVER_PORT=8080"
if not defined SPRING_PROFILES_ACTIVE set "SPRING_PROFILES_ACTIVE=dev"
if not defined SPRING_H2_CONSOLE_ENABLED set "SPRING_H2_CONSOLE_ENABLED=true"
if not defined DB_DRIVER set "DB_DRIVER=org.h2.Driver"
if not defined DB_URL set "DB_URL=jdbc:h2:file:%SERVER_DIR%\data\scenic-ai-mvp;MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE"
if not defined DB_USERNAME set "DB_USERNAME=sa"
if not defined DB_PASSWORD set "DB_PASSWORD="
if not defined JPA_DDL_AUTO set "JPA_DDL_AUTO=update"
if not defined ADMIN_USERNAME set "ADMIN_USERNAME=admin"
if not defined ADMIN_PASSWORD_HASH set "ADMIN_PASSWORD_HASH=$2b$10$/HnWT2AwFaRF1PYJN41kVOM8lesbvqdeuaAwpXiytKO07j.yGSo4W"
if not defined ADMIN_DISPLAY_NAME set "ADMIN_DISPLAY_NAME=Scenic AI Admin"
if not defined JWT_SECRET set "JWT_SECRET=scenic-ai-local-demo-jwt-secret-please-change"
if not defined VITE_BACKEND_TARGET set "VITE_BACKEND_TARGET=http://127.0.0.1:8080"

if /i "%~1"=="--check" (
  echo Check passed. Use start-all.bat to launch the services.
  exit /b 0
)

start "Scenic AI Backend 8080" cmd /k "cd /d ""%SERVER_DIR%"" && mvn.cmd spring-boot:run"
start "Scenic AI Desktop Web 5173" cmd /k "cd /d ""%WEB_DIR%"" && npm.cmd run dev -- --host 0.0.0.0 --port 5173"
start "Scenic AI Mobile Web 5174" cmd /k "cd /d ""%MOBILE_DIR%"" && npm.cmd run dev"

set "LOCAL_IP="
for /f "tokens=2 delims=:" %%A in ('ipconfig ^| findstr /i "IPv4"') do if not defined LOCAL_IP set "LOCAL_IP=%%A"
set "LOCAL_IP=%LOCAL_IP: =%"

echo.
echo Services are starting in separate windows.
echo Backend health: http://localhost:8080/actuator/health
echo Desktop web:    http://localhost:5173
echo Mobile web:     http://localhost:5174
if defined LOCAL_IP echo Phone access:    http://%LOCAL_IP%:5174
echo.
echo Admin login:    admin / admin123
echo.
echo Close the three service windows to stop everything.
echo.
pause
exit /b 0

:require
where %~1 >nul 2>nul
if errorlevel 1 (
  echo [ERROR] %~2 was not found in PATH.
  exit /b 1
)
exit /b 0

:npm_install
echo Installing %~2 dependencies...
pushd "%~1"
call npm.cmd install
set "NPM_STATUS=%ERRORLEVEL%"
popd
if not "%NPM_STATUS%"=="0" (
  echo [ERROR] npm install failed for %~2.
  exit /b %NPM_STATUS%
)
exit /b 0

:fail
echo.
echo Startup aborted.
pause
exit /b 1
