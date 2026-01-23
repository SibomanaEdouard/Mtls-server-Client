package qt.test.backend_proj.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import qt.test.backend_proj.Dto.RegisterRequest;
import qt.test.backend_proj.model.User;
import qt.test.backend_proj.repository.UserRepository;
import qt.test.backend_proj.service.UdpBroadcastService;

import java.security.cert.X509Certificate;
import java.util.regex.Pattern;

@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Management", description = "APIs for managing user presence and updates")
public class UserController {

    private final UserRepository userRepository;
    private final UdpBroadcastService udpBroadcastService;

    // Email validation pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    @PostMapping("/api/register")
    @Operation(summary = "Register a new user", description = "Registers a new user with the provided email address.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User registered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid email format"),
            @ApiResponse(responseCode = "409", description = "User already exists"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Void> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        try {
            String email = registerRequest.getEmail();

            if (email == null || email.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            // Validate email format
            if (!isValidEmail(email)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            // Check if user already exists
            if (userRepository.findByEmail(email).isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }

            // Create new user
            User newUser = new User();
            newUser.setEmail(email);
            userRepository.save(newUser);

            return ResponseEntity.status(HttpStatus.CREATED).build();

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/api/update")
    @PatchMapping("/api/update")
    @Operation(summary = "Update user presence", description = "Updates the user's last seen time, IP address, and port based on the client certificate. Requires mTLS authentication.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or certificate"),
            @ApiResponse(responseCode = "403", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Void> updateUser(HttpServletRequest request) {
        try {
            // Extract CN from client certificate
            String cn = extractCNFromCertificate(request);

            System.out.println("CN extracted: " + cn);

            if (cn == null) {
                System.out.println("CN is null");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }


            // Validate CN format (must be email-like)
            if (!isValidEmail(cn)) {
                System.out.println("CN does not match email pattern: " + cn);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            // Find user by email
            User user = userRepository.findByEmail(cn).orElse(null);

            if (user == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // Get client IP and port
            String clientIp = getClientIp(request);
            int clientPort = request.getRemotePort();

            // Get current time in nanoseconds since Unix epoch
            long currentTimeNanos = System.currentTimeMillis() * 1_000_000L +
                    (System.nanoTime() % 1_000_000L);

            // Update user record
            user.setLastSeen(currentTimeNanos);
            user.setIp(clientIp);
            user.setPort(clientPort);

            userRepository.save(user);

            // Broadcast the user update
            udpBroadcastService.broadcastUserUpdate(cn, currentTimeNanos, clientIp, clientPort);

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private String extractCNFromCertificate(HttpServletRequest request) {
        X509Certificate[] certs = (X509Certificate[]) request.getAttribute(
                "jakarta.servlet.request.X509Certificate"
        );

        System.out.println("Certs array: " + (certs == null ? "null" : certs.length));

        if (certs == null || certs.length == 0) {
            return null;
        }

        X509Certificate clientCert = certs[0];
        String dn = clientCert.getSubjectX500Principal().getName();
        System.out.println("DN: " + dn);

        // Extract CN from DN (format: CN=email@example.com,O=...)
        String[] parts = dn.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            System.out.println("Part: " + trimmed);
            if (trimmed.startsWith("CN=")) {
                String[] kv = trimmed.split("=", 2);
                System.out.println("KV: " + java.util.Arrays.toString(kv));
                if (kv.length == 2 && kv[0].trim().equals("CN")) {
                    return kv[1].trim();
                }
            }
        }

        return null;
    }

    private boolean isValidEmail(String email) {
        boolean matches = email != null && EMAIL_PATTERN.matcher(email).matches();
        System.out.println("Email validation for '" + email + "': " + matches);
        return matches;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
