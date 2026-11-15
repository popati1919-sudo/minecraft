$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$supervisor = Join-Path $root "SUPERVISEUR-RESEAU.ps1"
if (!(Test-Path $supervisor)) { throw "SUPERVISEUR-RESEAU.ps1 introuvable." }

# DPAPI chiffre le secret pour le compte Windows courant. Le fichier ne contient
# jamais le mot de passe en clair et ne peut pas etre dechiffre par un autre compte.
$secretDir = Join-Path $env:LOCALAPPDATA "LarpersCoin"
$secretFile = Join-Path $secretDir "panel-password.dpapi"
New-Item -ItemType Directory -Force -Path $secretDir | Out-Null

$secure = $null
$pointer = [IntPtr]::Zero
$plain = $null
try {
    if (Test-Path $secretFile) {
        $protected = (Get-Content -Raw -Path $secretFile).Trim()
        if ([string]::IsNullOrWhiteSpace($protected)) {
            throw "Le fichier de secret protege est vide. Supprime-le pour definir un nouveau mot de passe."
        }
        $secure = ConvertTo-SecureString -String $protected
        Write-Host "Secret du panneau charge depuis le stockage chiffre Windows."
    } else {
        $secure = Read-Host "Mot de passe du panneau web (20 caracteres minimum) - demande unique" -AsSecureString
        $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        $plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
        if ([string]::IsNullOrWhiteSpace($plain) -or $plain.Length -lt 20) {
            throw "Le mot de passe doit contenir au moins 20 caracteres."
        }
        $protected = ConvertFrom-SecureString -SecureString $secure
        Set-Content -Path $secretFile -Value $protected -Encoding ASCII -NoNewline
        try { attrib.exe +h $secretFile | Out-Null } catch {}
        Write-Host "Mot de passe enregistre chiffre pour ce compte Windows; il ne sera plus redemande."
    }

    if ($pointer -eq [IntPtr]::Zero) {
        $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        $plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
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