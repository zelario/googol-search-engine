package projetosd.web;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import projetosd.Log;

/**
 * WebSocket server for broadcasting stats to connected clients.
 * Manages client connections and provides a method to broadcast stats in JSON format.
 */
public class StatsWebSocket extends WebSocketServer {

    /**
     * Set of active WebSocket connections. Thread-safe for concurrent access.
     */
    private final Set<WebSocket> connections = Collections.synchronizedSet(new HashSet<>());

    /**
     * Constructs a StatsWebSocket server on the specified port.
     * Binds to all local interfaces (0.0.0.0).
     * @param port the port to listen for WebSocket connections
     */
    public StatsWebSocket(int port) {
        super(new InetSocketAddress(port));
    }

    /**
     * Constructs a StatsWebSocket server on the specified host and port.
     * @param host the hostname or IP address to bind
     * @param port the port to listen for WebSocket connections
     */
    public StatsWebSocket(String host, int port) {
        super(new InetSocketAddress(host, port));
    }

    /**
     * Called when a new client connects.
     * @param conn the WebSocket connection
     * @param handshake the handshake data
     */
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        connections.add(conn);
    }

    /**
     * Called when a client disconnects.
     * @param conn the WebSocket connection
     * @param code the close code
     * @param reason the reason for closing
     * @param remote true if closed by remote host
     */
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        connections.remove(conn);
    }

    /**
     * Called when a message is received from a client.
     * @param conn the WebSocket connection
     * @param message the received message
     */
    @Override
    public void onMessage(WebSocket conn, String message) {}

    /**
     * Called when an error occurs on a connection.
     * @param conn the WebSocket connection (may be null)
     * @param ex the exception thrown
     */
    @Override
    public void onError(WebSocket conn, Exception ex) {
        if (conn != null) {
            connections.remove(conn);
        }
    }

    /**
     * Called when the WebSocket server starts.
     */
    @Override
    public void onStart() {
        Log.info("[WEBSOCKET] Stats WebSocket server started on: " + getAddress().getHostString() + ":" + getAddress().getPort());
    }

    /**
     * Broadcasts the given stats JSON string to all connected clients.
     * @param statsJson the stats data in JSON format
     */
    public void broadcastStats(String statsJson) {
        synchronized (connections) {
            for (WebSocket conn : connections) {
                if (conn.isOpen()) {
                    conn.send(statsJson);
                }
            }
        }
    }
}