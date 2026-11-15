$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$servers = @(
    @{ Name = "Lobby"; Dir = "Lobby"; Jar = "paper.jar"; Heap = "-Xms512M -Xmx1G"; Nogui = $true },
    @{ Name = "SMP"; Dir = "SMP"; Jar = "paper.jar"; Heap = "-Xms1G -Xmx4G"; Nogui = $true },
    @{ Name = "Velocity"; Dir = "Velocity"; Jar = "velocity.jar"; Heap = "-Xms512M -Xmx1G"; Nogui = $false }
)
$processes = @{}

function Start-One($server) {
    $folder = Join-Path $root $server.Dir
    $jar = Join-Path $folder $server.Jar
    if (!(Test-Path $jar)) { throw "JAR introuvable: $jar" }
    $logDir = Join-Path $folder "logs"
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    $log = Join-Path $logDir "superviseur-console.log"
    $nogui = if ($server.Nogui) { " --nogui" } else { "" }

    # Lancer Java directement (pas cmd.exe) afin que le PID, stdin et l'arret
    # correspondent bien au vrai serveur Minecraft.
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = "java"
    $psi.Arguments = $server.Heap + ' -jar "' + $jar + '"' + $nogui
    $psi.WorkingDirectory = $folder
    $psi.UseShellExecute = $false
    $psi.RedirectStandardInput = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.CreateNoWindow = $true

    $proc = New-Object System.Diagnostics.Process
    $proc.StartInfo = $psi
    $logPath = $log
    $outBlock = {
        param($sender, $eventArgs)
        if ($null -ne $eventArgs.Data) {
            [System.IO.File]::AppendAllText($logPath, $eventArgs.Data + [Environment]::NewLine)
        }
    }.GetNewClosure()
    $errBlock = {
        param($sender, $eventArgs)
        if ($null -ne $eventArgs.Data) {
            [System.IO.File]::AppendAllText($logPath, "[ERREUR] " + $eventArgs.Data + [Environment]::NewLine)
        }
    }.GetNewClosure()
    $outHandler = [System.Diagnostics.DataReceivedEventHandler]$outBlock
    $errHandler = [System.Diagnostics.DataReceivedEventHandler]$errBlock
    $proc.add_OutputDataReceived($outHandler)
    $proc.add_ErrorDataReceived($errHandler)

    [void]$proc.Start()
    $proc.BeginOutputReadLine()
    $proc.BeginErrorReadLine()
    Write-Host ("Demarrage {0} (PID Java {1})" -f $server.Name, $proc.Id)
    return ,$proc
}

function Wait-ServerReady($serverName, $timeoutSeconds = 240) {
    $proc = $processes[$serverName]
    $log = Join-Path (Join-Path $root $serverName) "logs\superviseur-console.log"
    $deadline = (Get-Date).AddSeconds($timeoutSeconds)
    Write-Host ("Attente du demarrage complet de {0}..." -f $serverName)
    while ((Get-Date) -lt $deadline) {
        if ($null -eq $proc -or $proc.HasExited) {
            Write-Warning ("{0} s'est arrete avant d'etre pret." -f $serverName)
            if (Test-Path $log) {
                Get-Content $log -Tail 25 | ForEach-Object { Write-Host $_ }
            }
            return $false
        }
        if (Test-Path $log) {
            if (Select-String -Path $log -Pattern "Done \(" -Quiet -ErrorAction SilentlyContinue) {
                Write-Host ("{0} est pret." -f $serverName)
                return $true
            }
        }
        Start-Sleep -Seconds 2
    }
    Write-Warning ("Delai depasse en attendant {0}. Consulte {1}" -f $serverName, $log)
    return $false
}

function Send-LuckPermsSetup {
    $smpProc = $processes["SMP"]
    if ($null -eq $smpProc -or $smpProc.HasExited) {
        Write-Warning "SMP indisponible: groupes LuckPerms non configures pour ce demarrage."
        return
    }
    $luckPermsCommands = @(
        "lp creategroup joueur",
        "lp group default parent add joueur",
        "lp creategroup vip",
        "lp group vip parent add joueur",
        "lp group vip permission set larpers.vip true",
        "lp creategroup moderateur",
        "lp group moderateur parent add joueur",
        "lp group moderateur permission set larpers.staff true",
        "lp group moderateur permission set larpers.moderation true",
        "lp group moderateur permission set larpers.anticheat.mod true",
        "lp group moderateur permission set larpers.anticheat.use true",
        "lp group moderateur permission set larpers.anticheat.alerts true",
        "lp creategroup admin",
        "lp group admin parent add moderateur",
        "lp group admin permission set larpers.admin true",
        "lp group admin permission set larpers.economy.alerts true",
        "lp group admin permission set larpers.chat.bypass true",
        "lp group admin permission set larpers.anticheat.bypass true",
        "lp creategroup fondateur",
        "lp group fondateur parent add admin",
        "lp user 12felix122 parent set admin"
    )
    foreach ($command in $luckPermsCommands) {
        if ($smpProc.HasExited) {
            Write-Warning "SMP s'est arrete pendant la configuration LuckPerms."
            return
        }
        try {
            $smpProc.StandardInput.WriteLine($command)
            $smpProc.StandardInput.Flush()
            Start-Sleep -Milliseconds 500
        } catch {
            Write-Warning ("Commande LuckPerms non envoyee: " + $command + " — " + $_.Exception.Message)
        }
    }
    Write-Host "Groupes LuckPerms configures via la vraie console SMP. Voir SMP/logs/superviseur-console.log."
}

function Stop-All {
    foreach ($server in $servers) {
        $proc = $processes[$server.Name]
        if ($null -ne $proc -and !$proc.HasExited) {
            try { $proc.StandardInput.WriteLine("stop"); $proc.StandardInput.Flush() } catch {}
        }
    }
    $deadline = (Get-Date).AddSeconds(60)
    do {
        $alive = @($processes.Values | Where-Object { $null -ne $_ -and !$_.HasExited })
        if ($alive.Count -eq 0) { break }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    foreach ($proc in $processes.Values) {
        if ($null -ne $proc) {
            if (!$proc.HasExited) { try { $proc.Kill() } catch {} }
            try { $proc.Dispose() } catch {}
        }
    }
    $processes.Clear()
}

try {
    while ($true) {
        foreach ($server in $servers) {
            $processes[$server.Name] = Start-One $server
            Start-Sleep -Seconds 5
        }

        # Attendre la fin du chargement Paper avant d'envoyer des commandes console.
        $smpReady = Wait-ServerReady "SMP" 240
        if ($smpReady) {
            Start-Sleep -Seconds 3
            Send-LuckPermsSetup
        } else {
            Write-Warning "Configuration LuckPerms ignoree; le superviseur continue pour afficher les erreurs du SMP."
        }

        Write-Host "Reseau actif. Ctrl+C arrete le superviseur."
        $restart = $false
        while (!$restart) {
            $request = Join-Path $root "restart.request"
            if (Test-Path $request) {
                Remove-Item $request -Force
                Write-Host "Commande /redemarrer recue."
                $restart = $true
                break
            }
            foreach ($server in $servers) {
                $proc = $processes[$server.Name]
                if ($null -eq $proc -or $proc.HasExited) {
                    Write-Warning ($server.Name + " s'est arrete; redemarrage complet.")
                    $restart = $true
                    break
                }
            }
            Start-Sleep -Seconds 1
        }
        Stop-All
        Write-Host "Tous les serveurs sont arretes. Redemarrage dans 5 secondes..."
        Start-Sleep -Seconds 5
    }
}
finally {
    Stop-All
}