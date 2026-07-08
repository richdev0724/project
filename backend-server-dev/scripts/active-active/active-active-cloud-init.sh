#!/bin/bash
set -euo pipefail

exec > /var/log/cloud-init-backend-active-active.log 2>&1

echo "$GHCR_TOKEN" | docker login ghcr.io -u "$GHCR_USER" --password-stdin

docker pull ghcr.io/market-mono/backend-server:latest

docker rm -f marketmono-backend-active-active 2>/dev/null || true

docker run -d \
  --name marketmono-backend-active-active \
  --restart unless-stopped \
  --network host \
  -e SPRING_PROFILES_ACTIVE=prod,active-active \
  -e DB_HOST="${DB_HOST}" \
  -e DB_PORT="${DB_PORT}" \
  -e DB_USERNAME="${DB_USERNAME}" \
  -e DB_PASSWORD="${DB_PASSWORD}" \
  -e ACTIVE_ACTIVE_REDIS_ENABLED=true \
  -e WEBSOCKET_RELAY_ENABLED=true \
  -e REDIS_HOST="${REDIS_HOST}" \
  -e REDIS_PORT="${REDIS_PORT}" \
  -e REDIS_PASSWORD="${REDIS_PASSWORD}" \
  -e REDIS_SSL="${REDIS_SSL:-true}" \
  -e KAFKA_ENABLED=true \
  -e KAFKA_PASSWORD="${KAFKA_PASSWORD}" \
  -e KAFKA_USERNAME="${KAFKA_USERNAME:-marketmono}" \
  -e KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-stream.unusualwhales.com:9083}" \
  -e KAFKA_LISTENER_CONCURRENCY=2 \
  -e SPRING_LIQUIBASE_ENABLED=false \
  ghcr.io/market-mono/backend-server:latest