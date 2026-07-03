# Backend Setup & Troubleshooting

## Server Status
✅ **Backend server is now running with AUTO-RESTART enabled**

### To keep server running:
```bash
cd /Users/mihaela/Desktop/Licenta/backend
bash run_server.sh
```

Or start it in background:
```bash
nohup bash run_server.sh > backend_server.log 2>&1 &
```

### Verify server is working:
```bash
# Test recommendations endpoint
curl "http://127.0.0.1:5001/recommendations/personalized?lat=44.4268&lng=26.1025"

# Test weather endpoint
curl "http://127.0.0.1:5001/weather?lat=44.4268&lng=26.1025"
```

## Android App Configuration

The app is configured to reach the backend at:
- **Emulator**: `http://10.0.2.2:5001/` (auto-configured in build.gradle)
- **Physical Device**: Change to your computer's IP (e.g., `http://192.168.1.100:5001/`)

## How to Build & Run

### Option 1: Android Studio
1. Open project in Android Studio
2. Click "Run" (or Shift + F10)
3. Select emulator or device

### Option 2: Command Line
```bash
cd /Users/mihaela/Desktop/Licenta
./gradlew build
./gradlew installDebug  # for emulator
```

## Features Working
- ✅ Location services (Google Places API)
- ✅ Personalized recommendations
- ✅ Weather data
- ✅ Events & activities
- ✅ All location-based features

## If App Crashes

1. Check backend is running: `ps aux | grep python3`
2. Check logs: `tail -50 backend/backend_server.log`
3. Verify API: `curl http://127.0.0.1:5001/`
4. Restart backend: `cd backend && bash run_server.sh`
