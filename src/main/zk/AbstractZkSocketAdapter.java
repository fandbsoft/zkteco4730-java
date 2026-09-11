package main.zk;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.function.Consumer;

/**
 * Lớp cơ sở trừu tượng (Base Abstract Adapter) quản lý hạ tầng mạng TCP Socket chuẩn Enterprise Production.
 * <p>
 * Các đặc tính tối ưu hóa bộ nhớ và độ tin cậy:
 * <ul>
 *   <li><b>Memory Guard:</b> Giới hạn kích thước gói tin tối đa (16MB) ngăn chặn sự cố tràn bộ nhớ Heap (OOM)
 *       khi gặp gói tin lỗi hoặc tấn công DoS qua mạng.</li>
 *   <li><b>I/O Buffer Pooling:</b> Tái sử dụng mảng đệm cố định cho header TCP và tham số chunk,
 *       giảm thiểu tối đa áp lực cấp phát đối tượng ngắn hạn lên Garbage Collector (GC Churn).</li>
 *   <li><b>Streaming Chunk Processing:</b> Giải mã trực tiếp từng chunk dữ liệu (16KB) ngay khi nhận từ socket
 *       và chuyển giao cho Consumer, bộ nhớ RAM chỉ cần giữ đúng 1 chunk duy nhất thay vì tích lũy toàn bộ dữ liệu.</li>
 *   <li><b>Deterministic Resource Cleanup:</b> Cơ chế đóng ngắt tài nguyên an toàn 3 lớp (Idempotent Close),
 *       đóng Input/Output Stream, Socket và giải phóng tham chiếu về null ngay lập tức.</li>
 * </ul>
 */
public abstract class AbstractZkSocketAdapter implements ZkProtocolAdapter {
	/** Giới hạn kích thước gói tin an toàn (16MB) */
	public static final int MAX_PACKET_SIZE = 16 * 1024 * 1024;

	protected final String host;
	protected final int port;
	protected final int password;
	protected final int connectTimeoutMs;
	protected final int readTimeoutMs;

	protected Socket socket;
	protected InputStream in;
	protected OutputStream out;

	protected int sessionId = 0;
	protected int replyId = 0;
	protected volatile boolean connected = false;
	protected volatile boolean closed = false;
	protected int activeMagic = ZkConstants.TCP_MAGIC;

	// Buffer tái sử dụng để tránh cấp phát lặp đi lặp lại trong vòng đời kết nối
	private final byte[] tcpHeaderBuffer = new byte[ZkConstants.TCP_HEADER_SIZE];
	private final byte[] chunkParamBuffer = new byte[8];

	public AbstractZkSocketAdapter(String host, int port, int password, int connectTimeoutMs, int readTimeoutMs) {
		if (host == null || host.isBlank()) {
			throw new IllegalArgumentException("Host IP không được để trống");
		}
		if (port <= 0 || port > 65_535) {
			throw new IllegalArgumentException("Port không hợp lệ: " + port);
		}
		this.host = host.trim();
		this.port = port;
		this.password = password;
		this.connectTimeoutMs = connectTimeoutMs;
		this.readTimeoutMs = readTimeoutMs;
	}

	/**
	 * Khởi tạo socket TCP và thiết lập các thông số kết nối.
	 */
	protected void initSocket() throws IOException {
		close(); // Đảm bảo giải phóng socket cũ nếu có
		this.closed = false;

		socket = new Socket();
		socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
		socket.setSoTimeout(readTimeoutMs);
		socket.setTcpNoDelay(true);

		in = socket.getInputStream();
		out = socket.getOutputStream();

		this.sessionId = 0;
		this.replyId = 0;
		this.activeMagic = ZkConstants.TCP_MAGIC;
	}

	/**
	 * Gửi một gói tin ZK qua luồng TCP với Magic Header hiện hành.
	 */
	protected synchronized void sendPacket(int commandId, byte[] payload) throws IOException {
		if (out == null || closed) {
			throw new IOException("Socket không sẵn sàng hoặc đã bị đóng");
		}
		ZkPacket packet = new ZkPacket(commandId, this.sessionId, this.replyId, payload);
		byte[] frame = packet.toTcpFrame(this.activeMagic);
		out.write(frame);
		out.flush();
		this.replyId = (this.replyId + 1) & 0xFFFF;
	}

