# Use OpenJDK 25 as base image
FROM eclipse-temurin:25-jdk

# Set working directory
WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY mvnw pom.xml ./
COPY .mvn .mvn

# Copy source code
COPY src src

# Copy certificates
COPY certs certs
COPY ca-cert.pem ca-cert.pem

# Build the application
RUN chmod +x ./mvnw && ./mvnw clean package -DskipTests

# Expose port
EXPOSE 8443

# Run the application
CMD ["java", "-jar", "target/backend-proj-0.0.1-SNAPSHOT.jar"]