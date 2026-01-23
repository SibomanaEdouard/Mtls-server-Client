import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * UDP Broadcast Listener - Listens on port 6667 for broadcast messages
 * Usage: java UdpBroadcastListener
 */
public class UdpBroadcastListener {

    private static final int LISTEN_PORT = 6667;
    private static final int BUFFER_SIZE = 1024;

    public static void main(String[] args) {
        System.out.println("=== UDP Broadcast Listener Starting ===");
        System.out.println("Listening on port: " + LISTEN_PORT);
        System.out.println("Waiting for broadcast messages...\n");

        try (DatagramSocket socket = new DatagramSocket(LISTEN_PORT)) {
            byte[] buffer = new byte[BUFFER_SIZE];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                System.out.println("=== Received Broadcast ===");
                System.out.println("From: " + packet.getAddress() + ":" + packet.getPort());

                // Parse binary message
                try {
                    parseBinaryMessage(packet.getData(), packet.getLength());
                } catch (Exception e) {
                    System.err.println("Error parsing message: " + e.getMessage());
                }

                System.out.println();
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void parseBinaryMessage(byte[] data, int length) throws Exception {
        ByteArrayInputStream bais = new ByteArrayInputStream(data, 0, length);
        DataInputStream dis = new DataInputStream(bais);

        // Read email
        int emailLength = dis.readInt();
        byte[] emailBytes = new byte[emailLength];
        dis.readFully(emailBytes);
        String email = new String(emailBytes);

        // Read lastSeen
        long lastSeen = dis.readLong();

        // Read IP
        int ipLength = dis.readInt();
        byte[] ipBytes = new byte[ipLength];
        dis.readFully(ipBytes);
        String ip = new String(ipBytes);

        // Read port
        int port = dis.readInt();

        System.out.println("Email: " + email);
        System.out.println("Last Seen (nanoseconds): " + lastSeen);
        System.out.println("IP Address: " + ip);
        System.out.println("Port: " + port);
    }
}