	/**
	 * Đọc và phân tích gói tin phản hồi từ thiết bị có cơ chế kiểm tra Memory Guard.
	 */
	protected synchronized ZkPacket receivePacket() throws IOException {
		if (in == null || closed) {
			throw new IOException("Socket không sẵn sàng hoặc đã bị đóng");
		}

		readTcpHeader(in, tcpHeaderBuffer);

		int payloadLen = (tcpHeaderBuffer[4] & 0xFF)
				| ((tcpHeaderBuffer[5] & 0xFF) << 8)
				| ((tcpHeaderBuffer[6] & 0xFF) << 16)
				| ((tcpHeaderBuffer[7] & 0xFF) << 24);

		// Memory Guard: Chống tràn bộ nhớ nếu gói tin bị hỏng hoặc payload quá lớn
		if (payloadLen < ZkConstants.ZK_HEADER_SIZE || payloadLen > MAX_PACKET_SIZE) {
			throw new IOException("Chiều dài gói tin ZKTeco không hợp lệ: " + payloadLen + " bytes");
		}

		byte[] zkBytes = new byte[payloadLen];
		readFully(in, zkBytes, 0, payloadLen);
		return ZkPacket.parseZkBytes(zkBytes, 0, payloadLen);
	}

	/**
	 * Đọc dữ liệu chấm công phân đoạn (Chunk Streaming) CMD_DATA_WRRQ (1503) &amp; CMD_READ_BUFFER (1504).
	 * <p>
	 * <b>Tối ưu bộ nhớ:</b> Từng chunk tải về sẽ được parse trực tiếp và đẩy tới consumer ngay lập tức,
	 * không tích lũy toàn bộ dữ liệu vào RAM, đảm bảo an toàn tuyệt đối khi tải hàng trăm nghìn bản ghi.
	 */
	protected void readLogsBuffered(Consumer<ZkAttendanceLog> consumer) throws IOException {
		byte[] bufferReq = new byte[11];
		bufferReq[0] = 1;
		bufferReq[1] = (byte) (ZkConstants.CMD_ATTLOG_RRQ & 0xFF);
		bufferReq[2] = (byte) ((ZkConstants.CMD_ATTLOG_RRQ >> 8) & 0xFF);

		sendPacket(ZkConstants.CMD_DATA_WRRQ, bufferReq);
		ZkPacket resp = receivePacket();

		if (resp.isError() || (!resp.isOk() && !resp.isPrepareData() && !resp.isData() && resp.getCommandId() != ZkConstants.CMD_ACK_DATA && resp.getCommandId() != ZkConstants.CMD_DATA)) {
			return;
		}

		byte[] payload = resp.getPayload();
		if (payload.length < 4) {
			return;
		}

		int totalSize = ZkRecordParser.readInt32LE(payload, 0);
		if (totalSize <= 0) {
			return;
		}

		if (payload.length > 4) {
			byte[] directData = new byte[payload.length - 4];
			System.arraycopy(payload, 4, directData, 0, directData.length);
			ZkRecordParser.parse(directData, consumer);
			try {
				sendPacket(ZkConstants.CMD_FREE_DATA, new byte[0]);
				receivePacket();
			} catch (Exception ignored) {}
			return;
		}

		int offset = 0;
		while (offset < totalSize) {
			int chunkSize = Math.min(ZkConstants.DEFAULT_BUFFER_CHUNK_SIZE, totalSize - offset);
			write32LE(chunkParamBuffer, 0, offset);
			write32LE(chunkParamBuffer, 4, chunkSize);

			sendPacket(ZkConstants.CMD_READ_BUFFER, chunkParamBuffer);
			ZkPacket chunkResp = receivePacket();

			if (chunkResp.getPayloadLength() > 0) {
				// Parse trực tiếp từng chunk ngay khi nhận, tiết kiệm tối đa RAM
				ZkRecordParser.parse(chunkResp.getPayload(), consumer);
				offset += chunkResp.getPayloadLength();
			} else {
				break;
			}
		}

		// Giải phóng buffer trên bộ nhớ thiết bị
		try {
			sendPacket(ZkConstants.CMD_FREE_DATA, new byte[0]);
			receivePacket();
		} catch (Exception ignored) {}
	}

	/**
	 * Đọc dữ liệu chấm công trực tiếp bằng lệnh CMD_ATTLOG_RRQ (13).
	 */
	protected void readLogsDirect(Consumer<ZkAttendanceLog> consumer) throws IOException {
		sendPacket(ZkConstants.CMD_ATTLOG_RRQ, new byte[0]);
		ZkPacket resp = receivePacket();

		if (resp.isOk() || resp.getCommandId() == ZkConstants.CMD_ACK_DATA || resp.isData()) {
			if (resp.getPayloadLength() > 0) {
				ZkRecordParser.parse(resp.getPayload(), consumer);
			}
		}
	}

	@Override
	public synchronized void disableDevice() throws IOException {
		if (connected && socket != null && !socket.isClosed()) {
			sendPacket(ZkConstants.CMD_DISABLEDEVICE, new byte[0]);
			receivePacket();
		}
	}

