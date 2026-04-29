import sys
from pyngrok import ngrok

try:
    # Start a tunnel on port 5001
    public_url = ngrok.connect(5001).public_url
    print(f"NGROK_URL:{public_url}")
except Exception as e:
    print(f"ERROR:{e}")
