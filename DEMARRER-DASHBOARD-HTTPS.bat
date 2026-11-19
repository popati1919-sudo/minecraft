@echo off
setlocal
cd /d "%~dp0"
echo ==============================================
echo Larry SMP - Dashboard HTTPS + notification Discord
echo ==============================================
echo.
if not exist "DEMARRER-DASHBOARD-HTTPS.ps1" (
  echo ERREUR: fichier DEMARRER-DASHBOARD-HTTPS.ps1 introuvable.
  pause
  exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0DEMARRER-DASHBOARD-HTTPS.ps1"
pause
