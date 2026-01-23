# Certificate generation script for mTLS (PowerShell version)
# Requires OpenSSL to be installed on Windows

$CERTS_DIR = "../certs"
$RESOURCES_DIR = "../src/main/resources"

# Create directories
New-Item -ItemType Directory -Force -Path $CERTS_DIR | Out-Null
New-Item -ItemType Directory -Force -Path $RESOURCES_DIR | Out-Null

Write-Host "=== Step 1: Generate CA (Certificate Authority) ===" -ForegroundColor Green
# Generate CA private key
openssl genrsa -out "$CERTS_DIR/ca-key.pem" 4096

# Generate CA certificate (self-signed)
openssl req -new -x509 -days 3650 -key "$CERTS_DIR/ca-key.pem" -out "$CERTS_DIR/ca-cert.pem" `
  -subj "/C=US/ST=State/L=City/O=MyOrg/OU=CA/CN=MyCA"

Write-Host ""
Write-Host "=== Step 2: Generate Server Certificate ===" -ForegroundColor Green
# Generate server private key
openssl genrsa -out "$CERTS_DIR/server-key.pem" 4096

# Generate server CSR
openssl req -new -key "$CERTS_DIR/server-key.pem" -out "$CERTS_DIR/server.csr" `
  -subj "/C=US/ST=State/L=City/O=MyOrg/OU=Server/CN=localhost"

# Sign server certificate with CA
openssl x509 -req -days 365 -in "$CERTS_DIR/server.csr" `
  -CA "$CERTS_DIR/ca-cert.pem" -CAkey "$CERTS_DIR/ca-key.pem" -CAcreateserial `
  -out "$CERTS_DIR/server-cert.pem"

Write-Host ""
Write-Host "=== Step 3: Generate Client Certificate ===" -ForegroundColor Green
# Generate client private key
openssl genrsa -out "$CERTS_DIR/client-key.pem" 4096

# Generate client CSR with email as CN
openssl req -new -key "$CERTS_DIR/client-key.pem" -out "$CERTS_DIR/client.csr" `
  -subj "/C=US/ST=State/L=City/O=MyOrg/OU=Client/CN=user@example.com"

# Sign client certificate with CA
openssl x509 -req -days 365 -in "$CERTS_DIR/client.csr" `
  -CA "$CERTS_DIR/ca-cert.pem" -CAkey "$CERTS_DIR/ca-key.pem" -CAcreateserial `
  -out "$CERTS_DIR/client-cert.pem"

Write-Host ""
Write-Host "=== Step 4: Create PKCS12 keystores for Spring Boot ===" -ForegroundColor Green
# Create server keystore
openssl pkcs12 -export -in "$CERTS_DIR/server-cert.pem" -inkey "$CERTS_DIR/server-key.pem" `
  -out "$RESOURCES_DIR/keystore.p12" -name server -passout pass:changeit

# Create truststore with CA certificate
keytool -importcert -file "$CERTS_DIR/ca-cert.pem" -alias ca -keystore "$RESOURCES_DIR/truststore.p12" `
  -storepass changeit -storetype PKCS12 -noprompt

Write-Host ""
Write-Host "=== Step 5: Create client PKCS12 for client application ===" -ForegroundColor Green
# Create client keystore
openssl pkcs12 -export -in "$CERTS_DIR/client-cert.pem" -inkey "$CERTS_DIR/client-key.pem" `
  -out "$CERTS_DIR/client-keystore.p12" -name client -passout pass:changeit

# Copy CA cert for client
Copy-Item "$CERTS_DIR/ca-cert.pem" "$CERTS_DIR/client-ca-cert.pem"

Write-Host ""
Write-Host "=== Certificate Generation Complete ===" -ForegroundColor Green
Write-Host "Generated files:"
Write-Host "  - CA: $CERTS_DIR/ca-cert.pem, $CERTS_DIR/ca-key.pem"
Write-Host "  - Server: $CERTS_DIR/server-cert.pem, $CERTS_DIR/server-key.pem"
Write-Host "  - Client: $CERTS_DIR/client-cert.pem, $CERTS_DIR/client-key.pem"
Write-Host "  - Server keystore: $RESOURCES_DIR/keystore.p12"
Write-Host "  - Server truststore: $RESOURCES_DIR/truststore.p12"
Write-Host "  - Client keystore: $CERTS_DIR/client-keystore.p12"
Write-Host ""
Write-Host "Note: Default password for all keystores is 'changeit'" -ForegroundColor Yellow