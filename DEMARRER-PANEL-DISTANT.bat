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
echo Le panel local doit etre actif sur http://127.0.0.1:8765
echo Le tunnel HTTPS va afficher une adresse publique temporaire.
echo Garde cette fenetre ouverte pendant toute la duree d'utilisation.
echo.
cloudflared tunnel --url http://127.0.0.1:8765
pause
