#!/bin/bash

# Auto-restart server if it crashes
while true; do
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] Starting Flask server..."
    python3 app.py
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] Server crashed! Restarting in 3 seconds..."
    sleep 3
done
