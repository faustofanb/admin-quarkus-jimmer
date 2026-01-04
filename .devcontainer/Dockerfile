FROM mcr.microsoft.com/devcontainers/java:25

# Install Docker CLI manually to avoid feature build failures
RUN apt-get update && \
    apt-get install -y docker.io btop maven && \
    curl -L "https://github.com/docker/compose/releases/download/v2.29.2/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose && \
    chmod +x /usr/local/bin/docker-compose && \
    apt-get clean -y && rm -rf /var/lib/apt/lists/*
