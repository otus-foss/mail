FROM python:3.15.0-slim
WORKDIR /app
COPY push_bridge/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY push_bridge /app/push_bridge
RUN useradd --uid 10001 --create-home otus && mkdir /data && chown otus /data
USER otus
EXPOSE 8080
CMD ["gunicorn", "--bind", "0.0.0.0:8080", "--workers", "2", "--threads", "4", "--timeout", "30", "push_bridge.server:application"]