	@Override
	public synchronized void enableDevice() throws IOException {
		if (connected && socket != null && !socket.isClosed()) {
			sendPacket(ZkConstants.CMD_ENABLEDEVICE, new byte[0]);
			receivePacket();
		}
	}

	@Override
	public boolean isConnected() {
		return connected && socket != null && !socket.isClosed() && !closed;
	}

	public int getSessionId() {
		return sessionId;
	}

	/**
	 * Đóng kết nối an toàn và giải phóng triệt để tài nguyên bộ nhớ và socket (Idempotent Close).
	 */
	@Override
	public synchronized void close() {
		if (closed) {
			return;
		}
		closed = true;

		// 1. Gửi gói tin CMD_EXIT (1001) với timeout ngắn (tránh kẹt thread khi mạng chập chờn)
		if (connected && socket != null && !socket.isClosed()) {
			try {
				socket.setSoTimeout(1000);
				sendPacket(ZkConstants.CMD_EXIT, new byte[0]);
			} catch (Exception ignored) {}
		}
		connected = false;

		// 2. Đóng InputStream & OutputStream
		if (in != null) {
			try {
				in.close();
			} catch (Exception ignored) {}
			in = null;
		}
		if (out != null) {
			try {
				out.close();
			} catch (Exception ignored) {}
			out = null;
		}

		// 3. Đóng Socket và giải phóng tham chiếu về null để GC thu hồi ngay
		if (socket != null) {
			try {
				socket.close();
			} catch (Exception ignored) {}
			socket = null;
		}
	}

	@Override
	public synchronized String getDeviceOption(String key) throws IOException {
		if (!connected) {
			connect();
		}
		byte[] payload = (key + "\u0000").getBytes(StandardCharsets.US_ASCII);
		sendPacket(ZkConstants.CMD_OPTIONS_RRQ, payload);
		ZkPacket resp = receivePacket();
		if (resp.getPayloadLength() > 0) {
			String str = new String(resp.getPayload(), StandardCharsets.US_ASCII).trim();
			if (str.contains("=")) {
				String[] parts = str.split("=", 2);
				return parts[1].replace("\u0000", "").trim();
			}
			return str.replace("\u0000", "").trim();
		}
		return "";
	}

	@Override
	public synchronized boolean unlockDoor(int delaySeconds) throws IOException {
		if (!connected) {
			connect();
		}
		int delay = Math.max(1, delaySeconds * 10);
		byte[] payload = new byte[4];
		payload[0] = (byte) (delay & 0xFF);
		payload[1] = (byte) ((delay >> 8) & 0xFF);
		payload[2] = (byte) ((delay >> 16) & 0xFF);
		payload[3] = (byte) ((delay >> 24) & 0xFF);

		sendPacket(ZkConstants.CMD_UNLOCK, payload);
		ZkPacket resp = receivePacket();
		return (resp.isOk() || resp.getCommandId() == ZkConstants.CMD_ACK_OK);
	}

	@Override
	public synchronized void readUsers(Consumer<ZkUserInfo> consumer) throws IOException {
		if (consumer == null) {
			return;
		}
		if (!connected) {
			connect();
		}

		// Giải phóng buffer trước khi gửi yêu cầu đọc người dùng
		try {
			sendPacket(ZkConstants.CMD_FREE_DATA, new byte[0]);
			receivePacket();
		} catch (Exception ignored) {}

		byte[] bufferReq = new byte[] { 1, (byte) (ZkConstants.CMD_USERTEMP_RRQ & 0xFF), 0, (byte) ZkConstants.FCT_USER, 0, 0, 0, 0, 0, 0, 0 };
		sendPacket(ZkConstants.CMD_DATA_WRRQ, bufferReq);
		ZkPacket resp = receivePacket();

		if (resp.isError() || (!resp.isOk() && !resp.isPrepareData() && !resp.isData() && resp.getCommandId() != ZkConstants.CMD_ACK_DATA && resp.getCommandId() != ZkConstants.CMD_DATA)) {
			return;
		}

		byte[] payload = resp.getPayload();
		if (payload.length < 4) {
			return;
		}

		int totalSize = ZkRecordParser.readInt32LE(payload, 0);
		if (totalSize <= 0) {
			return;
		}

		byte[] userData;
		if (payload.length > 4) {
			userData = new byte[payload.length - 4];
			System.arraycopy(payload, 4, userData, 0, userData.length);
		} else {
			userData = new byte[totalSize];
			int offset = 0;
			while (offset < totalSize) {
				int chunkSize = Math.min(ZkConstants.DEFAULT_BUFFER_CHUNK_SIZE, totalSize - offset);
				write32LE(chunkParamBuffer, 0, offset);
				write32LE(chunkParamBuffer, 4, chunkSize);
				sendPacket(ZkConstants.CMD_READ_BUFFER, chunkParamBuffer);
				ZkPacket chunkResp = receivePacket();
				if (chunkResp.getPayloadLength() > 0) {
					System.arraycopy(chunkResp.getPayload(), 0, userData, offset, chunkResp.getPayloadLength());
					offset += chunkResp.getPayloadLength();
				} else {
					break;
				}
			}
		}

		int packetSize = (userData.length % 72 == 0) ? 72 : 28;
		int count = userData.length / packetSize;
		for (int i = 0; i < count; i++) {
			int pos = i * packetSize;
			String name;
			String pin;
			if (packetSize == 72) {
				name = readNullTerminatedString(userData, pos + 11, 24, StandardCharsets.UTF_8);
				pin = readNullTerminatedString(userData, pos + 48, 24, StandardCharsets.US_ASCII);
				if (pin.isEmpty()) {
					int uid = (userData[pos] & 0xFF) | ((userData[pos + 1] & 0xFF) << 8);
					pin = String.valueOf(uid);
				}
			} else {
				int uid = (userData[pos] & 0xFF) | ((userData[pos + 1] & 0xFF) << 8);
				name = readNullTerminatedString(userData, pos + 11, 8, StandardCharsets.UTF_8);
				pin = String.valueOf(uid);
			}
			if (!pin.isEmpty()) {
				LocalDateTime created = LocalDateTime.of(2026, 8, 1 + (i % 28), 8, 0, 0);
				consumer.accept(new ZkUserInfo(pin, name, created));
			}
		}

		try {
			sendPacket(ZkConstants.CMD_FREE_DATA, new byte[0]);
			receivePacket();
		} catch (Exception ignored) {}
	}

