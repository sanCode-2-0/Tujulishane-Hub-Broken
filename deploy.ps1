param (
    [switch]$Backend,
    [switch]$Restart
)

$ServerIP = "44.245.151.32"
$KeyPath = "C:\Users\Briane\.ssh\id_rsa"
$User = "ubuntu"

# 1. Always Deploy Frontend (Takes ~2 seconds, no compile)
Write-Host ">>> Deploying Frontend Static Files..." -ForegroundColor Cyan
scp -q -r -i $KeyPath frontend "$($User)@$($ServerIP):/home/ubuntu/"
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to upload frontend files."
    exit $LASTEXITCODE
}

# Move frontend to Nginx folder, enable production mode, and set permissions
ssh -i $KeyPath "$($User)@$($ServerIP)" "sudo cp -r /home/ubuntu/frontend/* /var/www/tujulishane-hub/ && sudo sed -i 's/const USE_PROD = false;/const USE_PROD = true;/' /var/www/tujulishane-hub/app-config.js && sudo chmod -R 755 /var/www/tujulishane-hub"
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to copy and configure frontend files."
    exit $LASTEXITCODE
}

# 2. Build and Deploy Backend (Only if -Backend flag is supplied)
if ($Backend) {
    Write-Host ">>> Building Backend Locally..." -ForegroundColor Yellow
    
    # Move to backend directory and compile
    Push-Location backend
    .\gradlew.bat bootJar
    Pop-Location
    
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Gradle build failed."
        exit $LASTEXITCODE
    }
    
    Write-Host ">>> Uploading backend JAR to EC2..." -ForegroundColor Cyan
    scp -i $KeyPath backend/build/libs/app.jar "$($User)@$($ServerIP):/home/ubuntu/app.jar"
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to upload JAR file."
        exit $LASTEXITCODE
    }
    
    Write-Host ">>> Restarting backend service on EC2..." -ForegroundColor Green
    ssh -i $KeyPath "$($User)@$($ServerIP)" "sudo systemctl restart tujulishane"
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to restart backend service."
        exit $LASTEXITCODE
    }
} 
# 3. Just Restart Backend (Only if -Restart flag is supplied)
elseif ($Restart) {
    Write-Host ">>> Restarting backend service on EC2..." -ForegroundColor Green
    ssh -i $KeyPath "$($User)@$($ServerIP)" "sudo systemctl restart tujulishane"
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to restart backend service."
        exit $LASTEXITCODE
    }
}

Write-Host ">>> Deployment Successful!" -ForegroundColor Green
