# Connection Steps

### Deploy Frontend Changes (Local Terminal)
```powershell
.\deploy.ps1
```

### Deploy Backend Changes (Local Terminal)
```powershell
.\deploy.ps1 -Backend
```

### Restart Backend Only (Local Terminal)
```powershell
.\deploy.ps1 -Restart
```

### Connect to EC2 Server via SSH (Local Terminal)
```powershell
ssh -i C:\Users\Briane\.ssh\id_rsa briane@44.245.151.32
```

### Open SSH Database Tunnel (Local Terminal)
Keep this terminal window running to connect via local database tools (host: `localhost`, port: `5433`):
```powershell
ssh -i C:\Users\Briane\.ssh\id_rsa -L 5433:127.0.0.1:5432 briane@44.245.151.32
```

### Download Database Backup (Local Terminal)
Run this after creating the dump on the EC2 server:
```powershell
scp -i C:\Users\Briane\.ssh\id_rsa briane@44.245.151.32:/home/briane/tujulishane_backup.dump C:\Users\Briane\Documents\
```
