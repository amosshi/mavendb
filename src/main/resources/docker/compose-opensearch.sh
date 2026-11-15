#!/bin/sh
#

# Clean Docker containers, if exists
sudo docker compose -f compose-opensearch.yml down --rmi local

# Clean unused Docker volumes
sudo docker volume prune -a -f

# Create Docker containers
sudo docker compose -f compose-opensearch.yml up -d

echo  "Waiting for opensearch to be ready"
sleep 30
