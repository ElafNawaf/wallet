# Use official OpenJDK 17 image as base
FROM openjdk:17-jdk-slim

# Set working directory
WORKDIR /app

# Install required packages
RUN apt-get update && apt-get install -y \
    maven \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy pom.xml and download dependencies (for better caching)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# Create directory for SQLite database with proper permissions
RUN mkdir -p /app/data && chmod 755 /app/data

# Create a non-root user for security
RUN groupadd -r appuser && useradd -r -g appuser appuser
RUN chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

# Set environment variables
ENV SPRING_PROFILES_ACTIVE=docker
ENV DATABASE_PATH=/app/data/wallet.db

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/api/wallet/health || exit 1

# Run the application
CMD ["java", "-jar", "target/wallet-api-1.0.0.jar"]