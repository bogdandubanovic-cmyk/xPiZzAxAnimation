@echo off
setlocal
cd /d "%~dp0"

echo ================================================
echo   xPiZzAxAnimation - upload source to GitHub
echo ================================================
echo.

where git >nul 2>nul
if errorlevel 1 (
  echo Git for Windows is not installed.
  echo Install it from https://git-scm.com/download/win and run this file again.
  pause
  exit /b 1
)

if not exist ".git" (
  git init || goto :error
)

git remote get-url origin >nul 2>nul
if errorlevel 1 (
  git remote add origin https://github.com/bogdandubanovic-cmyk/xPiZzAxAnimation.git || goto :error
) else (
  git remote set-url origin https://github.com/bogdandubanovic-cmyk/xPiZzAxAnimation.git || goto :error
)

git branch -M main || goto :error

rem Keep commit identity local to this project (no global Git settings changed).
git config user.name "xPiZzAx"
git config user.email "256457627+bogdandubanovic-cmyk@users.noreply.github.com"
git add -A || goto :error
git diff --cached --quiet
if errorlevel 1 (
  git commit -m "Add xPiZzAxAnimation source" || goto :error
) else (
  echo No new source changes to commit.
)

echo.
echo GitHub may open a browser or ask you to sign in.
git push -u origin main
if errorlevel 1 goto :error

echo.
echo Source uploaded successfully.
echo GitHub Actions will now build the mod.
echo Check: https://github.com/bogdandubanovic-cmyk/xPiZzAxAnimation/actions
pause
exit /b 0

:error
echo.
echo Upload failed. Check your internet connection and GitHub sign-in.
pause
exit /b 1
