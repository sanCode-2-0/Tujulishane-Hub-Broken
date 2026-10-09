param (
    [switch]$Backend,
    [switch]$Restart
)

$ServerIP = "44.245.151.32"
$KeyPath = "C:\Users\Briane\.ssh\id_rsa"
$User = "ubuntu"

Write-Host "=========================================" -ForegroundColor Yellow
Write-Host " Tujulishane Hub - Deployment Script v2.0" -ForegroundColor Yellow
Write-Host "=========================================" -ForegroundColor Yellow
Write-Host "Target Server: $ServerIP ($User)" -ForegroundColor Gray
Write-Host "SSH Key Path:  $KeyPath" -ForegroundColor Gray
Write-Host "Flags:         Backend=$Backend, Restart=$Restart" -ForegroundColor Gray
Write-Host "-----------------------------------------" -ForegroundColor Gray

# 1. Deploy Frontend (Always deployed)
Write-Host ">>> Starting Frontend Deployment..." -ForegroundColor Cyan
Write-Host "Uploading frontend directory via SCP..." -ForegroundColor Gray

# Removed -q to allow progress monitoring or normal exit feedback
scp -r -i $KeyPath frontend "$($User)@$($ServerIP):/home/ubuntu/"
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Failed to upload frontend files via SCP." -ForegroundColor Red
    exit $LASTEXITCODE
}
Write-Host "Upload completed successfully." -ForegroundColor Green

Write-Host "Configuring frontend files on remote EC2 server..." -ForegroundColor Gray
Write-Host "  - Copying files to /var/www/tujulishane-hub/" -ForegroundColor Gray
Write-Host "  - Setting production base URL (USE_PROD = true) in app-config.js" -ForegroundColor Gray
Write-Host "  - Granting read permissions (chmod 755)" -ForegroundColor Gray

$frontendSSHCommand = "sudo cp -r /home/ubuntu/frontend/* /var/www/tujulishane-hub/ && " +
                      "sudo sed -i 's/const USE_PROD = false;/const USE_PROD = true;/' /var/www/tujulishane-hub/app-config.js && " +
                      "sudo chmod -R 755 /var/www/tujulishane-hub"

ssh -i $KeyPath "$($User)@$($ServerIP)" $frontendSSHCommand
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Remote server commands failed during frontend setup." -ForegroundColor Red
    exit $LASTEXITCODE
}
Write-Host "Frontend files copied and configured successfully." -ForegroundColor Green

# Verify Frontend is serving content
Write-Host "Verifying Frontend HTTP availability..." -ForegroundColor Gray
try {
    $feCheck = Invoke-WebRequest -Uri "http://$ServerIP" -UseBasicParsing -TimeoutSec 5
    if ($feCheck.StatusCode -eq 200) {
        Write-Host "[SUCCESS] Frontend is online and responding with HTTP 200 OK!" -ForegroundColor Green
    } else {
        Write-Host "[WARNING] Frontend responded with unexpected status code: $($feCheck.StatusCode)" -ForegroundColor Yellow
    }
} catch {
    Write-Host "[WARNING] Frontend health check failed to respond: $_" -ForegroundColor Yellow
}

# 2. Build and Deploy Backend (Only if -Backend flag is supplied)
if ($Backend) {
    Write-Host "`n>>> Starting Backend Build & Deployment..." -ForegroundColor Yellow
    Write-Host "Locating local backend project folder..." -ForegroundColor Gray
    
    Push-Location backend
    Write-Host "Running Gradle compile and bootJar package..." -ForegroundColor Gray
    .\gradlew.bat bootJar
    Pop-Location
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] Local Gradle build failed. Check compilation errors above." -ForegroundColor Red
        exit $LASTEXITCODE
    }
    Write-Host "Gradle build completed successfully. Generated app.jar." -ForegroundColor Green
    
    Write-Host "Uploading backend JAR to remote server..." -ForegroundColor Gray
    scp -i $KeyPath backend/build/libs/app.jar "$($User)@$($ServerIP):/home/ubuntu/app.jar"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] Failed to upload JAR file via SCP." -ForegroundColor Red
        exit $LASTEXITCODE
    }
    Write-Host "Backend JAR uploaded successfully." -ForegroundColor Green
    
    Write-Host "Restarting backend systemd service (tujulishane) on EC2..." -ForegroundColor Gray
    ssh -i $KeyPath "$($User)@$($ServerIP)" "sudo systemctl restart tujulishane"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] Failed to restart remote backend systemd service." -ForegroundColor Red
        exit $LASTEXITCODE
    }
    Write-Host "Systemd service restart command sent." -ForegroundColor Green
    
    # Verify Backend Startup
    VerifyBackendStartup
} 
# 3. Just Restart Backend (Only if -Restart flag is supplied)
elseif ($Restart) {
    Write-Host "`n>>> Restarting Backend Service..." -ForegroundColor Yellow
    ssh -i $KeyPath "$($User)@$($ServerIP)" "sudo systemctl restart tujulishane"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] Failed to restart remote backend systemd service." -ForegroundColor Red
        exit $LASTEXITCODE
    }
    Write-Host "Systemd service restart command sent." -ForegroundColor Green
    
    # Verify Backend Startup
    VerifyBackendStartup
}

# Health check verification routine for backend startup
function VerifyBackendStartup {
    Write-Host "Verifying Backend API availability (polling startup status)..." -ForegroundColor Gray
    $backendUrl = "http://$ServerIP:8080/api/thematic-areas"
    $retries = 20
    $delay = 2
    $started = $false
    
    for ($i = 1; $i -le $retries; $i++) {
        Write-Host "  Pinging backend health (Attempt $i/$retries)..." -ForegroundColor Gray
        try {
            # 401 is considered a success because the endpoint exists and responds to HTTP authentication checks
            $beCheck = Invoke-WebRequest -Uri $backendUrl -UseBasicParsing -TimeoutSec 3
            if ($beCheck.StatusCode -eq 200 -or $beCheck.StatusCode -eq 401) {
                Write-Host "[SUCCESS] Backend API is up, running, and responding successfully!" -ForegroundColor Green
                $started = $true
                break
            }
        } catch {
            # Catch block catches non-200 responses. If it's a 401, that means the server is online.
            if ($_.Exception.Response -and ($_.Exception.Response.StatusCode -eq 401 -or $_.Exception.Response.StatusCode -eq 200)) {
                Write-Host "[SUCCESS] Backend API is up, running, and responding successfully!" -ForegroundColor Green
                $started = $true
                break
            }
            # Otherwise, service is still booting up (connection refused/timeout)
        }
        Start-Sleep -Seconds $delay
    }
    
    if (-not $started) {
        Write-Host "[ERROR] Backend service failed to start or respond within $(($retries * $delay)) seconds." -ForegroundColor Red
        exit 1
    }
}

Write-Host "`n=========================================" -ForegroundColor Green
Write-Host " >>> Deployment Completed Successfully! <<<" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green
