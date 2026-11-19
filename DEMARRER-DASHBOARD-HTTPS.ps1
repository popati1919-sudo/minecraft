$ErrorActionPreference = "Stop"
$Host.UI.RawUI.WindowTitle = "Larry SMP - Dashboard HTTPS + Discord"
$cloudflared = Join-Path $PSScriptRoot "cloudflared.exe"
$domain = Read-Host "Domaine du dashboard (ex: dashboard.larry-smp.ca)"
if ([string]::IsNullOrWhiteSpace($domain)) { $domain = "dashboard.larry-smp.ca" }
$domain = $domain.Trim().Replace("https://","").TrimEnd("/")
if ($domain -notmatch '^[a-zA-Z0-9.-]+$') { Write-Host "Domaine invalide." -ForegroundColor Red; pause; exit 1 }
$dashboardUrl = "https://$domain"
Write-Host ""
Write-Host "Colle le TOKEN du tunnel Cloudflare nomme." -ForegroundColor Cyan
Write-Host "Le tunnel doit avoir le hostname $domain -> http://localhost:8765"
$secureTunnel = Read-Host "Cloudflare Tunnel Token" -AsSecureString
Write-Host ""
Write-Host "Colle le NOUVEAU webhook Discord (apres avoir revoque l'ancien)." -ForegroundColor Cyan
$secureWebhook = Read-Host "Discord Webhook URL" -AsSecureString
function Convert-SecureToPlain([Security.SecureString]$secure) {
  $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
  try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
  finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}
$tunnelToken = Convert-SecureToPlain $secureTunnel
$webhook = Convert-SecureToPlain $secureWebhook
if ([string]::IsNullOrWhiteSpace($tunnelToken) -or $tunnelToken.Length -lt 20) { Write-Host "Token Cloudflare manquant/invalide." -ForegroundColor Red; pause; exit 1 }
if ($webhook -notmatch '^https://discord\.com/api/webhooks/\d+/[A-Za-z0-9_-]+$') { Write-Host "Webhook Discord invalide." -ForegroundColor Red; pause; exit 1 }
if (!(Test-Path $cloudflared)) {
  Write-Host "Telechargement de cloudflared..."
  Invoke-WebRequest -UseBasicParsing "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe" -OutFile $cloudflared
}
Write-Host ""
Write-Host "Demarrage du tunnel Cloudflare..." -ForegroundColor Cyan
$proc = Start-Process -FilePath $cloudflared -ArgumentList @("tunnel","run","--token",$tunnelToken) -PassThru -NoNewWindow
$tunnelToken = $null
Start-Sleep -Seconds 8
if ($proc.HasExited) {
  Write-Host "cloudflared s'est ferme. Verifie le token et le hostname dans Cloudflare." -ForegroundColor Red
  $webhook = $null
  pause
  exit 1
}
$payload = @{
  username = "Larry SMP | Dashboard"
  embeds = @(@{
    title = "Dashboard Larry SMP disponible"
    url = $dashboardUrl
    description = "Le tableau de bord est accessible en HTTPS."
    color = 5793266
    fields = @(
      @{ name = "Adresse"; value = "[$domain]($dashboardUrl)"; inline = $true },
      @{ name = "Etat"; value = "Tunnel Cloudflare actif"; inline = $true }
    )
    footer = @{ text = "Larry SMP • Administration" }
    timestamp = (Get-Date).ToUniversalTime().ToString("o")
  })
  allowed_mentions = @{ parse = @() }
} | ConvertTo-Json -Depth 8
try {
  Invoke-RestMethod -Uri $webhook -Method Post -ContentType "application/json" -Body $payload | Out-Null
  Write-Host "Adresse HTTPS envoyee sur Discord: $dashboardUrl" -ForegroundColor Green
} catch {
  Write-Host "Tunnel actif, mais l'envoi Discord a echoue: $($_.Exception.Message)" -ForegroundColor Red
}
$webhook = $null
Write-Host ""
Write-Host "Le tunnel reste actif. CTRL+C pour l'arreter." -ForegroundColor Yellow
while (!$proc.HasExited) { Start-Sleep -Seconds 2 }
