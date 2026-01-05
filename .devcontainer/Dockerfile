FROM mcr.microsoft.com/devcontainers/java:21

# Additional tools (Docker, Maven are handled by Features or Base Image)
RUN apt-get update && \
    apt-get install -y btop && \
    apt-get clean -y && rm -rf /var/lib/apt/lists/*
