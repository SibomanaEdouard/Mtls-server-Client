package main

import (
	"encoding/binary"
	"flag"
	"fmt"
	"net"
	"os"
	"time"
)

const (
	defaultPort = 6667
)

func main() {
	// Command-line flag for port
	port := flag.Int("port", defaultPort, "UDP port to listen on")
	flag.Parse()

	addr := fmt.Sprintf(":%d", *port)
	fmt.Printf("=== Go UDP Broadcast Listener ===\n")
	fmt.Printf("Listening on port: %d\n", *port)
	fmt.Println("Waiting for broadcast messages...")
	fmt.Println()

	// Create UDP connection
	conn, err := net.ListenPacket("udp", addr)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error creating UDP listener: %v\n", err)
		os.Exit(1)
	}
	defer conn.Close()

	buffer := make([]byte, 1024)

	for {
		// Read UDP packet
		n, addr, err := conn.ReadFrom(buffer)
		if err != nil {
			fmt.Fprintf(os.Stderr, "Error reading packet: %v\n", err)
			continue
		}

		fmt.Println("=== Received Broadcast ===")
		fmt.Printf("From: %s\n", addr.String())

		// Parse binary message
		if err := parseBinaryMessage(buffer[:n]); err != nil {
			fmt.Printf("Error parsing message: %v\n", err)
		}

		fmt.Println()
	}
}

func parseBinaryMessage(data []byte) error {
	if len(data) < 16 { // Minimum size: 4 + 4 + 8 + 4 + 4 = 24, but check for at least headers
		return fmt.Errorf("packet too short")
	}

	offset := 0

	// Read email length (4 bytes, big-endian)
	if offset+4 > len(data) {
		return fmt.Errorf("insufficient data for email length")
	}
	emailLen := int(binary.BigEndian.Uint32(data[offset:offset+4]))
	offset += 4

	// Read email
	if offset+emailLen > len(data) {
		return fmt.Errorf("insufficient data for email")
	}
	email := string(data[offset : offset+emailLen])
	offset += emailLen

	// Read lastSeen (8 bytes, big-endian, nanoseconds since Unix epoch)
	if offset+8 > len(data) {
		return fmt.Errorf("insufficient data for lastSeen")
	}
	lastSeenNanos := int64(binary.BigEndian.Uint64(data[offset:offset+8]))
	offset += 8

	// Convert nanoseconds to time
	lastSeenTime := time.Unix(0, lastSeenNanos)

	// Read IP length (4 bytes, big-endian)
	if offset+4 > len(data) {
		return fmt.Errorf("insufficient data for IP length")
	}
	ipLen := int(binary.BigEndian.Uint32(data[offset:offset+4]))
	offset += 4

	// Read IP
	if offset+ipLen > len(data) {
		return fmt.Errorf("insufficient data for IP")
	}
	ip := string(data[offset : offset+ipLen])
	offset += ipLen

	// Read port (4 bytes, big-endian)
	if offset+4 > len(data) {
		return fmt.Errorf("insufficient data for port")
	}
	port := int(binary.BigEndian.Uint32(data[offset:offset+4]))

	// Print parsed data
	fmt.Printf("Email: %s\n", email)
	fmt.Printf("Last Seen: %s\n", lastSeenTime.Format("2006-01-02 15:04:05.000000 MST"))
	fmt.Printf("IP Address: %s\n", ip)
	fmt.Printf("Port: %d\n", port)

	return nil
}