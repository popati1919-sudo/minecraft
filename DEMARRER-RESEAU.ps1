$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$supervisor = Join-Path $root "SUPERVISEUR-RESEAU.ps1"
if (!(Test-Path $supervisor)) { throw "SUPERVISEUR-RESEAU.ps1 introuvable." }

$secure = Read-Host "Mot de passe du panneau web (20 caracteres minimum)" -AsSecureString
$pointer = [IntPtr]::Zero
try {
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    $plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    if ([string]::IsNullOrWhiteSpace($plain) -or $plain.Length -lt 20) {
        throw "Le mot de passe doit contenir au moins 20 caracteres."
    }
    $env:LARPERS_PANEL_PASSWORD = $plain
    Write-Host "Secret charge en memoire. Demarrage de Lobby, SMP et Velocity..."
    & $supervisor
}
finally {
    Remove-Item Env:\LARPERS_PANEL_PASSWORD -ErrorAction SilentlyContinue
    if ($pointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
    if ($null -ne $secure) { $secure.Dispose() }
    $plain = $null
}
