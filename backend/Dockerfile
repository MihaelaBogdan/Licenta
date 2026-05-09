FROM python:3.9-slim

WORKDIR /app

# Install system deps
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc g++ && rm -rf /var/lib/apt/lists/*

# Install PyTorch CPU first (separate layer for caching)
RUN pip install --no-cache-dir \
    torch==2.2.2+cpu \
    --extra-index-url https://download.pytorch.org/whl/cpu

# Install remaining dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir \
    flask==3.0.3 \
    flask-cors==4.0.1 \
    transformers==4.40.2 \
    numpy==1.26.4 \
    nltk==3.8.1 \
    python-dotenv==1.0.1 \
    requests==2.31.0 \
    beautifulsoup4==4.12.3 \
    google-generativeai==0.8.3 \
    gunicorn==22.0.0

# Download NLTK data
RUN python -m nltk.downloader punkt punkt_tab

# Copy app files (excludes files listed in .dockerignore)
COPY app.py chatbot.py model.py nltk_utils.py ./
COPY data.pth ./
COPY data/ ./data/
COPY templates/ ./templates/
COPY static/ ./static/

# Railway injects PORT env var automatically
ENV FLASK_APP=app.py
ENV FLASK_ENV=production
EXPOSE 8080

CMD ["gunicorn", "--bind", "0.0.0.0:8080", "--workers", "1", "--timeout", "120", "app:app"]
