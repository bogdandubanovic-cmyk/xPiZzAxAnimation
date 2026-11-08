@echo off
chcp 65001 >nul
cd /d "%~dp0"
call gradlew.bat build
if errorlevel 1 (
  echo Build failed.
  pause
  exit /b 1
)
echo Done: build\libs\xPiZzAxAnimation.jar
pause
