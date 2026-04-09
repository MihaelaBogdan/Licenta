FROM python:3.9-slim

# Set working directory
WORKDIR /app

# Copy requirement files and install dependencies
COPY requirements.txt .
RUN pip install --upgrade pip
RUN pip install --no-cache-dir torch torchvision --index-url https://download.pytorch.org/whl/cpu
RUN pip install --no-cache-dir -r requirements.txt

# Download NLTK data required for Chatbot
RUN python -m nltk.downloader punkt

# Copy all the rest of the application files
COPY . .

# Expose port
EXPOSE 5000

# Set environment variables for Flask
ENV FLASK_APP=app.py
ENV FLASK_ENV=production

# Run the backend
CMD ["python", "app.py"]
