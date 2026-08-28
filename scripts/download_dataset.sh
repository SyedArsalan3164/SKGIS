#!/usr/bin/env bash

# SKGIS Dataset Setup Script
echo "=========================================="
echo "SKGIS Dataset Setup"
echo "=========================================="

mkdir -p data/sample

if [ -f "data/sample/paysim_sample_5000.csv" ]; then
    echo "Sample dataset already exists at data/sample/paysim_sample_5000.csv"
else
    echo "Generating deterministic sample dataset with synthetic device/account ring augmentation..."
    python3 scripts/generate_sample_data.py || python scripts/generate_sample_data.py
fi

echo "Dataset setup complete!"
