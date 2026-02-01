# Docker Commands Reference

## List Containers

```bash
docker ps                    # Running containers
docker ps -a                 # All containers (including stopped)
```

## List Images

```bash
docker images
```

## List Volumes

```bash
docker volume ls
```

## List Networks

```bash
docker network ls
```

## List Everything at Once

```bash
docker system df             # Disk usage summary
docker system df -v          # Detailed breakdown
```

## Docker Compose Commands

```bash
docker compose ps            # Containers for current project
docker compose images        # Images used by current project
```

## Filter by Project/Label

```bash
# If containers have a common name prefix or label
docker ps -a --filter "name=myproject"
docker ps -a --filter "label=com.docker.compose.project=myproject"
```

## Inspect a Specific Container

```bash
docker inspect <container_id>
```
# Docker Cleanup

## Cleanup Current Container Only

### Using Docker Compose (recommended)

```bash
# Stop and remove container + its volume
docker compose down -v
```

### Manual Cleanup

```bash
# Stop and remove the container
docker rm -f edm_postgres

# Remove the volume
docker volume rm output_postgres_data
```

## Remove Downloaded Image

```bash
docker rmi postgres:16
```

## Full Docker Cleanup (all projects)

**Warning:** These commands affect all Docker resources on your system, not just this project.

```bash
# Remove all stopped containers, unused networks, dangling images, and build cache
docker system prune

# Include unused volumes too
docker system prune --volumes
```