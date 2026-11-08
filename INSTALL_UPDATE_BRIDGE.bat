@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0INSTALL_UPDATE_BRIDGE.ps1" -ModsDirectory "%~dp0"
if errorlevel 1 (
  echo.
  echo Update failed. The old mod files were kept.
  pause
  exit /b 1
)
echo.
echo xPiZzAxAnimation updated successfully.
pause
