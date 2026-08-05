# PostgreSQL Database Administration Guide

This guide details how to perform database operations, secure passwords, establish tunnels, and manage database backups for the Tujulishane Hub EC2 database.

---

## 💾 Backing Up the Database Locally

Since the database runs on EC2, backup creation involves dumping the DB on the server and copying it to your local machine.

### Step 1: Create the Dump File on EC2
Run this in your EC2 terminal (it writes to the public `/tmp` directory first to bypass permissions, then moves it to your home directory):
```bash
# 1. Dump the database to the /tmp folder
sudo -u postgres pg_dump -F c -b -v -f /tmp/tujulishane_backup.dump tujulishane_hub

# 2. Move the file to your home directory
sudo mv /tmp/tujulishane_backup.dump /home/briane/

# 3. Change ownership so you can download it
sudo chown briane:briane /home/briane/tujulishane_backup.dump
sudo chmod 600 /home/briane/tujulishane_backup.dump
```

### Step 2: Download to your Local Computer
Run this on your **local computer's terminal (PowerShell / CMD)**:
```powershell
scp -i C:\Users\Briane\.ssh\id_rsa briane@44.245.151.32:/home/briane/tujulishane_backup.dump C:\Users\Briane\Documents\
```

---

## 🔒 Accessing the Database Securely (SSH Tunneling)

Do **not** open PostgreSQL port `5432` to the public internet. Instead, use an SSH tunnel to connect safely.

### Step 1: Open the Tunnel
Run this in a **local PowerShell window** and keep it open:
```powershell
ssh -i C:\Users\Briane\.ssh\id_rsa -L 5433:127.0.0.1:5432 briane@44.245.151.32
```

### Step 2: Connect via GUI Client (DBeaver / pgAdmin)
While the tunnel is running, configure your database client on your local computer with:
* **Host**: `localhost` (or `127.0.0.1`)
* **Port**: `5433` (tunnels to remote `5432`)
* **Database**: `tujulishane_hub`
* **Username**: `tujulishane` (or `postgres` if admin)
* **Password**: *[Your Password]*

---

## 🔑 Changing PostgreSQL User Passwords

Change user passwords to ensure administrative access remains confidential.

### Step 1: Run psql on EC2
Access the interactive database shell as the PostgreSQL superuser:
```bash
sudo -u postgres psql
```

### Step 2: Update User Passwords
```sql
-- Change password for the postgres administrator
ALTER USER postgres WITH PASSWORD 'your_new_secure_admin_password';

-- Change password for the application database user
ALTER USER tujulishane WITH PASSWORD 'your_new_secure_app_password';

-- Exit the shell
\q
```

### Step 3: Update Spring Boot Service Environment Variables
If you changed the `tujulishane` app database user's password, you must update the backend configuration:

1. Open the systemd service file:
   ```bash
   sudo nano /etc/systemd/system/tujulishane.service
   ```
2. Update the environment variable setting:
   ```ini
   Environment="SPRING_DATASOURCE_PASSWORD=your_new_secure_app_password"
   ```
3. Reload systemd and restart the backend:
   ```bash
   sudo systemctl daemon-reload
   sudo systemctl restart tujulishane
   ```
