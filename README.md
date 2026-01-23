# Backend Project with mTLS and UDP Broadcasting

A Spring Boot application that provides user presence management with mutual TLS (mTLS) authentication and UDP broadcast notifications for real-time user updates.

## Features

- **Mutual TLS Authentication**: Secure client-server communication using X.509 certificates
- **User Registration**: Register users with email validation
- **User Presence Updates**: Update user last seen time, IP address, and port via mTLS
- **UDP Broadcasting**: Automatic broadcast of user updates to listeners on the network
- **PostgreSQL Database**: Persistent storage of user information
- **RESTful API**: Clean REST endpoints with OpenAPI/Swagger documentation
- **Java Test Client**: Standalone client for testing mTLS connections and UDP broadcasts

## Architecture

The application consists of:

- **Spring Boot Server**: Main application running on HTTPS (port 8443) with mTLS
- **PostgreSQL Database**: Stores user records
- **UDP Broadcast Service**: Sends binary-encoded user updates to port 6667
- **Java Client**: Tests registration and updates, triggers broadcasts
- **UDP Listener**: Receives and decodes broadcast messages

### Message Format

UDP broadcasts use a custom binary format:
```
[email_length:4][email:variable][lastSeen:8][ip_length:4][ip:variable][port:4]
```

## Prerequisites

### For Docker Setup (Recommended)
- Docker and Docker Compose
- OpenSSL (for certificate generation)

### For Manual Setup
- Java 25
- Maven 3.6+
- PostgreSQL 16
- OpenSSL (for certificate generation)

## Setup

### Option 1: Docker Setup (Recommended)

1. **Generate Certificates** (required for Docker):
```bash
# On Linux/Mac
./scripts/generate.sh

# On Windows (PowerShell)
.\scripts\generate.ps1
```

2. **Run with Docker Compose**:
```bash
docker compose up -d --build
```

This will start:
- PostgreSQL database on port 5432
- Spring Boot application on port 8443 with mTLS
- Automatic UDP broadcasting on port 6667

### Option 2: Manual Setup

1. **Database Setup**

Create a PostgreSQL database:
```sql
CREATE DATABASE mtls_db;
CREATE USER postgres WITH PASSWORD 'sibo1234';
GRANT ALL PRIVILEGES ON DATABASE mtls_db TO postgres;
```

2. **Certificate Generation**

Run the certificate generation script:
```bash
# On Linux/Mac
./scripts/generate.sh

# On Windows (PowerShell)
.\scripts\generate.ps1
```

This creates:
- CA certificate and key
- Server certificate and keystore
- Client certificate and keystore

3. **Build the Application**
```bash
mvn clean install
```

## Configuration

Key configuration files:

- `src/main/resources/application.properties`: Server and database configuration
- Certificate stores in `src/main/resources/` and `certs/`

### Important Settings

```properties
# Server Configuration
server.port=8443
server.ssl.enabled=true
server.ssl.key-store=classpath:keystore.p12
server.ssl.key-store-password=changeit
server.ssl.key-store-type=PKCS12
server.ssl.key-alias=server

# mTLS Configuration
server.ssl.client-auth=need
server.ssl.trust-store=classpath:truststore.jks
server.ssl.trust-store-password=changeit
server.ssl.trust-store-type=JKS

# Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/mtls_db
spring.datasource.username=postgres
spring.datasource.password=sibo1234
```

## Running the Application

### Docker (Recommended)

After running `docker-compose up --build`, the services will be available at:
- Spring Boot application: `https://localhost:8443`
- PostgreSQL database: `localhost:5432`

### Manual Startup

#### Start the Server

```bash
mvn spring-boot:run
```

The server starts on `https://localhost:8443`

### Test with UDP Listener

In a separate terminal, start the UDP listener:

```bash
cd client
java UdpBroadcastListener.java
```

### Test with mTLS Client

In another terminal, run the test client:

```bash
cd client
java MtlsClient.java
```

This will:
1. Register a user with email `user@example.com`
2. Update the user's presence
3. Trigger a UDP broadcast

The UDP listener should display the received broadcast message.

## API Endpoints

### POST /api/register
Register a new user.

**Request Body:**
```json
{
  "email": "user@example.com"
}
```

**Responses:**
- `201`: User registered successfully
- `400`: Invalid email format
- `409`: User already exists

### POST /api/update (with X-HTTP-Method-Override: PATCH)
Update user presence information. Requires mTLS authentication.

**Authentication:** Client certificate with CN matching registered email

**Responses:**
- `200`: User updated successfully (triggers UDP broadcast)
- `400`: Invalid certificate or request
- `403`: User not found

### API Documentation

Access Swagger UI at: `https://localhost:8443/swagger-ui.html`

## Testing

### Manual Testing

1. Start the server
2. Start the UDP listener
3. Run the mTLS client
4. Observe the broadcast message in the listener terminal

### Certificate Verification

Verify certificates:

```bash
# Server keystore
openssl pkcs12 -info -in src/main/resources/keystore.p12 -passin pass:changeit

# Client keystore
openssl pkcs12 -info -in certs/client-keystore.p12 -passin pass:changeit
```

## Security

- **mTLS**: Mutual authentication ensures both client and server identities
- **Certificate Validation**: Server validates client certificates against trust store
- **HTTPS Only**: All communication encrypted
- **Input Validation**: Email format validation and sanitization

## Troubleshooting

### Common Issues

1. **Server won't start**: Check database connection and certificate paths
2. **mTLS connection fails**: Verify client certificate CN matches registered email
3. **UDP broadcasts not received**: Check firewall settings for port 6667
4. **Database errors**: Ensure PostgreSQL is running and credentials are correct

### Logs

Enable debug logging in `application.properties`:

```properties
logging.level.qt.test.backend_proj=DEBUG
logging.level.org.springframework.security=DEBUG
```

## Technologies Used

- **Spring Boot 3.x**: Framework
- **Spring Security**: mTLS implementation
- **Spring Data JPA**: Database access
- **PostgreSQL**: Database
- **Maven**: Build tool
- **OpenSSL**: Certificate generation
- **Lombok**: Code generation
- **Swagger/OpenAPI**: API documentation