package zk4370new;

import java.io.IOException;

/**
 * Class chuyen trach dieu khien mo cua (Access Control / Door Unlock)
 * cho firmware moi ZKTeco qua secure raw socket TCP 4370.
 */
public class ZkNewUnlock implements AutoCloseable {
	public static final int DEFAULT_DELAY_SECONDS = ZkNewConstants.DEFAULT_UNLOCK_DELAY_SECONDS;

	private final String ip;
	private final int port;
	private final int password;
	private final int machineNumber;

	private ZkNewSocketClient client;

	public ZkNewUnlock(String ip, int port, int password, int machineNumber) {
		if (ip == null || ip.isBlank()) {
			throw new IllegalArgumentException("IP khong duoc de trong");
		}
		this.ip = ip.trim();
		this.port = (port > 0) ? port : ZkNewConstants.DEFAULT_PORT;
		this.password = password;
		this.machineNumber = (machineNumber > 0) ? machineNumber : 1;
	}

	public ZkNewUnlock(String ip, int port, int password) {
		this(ip, port, password, 1);
	}

	public ZkNewUnlock(String ip, int password) {
		this(ip, ZkNewConstants.DEFAULT_PORT, password, 1);
	}

	public ZkNewUnlock(String ip) {
		this(ip, ZkNewConstants.DEFAULT_PORT, 0, 1);
	}

	/**
	 * Mo cua voi thoi gian relay tinh bang giay.
	 *
	 * @param delaySeconds so giay mo relay, toi thieu 1 giay
	 * @return true neu thiet bi chap nhan lenh mo cua
	 * @throws IOException neu loi ket noi hoac xac thuc
	 */
	public synchronized boolean unlock(int delaySeconds) throws IOException {
		ensureConnected();
		return client.unlockDoor(delaySeconds);
	}

	public synchronized boolean unlockDoor(int delaySeconds) throws IOException {
		return unlock(delaySeconds);
	}

	public synchronized boolean unlock() throws IOException {
		return unlock(DEFAULT_DELAY_SECONDS);
	}

	public int getMachineNumber() {
		return machineNumber;
	}

	public static boolean quickUnlock(String ip, int port, int password, int delaySeconds) {
		try (ZkNewUnlock unlocker = new ZkNewUnlock(ip, port, password)) {
			return unlocker.unlock(delaySeconds);
		} catch (Exception e) {
			return false;
		}
	}

	private void ensureConnected() throws IOException {
		if (client != null && client.isConnected()) {
			return;
		}
		if (client != null) {
			client.close();
		}
		client = new ZkNewSocketClient(ip, port, password);
		client.connect();
	}

	@Override
	public synchronized void close() {
		if (client != null) {
			client.close();
			client = null;
		}
	}
}
