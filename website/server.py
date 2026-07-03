#!/usr/bin/env python3

import http.server
import socketserver
import os
import sys

PORT = int(os.environ.get("PORT", 3000))
os.chdir(os.path.dirname(os.path.abspath(__file__)))

class MyHTTPRequestHandler(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header('Cache-Control', 'no-store, no-cache, must-revalidate')
        self.send_header('Access-Control-Allow-Origin', '*')
        return super().end_headers()

    def do_GET(self):
        if self.path == '/':
            self.path = '/index.html'
        return super().do_GET()

with socketserver.TCPServer(("", PORT), MyHTTPRequestHandler) as httpd:
    print(f"🚀 CityScape Website rodando em http://localhost:{PORT}")
    print(f"📱 Acessa http://localhost:{PORT} para download")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n✋ Servidor parado")
        sys.exit(0)
