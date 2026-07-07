#!/usr/bin/env python3
"""
CityScape Flask Server + ngrok tunnel starter
Cross-platform (Windows, Mac, Linux)
"""

import subprocess
import time
import sys
import os
import json
import urllib.request
import signal

def get_ngrok_url():
    """Get ngrok public URL from API"""
    try:
        response = urllib.request.urlopen('http://localhost:4040/api/tunnels', timeout=5)
        data = json.loads(response.read().decode())
        if data['tunnels']:
            return data['tunnels'][0]['public_url']
    except Exception as e:
        print(f"Could not get ngrok URL: {e}")
    return "https://your-ngrok-url.ngrok.io"

def check_requirements():
    """Check if required packages are installed"""
    packages = ['flask', 'python-dotenv', 'requests']
    missing = []

    for package in packages:
        try:
            __import__(package.replace('-', '_'))
        except ImportError:
            missing.append(package)

    if missing:
        print(f"📦 Installing missing packages: {', '.join(missing)}")
        subprocess.run([sys.executable, '-m', 'pip', 'install', '-r', 'requirements.txt'], check=True)

def main():
    print("🚀 Starting CityScape Flask Server with ngrok tunnel...\n")

    # Check requirements
    check_requirements()

    # Start Flask
    print("▶️  Starting Flask server on port 5001...")
    flask_process = subprocess.Popen(
        [sys.executable, 'app.py'],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE
    )

    # Give Flask time to start
    time.sleep(3)

    # Check if Flask is running
    if flask_process.poll() is not None:
        print("❌ Flask failed to start!")
        stdout, stderr = flask_process.communicate()
        print(stderr.decode() if stderr else "No error output")
        return

    # Start ngrok
    print("🌐 Starting ngrok tunnel...")
    try:
        ngrok_process = subprocess.Popen(
            ['ngrok', 'http', '5001', '--log=stdout'],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE
        )
    except FileNotFoundError:
        print("❌ ngrok not found. Install with: npm install -g ngrok")
        flask_process.terminate()
        return

    # Give ngrok time to start
    time.sleep(4)

    # Get ngrok URL
    ngrok_url = get_ngrok_url()

    # Print info
    print("\n" + "="*50)
    print("✅ Servers started successfully!")
    print("="*50)
    print(f"🔗 Public URL: {ngrok_url}")
    print(f"🖥️  Local URL: http://localhost:5001")
    print("="*50)
    print("\n📝 Update email confirmation page with:")
    print(f"const backendUrl = '{ngrok_url}';")
    print("\n⚠️  Note: URL changes each time you restart!")
    print("Press CTRL+C to stop all services...\n")

    # Keep running
    try:
        while True:
            time.sleep(1)
            if flask_process.poll() is not None:
                print("❌ Flask process died!")
                break
            if ngrok_process.poll() is not None:
                print("❌ ngrok process died!")
                break
    except KeyboardInterrupt:
        print("\n\n🛑 Stopping services...")
        flask_process.terminate()
        ngrok_process.terminate()

        # Wait for processes to finish
        try:
            flask_process.wait(timeout=5)
            ngrok_process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            flask_process.kill()
            ngrok_process.kill()

        print("✅ Services stopped.")

if __name__ == '__main__':
    main()
