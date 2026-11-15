#!/bin/sh
#

# Clean Docker containers, if exists
sudo docker compose -f compose-mariadb.yml down --rmi local

# Clean unused Docker volumes
sudo docker volume prune -a -f

# Create Docker containers
sudo docker compose -f compose-mariadb.yml up -d

echo  "Waiting for mariadb to be ready"
sleep 30
