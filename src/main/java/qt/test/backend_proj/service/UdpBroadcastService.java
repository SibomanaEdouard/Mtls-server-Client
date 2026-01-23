package qt.test.backend_proj.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

@Slf4j
@Service
public class UdpBroadcastService {

    private static final int BROADCAST_SOURCE_PORT = 6668;
    private static final int BROADCAST_DEST_PORT = 6667;
    private DatagramSocket socket;

    @PostConstruct
    public void init() throws SocketException {
        socket = new DatagramSocket(BROADCAST_SOURCE_PORT);
        socket.setBroadcast(true);
        log.info("UDP Broadcast service initialized on port {}", BROADCAST_SOURCE_PORT);
    }

    @PreDestroy
    public void cleanup() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
            log.info("UDP Broadcast service closed");
        }
    }

    public void broadcastUserUpdate(String email, long lastSeen, String ip, int port) {
        try {
            byte[] message = createBinaryMessage(email, lastSeen, ip, port);

            // Broadcast to 255.255.255.255
            InetAddress broadcastAddress = InetAddress.getByName("255.255.255.255");
            DatagramPacket packet = new DatagramPacket(
                    message,
                    message.length,
                    broadcastAddress,
                    BROADCAST_DEST_PORT
            );

            socket.send(packet);
            log.info("Broadcasted update for user: {} to port {}", email, BROADCAST_DEST_PORT);

        } catch (IOException e) {
            log.error("Failed to broadcast user update", e);
        }
    }

    /**
     * Creates a simple binary message format:
     * [email_length:4][email:variable][lastSeen:8][ip_length:4][ip:variable][port:4]
     */
    private byte[] createBinaryMessage(String email, long lastSeen, String ip, int port) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // Write email
        byte[] emailBytes = email.getBytes();
        dos.writeInt(emailBytes.length);
        dos.write(emailBytes);

        // Write lastSeen (nanoseconds)
        dos.writeLong(lastSeen);

        // Write IP
        byte[] ipBytes = ip.getBytes();
        dos.writeInt(ipBytes.length);
        dos.write(ipBytes);

        // Write port
        dos.writeInt(port);

        dos.flush();
        return baos.toByteArray();
    }
}
