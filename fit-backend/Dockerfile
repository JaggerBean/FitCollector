FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
RUN pip install supervisor

COPY . .

# Add supervisord config
COPY supervisord.conf /etc/supervisord.conf

CMD ["supervisord", "-c", "/etc/supervisord.conf"]
