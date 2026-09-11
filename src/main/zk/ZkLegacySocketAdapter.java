package main.zk;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Adapter TCP Socket thuần Java (Pure Java TCP Socket) cho các dòng máy chấm công
 * ZKTeco tiêu chuẩn (cổng 4370) đạt chuẩn Enterprise Production.
 */
public class ZkLegacySocketAdapter extends AbstractZkSocketAdapter {

	public ZkLegacySocketAdapter(String host, int port, int password) {
		this(host, port, password, ZkConstants.DEFAULT_CONNECT_TIMEOUT_MS, ZkConstants.DEFAULT_READ_TIMEOUT_MS);
	}

	public ZkLegacySocketAdapter(String host, int port, int password, int connectTimeoutMs, int readTimeoutMs) {
		super(host, port, password, connectTimeoutMs, readTimeoutMs);
	}

	@Override
	public synchronized void connect() throws IOException {
		if (connected) {
			return;
		}

		initSocket();

		// 1. Gửi lệnh CMD_CONNECT (1000)
		sendPacket(ZkConstants.CMD_CONNECT, new byte[0]);
		ZkPacket response = receivePacket();

		int respCode = response.getCommandId();
		this.sessionId = response.getSessionId();

		// Nhận diện mã thách thức bảo mật firmware mới
		if (respCode == ZkConstants.CMD_ACK_CHALLENGE_6001 || respCode == ZkConstants.CMD_ACK_AUTH_LOCK) {
			throw new ZkAuthChallengeException("Thiết bị phản hồi mã thách thức bảo mật: " + respCode, respCode);
		}

		if (respCode == ZkConstants.CMD_ACK_OK) {
			if (password > 0) {
				authenticate(password);
			}
		} else if (respCode == ZkConstants.CMD_ACK_UNAUTH) {
			authenticate(password);
		} else {
			throw new ZkException("Thiết bị phản hồi mã không hợp lệ: " + respCode, respCode);
		}

		connected = true;
	}

	/**
	 * Xác thực phiên bằng CMD_AUTH (1102) và thuật toán MakeKey.
	 */
	public void authenticate(int pwd) throws IOException {
		// Thử lần 1: MakeKey với ticks = 50
		byte[] keyPayload = ZkCrypto.makeCommKey(pwd, this.sessionId, 50);
		sendPacket(ZkConstants.CMD_AUTH, keyPayload);
		ZkPacket authResp = receivePacket();

		if (authResp.getCommandId() == ZkConstants.CMD_ACK_OK) {
			return;
		}

		// Thiết bị từ chối xác thực truyền thống
		if (authResp.getCommandId() == ZkConstants.CMD_ACK_AUTH_LOCK || authResp.getCommandId() == ZkConstants.CMD_ACK_CHALLENGE_6001) {
			throw new ZkAuthChallengeException("Giao thức xác thực cũ bị từ chối với mã: " + authResp.getCommandId(), authResp.getCommandId());
		}

		// Thử lần 2: MakeKey với ticks = 0
		keyPayload = ZkCrypto.makeCommKey(pwd, this.sessionId, 0);
		sendPacket(ZkConstants.CMD_AUTH, keyPayload);
		authResp = receivePacket();

		if (authResp.getCommandId() == ZkConstants.CMD_ACK_OK) {
			return;
		}

		if (authResp.getCommandId() == ZkConstants.CMD_ACK_AUTH_LOCK || authResp.getCommandId() == ZkConstants.CMD_ACK_CHALLENGE_6001) {
			throw new ZkAuthChallengeException("Giao thức xác thực cũ bị từ chối với mã: " + authResp.getCommandId(), authResp.getCommandId());
		}

		throw new ZkException("Xác thực ZKTeco thất bại (Mã lỗi: " + authResp.getCommandId() + ")", authResp.getCommandId());
	}

	@Override
	public synchronized void readAttendanceLogs(Consumer<ZkAttendanceLog> consumer) throws IOException {
		if (consumer == null) {
			return;
		}
		if (!connected) {
			connect();
		}

		try {
			disableDevice();
		} catch (Exception ignored) {}

		try {
			// Thử đọc phân đoạn (Chunked Streaming) CMD_DATA_WRRQ (1503)
			readLogsBuffered(consumer);
		} catch (ZkAuthChallengeException ex) {
			throw ex;
		} catch (Exception ignored) {
			// Dự phòng đọc trực tiếp CMD_ATTLOG_RRQ (13)
			readLogsDirect(consumer);
		} finally {
			try {
				enableDevice();
			} catch (Exception ignored) {}
		}
	}

	@Override
	public String getProtocolName() {
		return "Pure Java TCP Socket (Legacy / Port 4370)";
	}
}
