#!/usr/bin/env bash

# SKGIS One-Command Demo Launcher
echo "=========================================================="
echo "  Semantic Knowledge Graph Intelligence System (SKGIS)   "
echo "=========================================================="

echo "[1/5] Starting Neo4j 5.x Container with GDS Plugin..."
docker-compose up -d

echo "Waiting 15 seconds for Neo4j database to initialize..."
sleep 15

echo "[2/5] Ensuring Sample Data Exists..."
bash scripts/download_dataset.sh

echo "[3/5] Building Spring Boot Application..."
mvn clean package -DskipTests

echo "[4/5] Launching SKGIS Service..."
mvn spring-boot:run &
APP_PID=$!

echo "Waiting 10 seconds for Spring Boot API to start..."
sleep 10

echo "Triggering Batch Ingestion API..."
curl -X POST http://localhost:8080/api/ingest/run

echo "Triggering Risk Detection Pipeline (Louvain + Rules)..."
curl -X POST http://localhost:8080/api/risk/detect

echo "[5/5] Opening vis.js Graph Intelligence Explorer..."
if command -v start &> /dev/null; then
    start frontend/index.html
elif command -v open &> /dev/null; then
    open frontend/index.html
elif command -v xdg-open &> /dev/null; then
    xdg-open frontend/index.html
else
    echo "Please open frontend/index.html in your browser: file://$(pwd)/frontend/index.html"
fi

echo "=========================================================="
echo "SKGIS Demo running! Press Ctrl+C to exit service."
echo "=========================================================="
wait $APP_PID