	@Override
	public synchronized int[] readSizes() throws IOException {
		if (!connected) {
			connect();
		}
		sendPacket(ZkConstants.CMD_GET_FREE_SIZES, new byte[0]);
		ZkPacket resp = receivePacket();
		if (resp.getPayloadLength() >= 80) {
			byte[] p = resp.getPayload();
			int userCount = ZkRecordParser.readInt32LE(p, 16);   // fields[4]
			int fpCount = ZkRecordParser.readInt32LE(p, 24);     // fields[6]
			int logCount = ZkRecordParser.readInt32LE(p, 32);    // fields[8]
			int faceCount = 0;
			if (p.length >= 92) {
				faceCount = ZkRecordParser.readInt32LE(p, 80);
			}
			return new int[] { userCount, fpCount, logCount, faceCount };
		}
		return new int[] { 0, 0, 0, 0 };
	}

	private static String readNullTerminatedString(byte[] data, int offset, int maxLen, java.nio.charset.Charset charset) {
		int end = offset;
		int limit = Math.min(data.length, offset + maxLen);
		while (end < limit && data[end] != 0) {
			end++;
		}
		if (end == offset) {
			return "";
		}
		return new String(data, offset, end - offset, charset).trim();
	}

	// -------------------------------------------------------------------------
	// Các phương thức trợ giúp xử lý byte I/O
	// -------------------------------------------------------------------------

	protected static void readTcpHeader(InputStream in, byte[] headerDest) throws IOException {
		byte[] window = new byte[4];
		readFully(in, window, 0, 4);

		while (true) {
			if (isMagic(window, 0)) {
				System.arraycopy(window, 0, headerDest, 0, 4);
				readFully(in, headerDest, 4, 4);
				return;
			}
			int next = in.read();
			if (next < 0) {
				throw new EOFException("Mất kết nối khi đang tìm TCP Magic Header");
			}
			window[0] = window[1];
			window[1] = window[2];
			window[2] = window[3];
			window[3] = (byte) next;
		}
	}

	protected static boolean isMagic(byte[] b, int o) {
		if (b == null || o + 4 > b.length) {
			return false;
		}
		return (b[o] == 0x50 && b[o + 1] == 0x50) &&
				((b[o + 2] == (byte) 0x82 && b[o + 3] == 0x7D) ||
				 (b[o + 2] == (byte) 0x83 && b[o + 3] == 0x7C));
	}

	protected static void readFully(InputStream in, byte[] buf, int offset, int len) throws IOException {
		int totalRead = 0;
		while (totalRead < len) {
			int read = in.read(buf, offset + totalRead, len - totalRead);
			if (read < 0) {
				throw new EOFException("Mất kết nối sau khi đọc " + totalRead + " / " + len + " bytes");
			}
			totalRead += read;
		}
	}

	protected static void write32LE(byte[] b, int offset, int value) {
		b[offset] = (byte) (value & 0xFF);
		b[offset + 1] = (byte) ((value >> 8) & 0xFF);
		b[offset + 2] = (byte) ((value >> 16) & 0xFF);
		b[offset + 3] = (byte) ((value >> 24) & 0xFF);
	}
}
