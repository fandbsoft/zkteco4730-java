package main.zk;

import java.io.IOException;
import java.util.List;

/**
 * Pure Java TCP Socket Client for communicating directly with ZKTeco terminals
 * on Port 4370.
 * <p>
 * 100% pure Java java.net.Socket implementation without external COM / DLL dependencies.
 * Delegates to {@link ZkLegacySocketAdapter} for standard pure socket communication.
 */
public class ZkSocketClient implements AutoCloseable {
	private final ZkLegacySocketAdapter adapter;

	public ZkSocketClient(String host, int port, int password) {
		this.adapter = new ZkLegacySocketAdapter(host, port, password);
	}

	public ZkSocketClient(String host, int port, int password, int connectTimeoutMs, int readTimeoutMs) {
		this.adapter = new ZkLegacySocketAdapter(host, port, password, connectTimeoutMs, readTimeoutMs);
	}

	public void connect() throws IOException {
		adapter.connect();
	}

	public List<ZkAttendanceLog> readAttendanceLogs() throws IOException {
		return adapter.readAttendanceLogs();
	}

	public void disableDevice() throws IOException {
		adapter.disableDevice();
	}

	public void enableDevice() throws IOException {
		adapter.enableDevice();
	}

	public boolean isConnected() {
		return adapter.isConnected();
	}

	public int getSessionId() {
		return adapter.getSessionId();
	}

	@Override
	public void close() {
		adapter.close();
	}
}
