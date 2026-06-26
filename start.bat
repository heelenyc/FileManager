@echo off
chcp 65001 >nul
set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8
echo ========================================
echo   FileManager - Start All Services
echo ========================================
echo.

docker info >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Docker is not running, please start Docker Desktop first
    pause
    exit /b 1
)

echo [1/4] Starting Docker containers (MySQL + Redis + Zookeeper)...
cd /d "%~dp0docker"
docker-compose up -d
if %errorlevel% neq 0 (
    echo [ERROR] Docker containers failed to start
    pause
    exit /b 1
)
echo [1/4] Docker containers started
echo.

echo [2/4] Waiting for MySQL to be ready...
timeout /t 10 /nobreak >nul
echo [2/4] MySQL ready
echo.

echo [3/4] Building and starting backend service...
cd /d "%~dp0"
call mvn install -DskipTests
if %errorlevel% neq 0 (
    echo [ERROR] Maven build failed
    pause
    exit /b 1
)
start "FileManager-Backend" cmd /k "chcp 65001 >nul && set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 && mvn spring-boot:run -pl file-manager-web"
echo [3/4] Backend starting on port 8080
echo.

echo [4/4] Starting frontend service...
cd /d "%~dp0file-manager-frontend"
if not exist "node_modules" (
    echo [4/4] First run, installing frontend dependencies...
    call npm install
    if %errorlevel% neq 0 (
        echo [ERROR] npm install failed, please ensure Node.js is installed
        pause
        exit /b 1
    )
)
start "FileManager-Frontend" cmd /k "chcp 65001 >nul && npm run dev"
echo [4/4] Frontend starting on port 5173
echo.

echo ========================================
echo   All services started!
echo   Frontend:  http://localhost:5173
echo   API Docs:  http://localhost:8080/api/doc.html
echo   Account:   admin / admin123
echo ========================================
pause
