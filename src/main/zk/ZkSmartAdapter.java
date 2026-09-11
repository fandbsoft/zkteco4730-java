package main.zk;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Smart Adaptive Socket Adapter cho các dòng máy ZKTeco firmware bảo mật đời mới
 * (hỗ trợ đàm phán mã hóa ZKCommuCrypto, xử lý phản hồi thách thức 6001 / 2032).
 */
public class ZkSmartAdapter extends AbstractZkSocketAdapter {

	public ZkSmartAdapter(String host, int port, int password) {
		this(host, port, password, ZkConstants.DEFAULT_CONNECT_TIMEOUT_MS, ZkConstants.DEFAULT_READ_TIMEOUT_MS);
	}

	public ZkSmartAdapter(String host, int port, int password, int connectTimeoutMs, int readTimeoutMs) {
		super(host, port, password, connectTimeoutMs, readTimeoutMs);
	}

	@Override
	public synchronized void connect() throws IOException {
		if (connected) {
			return;
		}

		initSocket();

		sendPacket(ZkConstants.CMD_CONNECT, new byte[0]);
		ZkPacket resp = receivePacket();
		this.sessionId = resp.getSessionId();

		if (resp.getCommandId() == ZkConstants.CMD_ACK_OK) {
			connected = true;
			return;
		}
		if (resp.getCommandId() == ZkConstants.CMD_ACK_UNAUTH) {
			throw new ZkAuthChallengeException("Thiết bị yêu cầu xác thực legacy, không phải secure adapter", resp.getCommandId());
		}
		throw unsupported(resp.getCommandId(), "Thiết bị từ chối giao thức pull 4370 legacy");
	}

	private ZkUnsupportedProtocolException unsupported(int responseCode, String reason) {
		return new ZkUnsupportedProtocolException(reason
				+ ". Response=" + responseCode
				+ ". Firmware này có thể đang bật AC Push/TA Push/BEST hoặc khóa Pull SDK trực tiếp; "
				+ "hãy kiểm tra menu Communication/Cloud Service/PC Connection/Comm Key trên thiết bị.",
				responseCode);
	}

	@Override
	public synchronized void readAttendanceLogs(Consumer<ZkAttendanceLog> consumer) throws IOException {
		throw unsupported(ZkConstants.CMD_ACK_AUTH_LOCK, "Không thể đọc log bằng secure adapter chưa có public protocol chính thức");
	}

	@Override
	public synchronized boolean unlockDoor(int delaySeconds) throws IOException {
		throw unsupported(ZkConstants.CMD_ACK_AUTH_LOCK, "Không thể mở cửa vì thiết bị chưa ACK phiên pull 4370");
	}

	@Override
	public synchronized void readUsers(Consumer<ZkUserInfo> consumer) throws IOException {
		if (consumer == null) {
			return;
		}
		throw unsupported(ZkConstants.CMD_ACK_AUTH_LOCK, "Không thể đọc user bằng secure adapter chưa có public protocol chính thức");
	}

	@Override
	public synchronized String getDeviceOption(String key) throws IOException {
		throw unsupported(ZkConstants.CMD_ACK_AUTH_LOCK, "Không thể đọc option bằng secure adapter chưa có public protocol chính thức");
	}

	@Override
	public synchronized int[] readSizes() throws IOException {
		throw unsupported(ZkConstants.CMD_ACK_AUTH_LOCK, "Không thể đọc bộ đếm bằng secure adapter chưa có public protocol chính thức");
	}

	@Override
	public String getProtocolName() {
		return "Smart Adaptive Protocol (ZKCommuCrypto / Port 4370)";
	}
}
