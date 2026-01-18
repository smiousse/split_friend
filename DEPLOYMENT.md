# SplitFriend Deployment Guide for Portainer

This guide explains how to deploy SplitFriend in a Docker container using Portainer.

## Prerequisites

- Portainer installed and running
- Docker Hub account (or another container registry)
- Git repository accessible from your build machine

## Step 1: Build and Push the Docker Image

### Option A: Build Locally and Push to Docker Hub

```bash
# Clone the repository
git clone https://github.com/smiousse/split_friend.git
cd split_friend

# Build the Docker image
docker build -t smiousse/splitfriend:latest .

# Tag with version (optional but recommended)
docker tag smiousse/splitfriend:latest smiousse/splitfriend:1.0.0

# Login to Docker Hub
docker login

# Push to Docker Hub
docker push smiousse/splitfriend:latest
docker push smiousse/splitfriend:1.0.0
```

### Option B: Use Portainer's Git Integration (if available)

Portainer Business Edition can build directly from a Git repository. Skip to Step 2 if using this method.

## Step 2: Deploy in Portainer

### Method 1: Using Stacks (Recommended)

1. Log in to Portainer
2. Select your environment (local Docker or remote)
3. Go to **Stacks** → **Add stack**
4. Name the stack: `splitfriend`
5. Choose **Web editor** and paste this docker-compose configuration:

```yaml
services:
  splitfriend:
    image: smiousse/splitfriend:latest
    container_name: splitfriend
    network_mode: host
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - DB_PASSWORD=your_secure_db_password
      - ADMIN_PASSWORD=your_secure_admin_password
      - SERVER_PORT=8080
      - BACKUP_AUTO_ENABLED=true
      - BACKUP_MAX_FILES=10
      - BACKUP_CRON=0 0 2 * * ?
    volumes:
      - splitfriend_data:/app/data
      - splitfriend_uploads:/app/uploads
      - splitfriend_backups:/app/backups
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:8080/login"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s

volumes:
  splitfriend_data:
  splitfriend_uploads:
  splitfriend_backups:
```

6. Scroll down to **Environment variables** and add:
   - `DB_PASSWORD`: A secure password for the H2 database
   - `ADMIN_PASSWORD`: A secure password for the admin account

7. Click **Deploy the stack**

### Method 2: Using Containers (Manual)

1. Go to **Volumes** → **Add volume**
2. Create three volumes:
   - `splitfriend_data`
   - `splitfriend_uploads`
   - `splitfriend_backups`

3. Go to **Containers** → **Add container**
4. Configure:
   - **Name**: `splitfriend`
   - **Image**: `smiousse/splitfriend:latest`

5. Under **Network ports configuration**:
   - Host: `8080` → Container: `8080`

6. Under **Advanced container settings** → **Volumes**:
   - Volume: `splitfriend_data` → Container: `/app/data`
   - Volume: `splitfriend_uploads` → Container: `/app/uploads`
   - Volume: `splitfriend_backups` → Container: `/app/backups`

7. Under **Advanced container settings** → **Env**:
   | Name | Value |
   |------|-------|
   | SPRING_PROFILES_ACTIVE | docker |
   | DB_PASSWORD | your_secure_db_password |
   | ADMIN_PASSWORD | your_secure_admin_password |
   | SERVER_PORT | 8080 |
   | BACKUP_AUTO_ENABLED | true |
   | BACKUP_MAX_FILES | 10 |

8. Under **Restart policy**: Select **Unless stopped**

9. Click **Deploy the container**

## Step 3: Verify Deployment

1. In Portainer, check the container status shows **running** (green)
2. Wait for the health check to pass (may take up to 60 seconds on first start)
3. Access the application at: `http://your-server-ip:8080`
4. Log in with:
   - Email: `admin@splitfriend.local`
   - Password: The `ADMIN_PASSWORD` you configured

## Step 4: Post-Deployment Setup

1. **Change admin email**: Go to Profile and update your email
2. **Enable 2FA**: Go to 2FA Settings and scan the QR code
3. **Create a backup**: Go to Admin → Backup & Restore → Create Backup
4. **Create users**: Go to Admin → Manage Users → Create User

## Environment Variables Reference

| Variable | Default | Description |
|----------|---------|-------------|
| SERVER_PORT | 8080 | Application port |
| DB_PASSWORD | splitfriend_secret | H2 database password |
| ADMIN_PASSWORD | admin123 | Default admin password |
| BACKUP_AUTO_ENABLED | true | Enable automatic daily backups |
| BACKUP_MAX_FILES | 10 | Number of backup files to retain |
| BACKUP_CRON | 0 0 2 * * ? | Backup schedule (default: 2:00 AM daily) |

## Updating the Application

### Using Stacks

1. Build and push new image: `docker push smiousse/splitfriend:latest`
2. In Portainer, go to **Stacks** → **splitfriend**
3. Click **Pull and redeploy**

### Using Containers

1. Build and push new image
2. In Portainer, go to **Containers** → **splitfriend**
3. Click **Recreate** → Enable **Pull latest image** → **Recreate**

## Backup and Restore

### Automatic Backups
Backups are stored in the `splitfriend_backups` volume at 2:00 AM daily (configurable via `BACKUP_CRON`).

### Manual Backup via UI
1. Log in as admin
2. Go to Admin → Backup & Restore
3. Click **Create & Download Backup**

### Accessing Backup Files
```bash
# Find the volume path
docker volume inspect splitfriend_backups

# List backups
docker exec splitfriend ls -la /app/backups
```

## Troubleshooting

### Container won't start
```bash
# Check container logs in Portainer or via CLI
docker logs splitfriend
```

### Health check failing
- The app takes up to 60 seconds to start
- Check if port 8080 is available
- Verify environment variables are set correctly

### "manifest unknown" error when pulling image

This error means the image doesn't exist in the container registry.

**Option 1: Build instead of pull (easiest)**

Modify your compose file to only build from source:

```yaml
services:
  splitfriend:
    build:
      context: https://github.com/smiousse/split_friend.git
      dockerfile: Dockerfile
    # Remove or comment out the image line:
    # image: ghcr.io/smiousse/split_friend:latest
```

**Option 2: Push the image to ghcr.io first**

On your local machine where you have the code:

```bash
# Login to GitHub Container Registry
echo $GITHUB_TOKEN | docker login ghcr.io -u smiousse --password-stdin

# Build the image
docker build -t ghcr.io/smiousse/split_friend:latest .

# Push it
docker push ghcr.io/smiousse/split_friend:latest
```

### Database reset / Can't login with configured credentials

If the database already exists with old credentials, you need to reset it.

**Option 1: Remove just the data volume (CLI)**
```bash
# Stop the container
docker stop split_friend

# Remove the data volume (this deletes the database)
docker volume rm splitfriend_data

# Restart the container (will recreate the volume and admin user)
docker start split_friend
```

**Option 2: Full reset with docker compose**
```bash
# Stop and remove the container and all its volumes
docker compose -f docker-compose_portainer.yml down -v

# Start fresh
docker compose -f docker-compose_portainer.yml up -d
```

**Option 3: Via Portainer UI**
1. Go to **Containers** → Stop `split_friend`
2. Go to **Volumes** → Find `splitfriend_data` → Delete it
3. Go back to **Containers** → Start `split_friend`

After resetting, the app will create a new admin user with the credentials from your `ADMIN_PASSWORD` environment variable (default email: `admin@splitfriend.local`).

### Port conflict
Change the host port in the stack/container configuration:
```yaml
ports:
  - "9090:8080"  # Use port 9090 instead
```
