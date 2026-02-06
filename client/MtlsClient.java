import javax.net.ssl.*;
import java.io.*;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyStore;
import java.security.SecureRandom;

/**
 * mTLS Client that sends requests to the server
 * Usage: java MtlsClient
 */
public class MtlsClient {

    private static final String REGISTER_URL = "https://localhost:8443/api/register";
    private static final String UPDATE_URL = "https://localhost:8443/api/update";
    private static final String CLIENT_KEYSTORE_PATH = "../certs/client-keystore.p12";
    private static final String TRUSTSTORE_PATH = "../client-truststore.p12";
    private static final String KEYSTORE_PASSWORD = "changeit";

    public static void main(String[] args) {
        try {
            System.out.println("=== mTLS Client Starting ===");

            // Create SSL context with client certificate and truststore
            SSLContext sslContext = SSLContext.getInstance("TLS");
            
            // Load keystore with client certificate
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (FileInputStream fis = new FileInputStream(CLIENT_KEYSTORE_PATH)) {
                keyStore.load(fis, KEYSTORE_PASSWORD.toCharArray());
            }
            
            // Initialize KeyManagerFactory
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, KEYSTORE_PASSWORD.toCharArray());
            
            // Load truststore with CA certificate
            KeyStore trustStore = KeyStore.getInstance("PKCS12");
            try (FileInputStream fis = new FileInputStream(TRUSTSTORE_PATH)) {
                trustStore.load(fis, KEYSTORE_PASSWORD.toCharArray());
            }
            
            // Initialize TrustManagerFactory
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            
            // Initialize SSL context
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
            
            // Create socket factory and set as default
            SSLSocketFactory socketFactory = sslContext.getSocketFactory();
            HttpsURLConnection.setDefaultSSLSocketFactory(socketFactory);
            
            // Allow localhost hostname
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> 
                hostname.equals("localhost") || hostname.equals("127.0.0.1"));

            System.out.println("SSL Context initialized successfully");
            System.out.println("TrustManager providers: " + tmf.getTrustManagers().length);
            for (TrustManager tm : tmf.getTrustManagers()) {
                System.out.println("  - " + tm.getClass().getName());
            }

            // Register user first
            System.out.println("\nRegistering user...");
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
                System.out.println("Response: " + readResponse(registerConnection));
            }
            registerConnection.disconnect();

            // Now update
            URL updateUrl = new URL(UPDATE_URL);
            HttpsURLConnection connection = (HttpsURLConnection) updateUrl.openConnection();

            // Configure request
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setDoOutput(true);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            // Send _method=PATCH as form data
            String formData = "_method=PATCH";
            try (OutputStream os = connection.getOutputStream()) {
                os.write(formData.getBytes());
            }

            System.out.println("\nSending POST request (PATCH override) to: " + UPDATE_URL);
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
                System.out.println("Response: " + readResponse(connection));
            }

            connection.disconnect();

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String readResponse(HttpURLConnection conn) throws IOException {
        InputStream errorStream = conn.getErrorStream();
        if (errorStream == null) {
            InputStream inputStream = conn.getInputStream();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                return reader.lines().reduce("", (a, b) -> a + b);
            }
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream))) {
            return reader.lines().reduce("", (a, b) -> a + b);
        }
    }

    private static void enablePatchMethod() {
        try {
            Field methodsField = HttpURLConnection.class.getDeclaredField("methods");
            methodsField.setAccessible(true);
            String[] oldMethods = (String[]) methodsField.get(null);
            String[] newMethods = new String[oldMethods.length + 1];
            System.arraycopy(oldMethods, 0, newMethods, 0, oldMethods.length);
            newMethods[oldMethods.length] = "PATCH";
            methodsField.set(null, newMethods);
        } catch (Exception e) {
            System.err.println("Failed to enable PATCH method: " + e.getMessage());
        }
    }
}
