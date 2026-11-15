@echo off
title LarpersCoin - Panel distant
where cloudflared >nul 2>nul
if errorlevel 1 (
  echo cloudflared n'est pas installe.
  echo Installe-le avec: winget install --id Cloudflare.cloudflared -e
  pause
  exit /b 1
)
echo.
echo Verification du panneau local sur http://127.0.0.1:8765 ...
powershell -NoProfile -ExecutionPolicy Bypass -Command "try { $r=Invoke-WebRequest -UseBasicParsing -TimeoutSec 4 http://127.0.0.1:8765/; if ($r.StatusCode -ne 200) { exit 1 } } catch { exit 1 }"
if errorlevel 1 (
  echo ERREUR: le panneau ne repond pas.
  echo Lance d'abord START-NETWORK.bat et verifie le journal SMP/logs/latest.log.
  echo Le panneau est demarre uniquement par le serveur SMP.
  pause
  exit /b 1
)
echo Panneau actif. Le tunnel HTTPS va afficher une adresse publique temporaire.
echo Garde cette fenetre ouverte pendant toute la duree d'utilisation.
echo.
cloudflared tunnel --url http://127.0.0.1:8765
pause
