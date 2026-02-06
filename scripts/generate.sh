#!/bin/bash

# Certificate generation script for mTLS (Linux/Bash version)
# Requires OpenSSL and keytool to be installed

CERTS_DIR="./certs"
RESOURCES_DIR="./src/main/resources"

# Create directories
mkdir -p "$CERTS_DIR"
mkdir -p "$RESOURCES_DIR"

echo "=== Step 1: Generate CA (Certificate Authority) ==="
# Generate CA private key
openssl genrsa -out "$CERTS_DIR/ca-key.pem" 4096

# Generate CA certificate (self-signed)
openssl req -new -x509 -days 3650 -key "$CERTS_DIR/ca-key.pem" -out "$CERTS_DIR/ca-cert.pem" \
  -subj "/C=US/ST=State/L=City/O=MyOrg/OU=CA/CN=MyCA"

echo ""
echo "=== Step 2: Generate Server Certificate ==="
# Generate server private key
openssl genrsa -out "$CERTS_DIR/server-key.pem" 4096

# Generate server CSR
openssl req -new -key "$CERTS_DIR/server-key.pem" -out "$CERTS_DIR/server.csr" \
  -subj "/C=US/ST=State/L=City/O=MyOrg/OU=Server/CN=localhost"

# Sign server certificate with CA
openssl x509 -req -days 365 -in "$CERTS_DIR/server.csr" \
  -CA "$CERTS_DIR/ca-cert.pem" -CAkey "$CERTS_DIR/ca-key.pem" -CAcreateserial \
  -out "$CERTS_DIR/server-cert.pem"

echo ""
echo "=== Step 3: Generate Client Certificate ==="
# Generate client private key
openssl genrsa -out "$CERTS_DIR/client-key.pem" 4096

# Generate client CSR with email as CN
openssl req -new -key "$CERTS_DIR/client-key.pem" -out "$CERTS_DIR/client.csr" \
  -subj "/C=US/ST=State/L=City/O=MyOrg/OU=Client/CN=user@example.com"

# Sign client certificate with CA
openssl x509 -req -days 365 -in "$CERTS_DIR/client.csr" \
  -CA "$CERTS_DIR/ca-cert.pem" -CAkey "$CERTS_DIR/ca-key.pem" -CAcreateserial \
  -out "$CERTS_DIR/client-cert.pem"

echo ""
echo "=== Step 4: Create PKCS12 keystores for Spring Boot ==="
# Create server keystore
openssl pkcs12 -export -in "$CERTS_DIR/server-cert.pem" -inkey "$CERTS_DIR/server-key.pem" \
  -out "$RESOURCES_DIR/keystore.p12" -name server -passout pass:changeit

# Create truststore with CA certificate
keytool -importcert -file "$CERTS_DIR/ca-cert.pem" -alias ca -keystore "$RESOURCES_DIR/truststore.p12" \
  -storepass changeit -storetype PKCS12 -noprompt

echo ""
echo "=== Step 5: Create client PKCS12 for client application ==="
# Create client keystore
openssl pkcs12 -export -in "$CERTS_DIR/client-cert.pem" -inkey "$CERTS_DIR/client-key.pem" \
  -out "$CERTS_DIR/client-keystore.p12" -name client -passout pass:changeit

# Copy CA cert for client
cp "$CERTS_DIR/ca-cert.pem" "$CERTS_DIR/client-ca-cert.pem"

echo ""
echo "=== Certificate Generation Complete ==="
echo "Generated files:"
echo "  - CA: $CERTS_DIR/ca-cert.pem, $CERTS_DIR/ca-key.pem"
echo "  - Server: $CERTS_DIR/server-cert.pem, $CERTS_DIR/server-key.pem"
echo "  - Client: $CERTS_DIR/client-cert.pem, $CERTS_DIR/client-key.pem"
echo "  - Server keystore: $RESOURCES_DIR/keystore.p12"
echo "  - Server truststore: $RESOURCES_DIR/truststore.p12"
echo "  - Client keystore: $CERTS_DIR/client-keystore.p12"
echo ""
echo "Note: Default password for all keystores is 'changeit'"
