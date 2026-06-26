@echo off
echo ========================================
echo   FileManager - Stop All Services
echo ========================================
echo.

echo [1/2] Stopping backend and frontend...
taskkill /fi "WINDOWTITLE eq FileManager-Backend*" /f >nul 2>&1
taskkill /fi "WINDOWTITLE eq FileManager-Frontend*" /f >nul 2>&1
echo [1/2] Backend and frontend stopped
echo.

echo [2/2] Stopping Docker containers...
cd /d "%~dp0docker"
docker-compose down
echo [2/2] Docker containers stopped
echo.

echo ========================================
echo   All services stopped
echo ========================================
pause
