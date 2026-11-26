@echo off
cd /d "%~dp0"
echo ============================================================
echo   [onyx - EY.ONYX.PE] One-Click GitHub Cloud APK Builder
echo ============================================================
echo.
set /p REPO_URL="Paste your empty GitHub repository URL: "
if "%REPO_URL%"=="" (
    echo [ERROR] Repository URL cannot be empty.
    pause
    exit /b 1
)
git init
git branch -M main
git add .
git commit -m "onyx EY.ONYX.PE 1:1 UI"
git remote remove origin >nul 2>&1
git remote add origin %REPO_URL%
git push -u origin main --force
echo.
echo [DONE] Pushed to GitHub! Opening GitHub Actions page...
start "" "%REPO_URL:.git=%/actions"
pause
