#!/bin/bash

# CityScape Flask Server + ngrok tunnel starter

echo "🚀 Starting CityScape Flask Server with ngrok tunnel..."

# Check if ngrok is installed
if ! command -v ngrok &> /dev/null; then
    echo "❌ ngrok not found. Installing..."
    npm install -g ngrok
fi

# Check if Flask is installed
if ! python3 -c "import flask" 2>/dev/null; then
    echo "📦 Installing Flask dependencies..."
    pip install -r requirements.txt
fi

# Create a temporary directory for ngrok logs
mkdir -p /tmp/cityscape-ngrok

# Start Flask in background
echo "▶️  Starting Flask server on port 5001..."
python3 app.py > /tmp/cityscape-ngrok/flask.log 2>&1 &
FLASK_PID=$!

# Give Flask time to start
sleep 2

# Start ngrok tunnel
echo "🌐 Starting ngrok tunnel..."
ngrok http 5001 --log=stdout > /tmp/cityscape-ngrok/ngrok.log 2>&1 &
NGROK_PID=$!

# Give ngrok time to start and get the URL
sleep 3

# Get the ngrok URL
NGROK_URL=$(curl -s http://localhost:4040/api/tunnels | grep -o '"public_url":"[^"]*' | cut -d'"' -f4 | head -1)

if [ -z "$NGROK_URL" ]; then
    NGROK_URL="https://your-ngrok-url.ngrok.io"
fi

echo ""
echo "✅ Servers started successfully!"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🔗 Public URL: $NGROK_URL"
echo "🖥️  Local URL: http://localhost:5001"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "📝 Update email confirmation page with:"
echo "const backendUrl = '$NGROK_URL';"
echo ""
echo "Press CTRL+C to stop all services..."
echo ""

# Function to cleanup on exit
cleanup() {
    echo ""
    echo "🛑 Stopping services..."
    kill $FLASK_PID 2>/dev/null
    kill $NGROK_PID 2>/dev/null
    echo "✅ Services stopped."
    exit 0
}

# Set trap to cleanup on CTRL+C
trap cleanup SIGINT

# Keep script running
wait $FLASK_PID $NGROK_PID
