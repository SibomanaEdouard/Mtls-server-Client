import javax.net.ssl.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyStore;

/**
 * mTLS Client that sends empty PATCH request to the server
 * Usage: java MtlsClient
 */
public class MtlsClient {

    private static final String REGISTER_URL = "https://localhost:8443/api/register";
    private static final String UPDATE_URL = "https://localhost:8443/api/update";
    private static final String CLIENT_KEYSTORE_PATH = "../certs/client-keystore.p12";
    private static final String TRUSTSTORE_PATH = "../certs/ca-cert.pem";
    private static final String KEYSTORE_PASSWORD = "changeit";

    public static void main(String[] args) {
        try {
            System.out.println("=== mTLS Client Starting ===");

            // Load client certificate and key
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (FileInputStream fis = new FileInputStream(CLIENT_KEYSTORE_PATH)) {
                keyStore.load(fis, KEYSTORE_PASSWORD.toCharArray());
            }

            // Initialize KeyManager with client certificate
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm()
            );
            keyManagerFactory.init(keyStore, KEYSTORE_PASSWORD.toCharArray());

            // Load CA certificate for trusting server
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);

            // Read CA certificate
            try (FileInputStream fis = new FileInputStream(TRUSTSTORE_PATH)) {
                java.security.cert.CertificateFactory cf =
                        java.security.cert.CertificateFactory.getInstance("X.509");
                java.security.cert.Certificate caCert = cf.generateCertificate(fis);
                trustStore.setCertificateEntry("ca", caCert);
            }

            // Initialize TrustManager
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm()
            );
            trustManagerFactory.init(trustStore);

            // Create SSL context with both KeyManager and TrustManager
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(
                    keyManagerFactory.getKeyManagers(),
                    trustManagerFactory.getTrustManagers(),
                    new java.security.SecureRandom()
            );

            // Set as default SSL socket factory
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());

            // Register user first
            System.out.println("Registering user...");
            URL registerUrl = new URL(REGISTER_URL);
            HttpsURLConnection registerConnection = (HttpsURLConnection) registerUrl.openConnection();
            registerConnection.setRequestMethod("POST");
            registerConnection.setRequestProperty("Content-Type", "application/json");
            registerConnection.setDoOutput(true);
            registerConnection.setConnectTimeout(5000);
            registerConnection.setReadTimeout(5000);

            String jsonBody = "{\"email\":\"user@example.com\"}";
            try (OutputStream os = registerConnection.getOutputStream()) {
                os.write(jsonBody.getBytes());
            }

            int registerResponseCode = registerConnection.getResponseCode();
            if (registerResponseCode == 201) {
                System.out.println("✓ User registered successfully.");
            } else if (registerResponseCode == 409) {
                System.out.println("User already exists, proceeding to update.");
            } else {
                System.out.println("✗ Registration failed with code: " + registerResponseCode);
            }
            registerConnection.disconnect();

            // Now update
            URL updateUrl = new URL(UPDATE_URL);
            HttpsURLConnection connection = (HttpsURLConnection) updateUrl.openConnection();

            // Configure request
            connection.setRequestMethod("POST");
            connection.setRequestProperty("X-HTTP-Method-Override", "PATCH");
            connection.setDoOutput(false); // Empty body
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            System.out.println("Sending POST request (PATCH override) to: " + UPDATE_URL);
            System.out.println("Using client certificate with CN: user@example.com");

            // Send request
            int responseCode = connection.getResponseCode();
            String responseMessage = connection.getResponseMessage();

            System.out.println("\n=== Response ===");
            System.out.println("Response Code: " + responseCode);
            System.out.println("Response Message: " + responseMessage);

            if (responseCode == 200) {
                System.out.println("✓ Success! User record updated.");
            } else if (responseCode == 400) {
                System.out.println("✗ Bad Request - CN does not resemble an email");
            } else if (responseCode == 403) {
                System.out.println("✗ Forbidden - User not found in database");
            } else {
                System.out.println("✗ Unexpected response code");
            }

            connection.disconnect();

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}