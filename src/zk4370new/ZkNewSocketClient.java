package zk4370new;

import java.io.EOFException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Client TCP Raw Socket chuyên dụng cho các dòng máy ZKTeco Firmware mới (Modern Firmware / SenseFace / Linux OS)
 * hoạt động trên cổng 4370.
 * <p>
 * Đặc điểm:
 * <ul>
 *   <li>100% Pure Java, không phụ thuộc bất kỳ file DLL, COM hay thư viện ngoài nào.</li>
 *   <li>Nhận diện và xử lý cơ chế bắt tay hiện đại (Challenge 6001, Auth Lock 2032).</li>
 *   <li>Cung cấp công cụ chẩn đoán chuyên sâu (Deep Diagnostic Probe) cho thiết bị.</li>
 * </ul>
 */
public class ZkNewSocketClient implements AutoCloseable {

	public enum ConnectionState {
		DISCONNECTED,
		CONNECTED_STANDARD,
		CHALLENGE_6001_RECEIVED,
		SECURE_HANDSHAKE_OK,
		AUTH_LOCK_2032,
		AUTH_SUCCESS,
		AUTH_FAILED_UNAUTH,
		ERROR
	}

	public static class ProbeReport {
		public boolean tcpConnected = false;
		public int responseCode = -1;
		public int sessionId = 0;
		public int replyId = 0;
		public String rawHex = "";
		public ConnectionState state = ConnectionState.DISCONNECTED;
		public int authResponseCode = -1;
		public int authExtResponseCode = -1;
		public boolean secureHandshakeOk = false;
		public boolean encryptedChannelActive = false;
		public String diagnosticMessage = "";

		@Override
		public String toString() {
			return String.format(
					"ProbeReport [TCP=%b, State=%s, RespCode=%d, SessID=%d, AuthResp=%d]%n" +
					"  -> Chi tiet: %s",
					tcpConnected, state, responseCode, sessionId, authResponseCode, diagnosticMessage);
		}
	}

	private final String host;
	private final int port;
	private final int password;
	private final String passwordText;
	private final int connectTimeoutMs;
	private final int readTimeoutMs;

	private Socket socket;
	private InputStream in;
	private OutputStream out;

	private int sessionId = 0;
	private int replyId = 0;
	private boolean secureMode = false;
	private byte[] aesKey = null;
	private List<ZkNewAttendanceLog> attendanceLogCache = null;
	private volatile boolean connected = false;
	private volatile boolean closed = false;
	private ConnectionState connectionState = ConnectionState.DISCONNECTED;

	public ZkNewSocketClient(String host, int port, int password) {
		this(host, port, password, ZkNewConstants.DEFAULT_CONNECT_TIMEOUT_MS, ZkNewConstants.DEFAULT_READ_TIMEOUT_MS);
	}

	public ZkNewSocketClient(String host, int port, int password, int connectTimeoutMs, int readTimeoutMs) {
		if (host == null || host.isBlank()) {
			throw new IllegalArgumentException("Địa chỉ IP không được để trống");
		}
		this.host = host.trim();
		this.port = (port > 0) ? port : ZkNewConstants.DEFAULT_PORT;
		this.password = password;
		this.passwordText = Integer.toString(password);
		this.connectTimeoutMs = connectTimeoutMs;
		this.readTimeoutMs = readTimeoutMs;
	}

	/**
	 * Thực hiện chẩn đoán Raw Socket toàn diện với thiết bị:
	 * Kết nối TCP, gửi CMD_CONNECT (1000), phân tích phản hồi (2000, 6001, 2005),
	 * thử xác thực Comm Key và tạo báo cáo chi tiết.
	 */
	public ProbeReport probe() {
		ProbeReport report = new ProbeReport();
		try {
			close();
			this.closed = false;

			// 1. Kết nối TCP Raw Socket
			socket = new Socket();
			socket.setSoTimeout(readTimeoutMs);
			socket.setTcpNoDelay(true);
			socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);

			in = socket.getInputStream();
			out = socket.getOutputStream();
			report.tcpConnected = true;
			this.secureMode = false;
			this.aesKey = null;
			this.attendanceLogCache = null;

			// 2. Gửi CMD_CONNECT (1000)
			this.sessionId = 0;
			this.replyId = 0;
			sendPacket(ZkNewConstants.CMD_CONNECT, new byte[0]);

			// 3. Nhận phản hồi
			ZkNewPacket resp = receivePacket();
			report.responseCode = resp.getCommandId();
			report.sessionId = resp.getSessionId();
			report.replyId = resp.getReplyId();
			this.sessionId = resp.getSessionId();

			byte[] frame = resp.toTcpFrame(ZkNewConstants.TCP_MAGIC);
			StringBuilder sb = new StringBuilder();
			for (byte b : frame) {
				sb.append(String.format("%02X ", b));
			}
			report.rawHex = sb.toString().trim();

			if (resp.isOk()) {
				report.state = ConnectionState.CONNECTED_STANDARD;
				report.diagnosticMessage = "Thiết bị phản hồi CMD_ACK_OK (2000). Chấp nhận kết nối trực tiếp!";
				this.connected = true;
				return report;
			}

			if (resp.isAuthChallenge6001()) {
				report.state = ConnectionState.CHALLENGE_6001_RECEIVED;
				report.diagnosticMessage = "Thiết bị phản hồi thách thức bảo mật 6001 (Firmware mới nhận diện). " +
						"Đang bắt tay ZKCommuCrypto (10063/10064/10065)...";
				try {
					negotiateSecureSession(report);
					report.secureHandshakeOk = true;
					report.encryptedChannelActive = true;
					report.state = ConnectionState.SECURE_HANDSHAKE_OK;
					report.diagnosticMessage = "Bắt tay crypto thành công; đang xác thực Comm Key trong tunnel AES...";
					if (authenticateModernSession(report)) {
						report.state = ConnectionState.AUTH_SUCCESS;
						report.diagnosticMessage = "Bắt tay crypto và xác thực Comm Key thành công. Thiết bị sẵn sàng nhận lệnh 4370 firmware mới.";
						this.connected = true;
					}
				} catch (Exception exSecure) {
					report.state = ConnectionState.ERROR;
					report.diagnosticMessage += " Lỗi secure handshake/auth: " + exSecure.getClass().getSimpleName() + " - " + exSecure.getMessage();
				}
				return report;
			}

			if (resp.isUnauth()) {
				report.state = ConnectionState.AUTH_FAILED_UNAUTH;
				report.diagnosticMessage = "Thiết bị yêu cầu Comm Key (2005 UNAUTH legacy).";
				return report;
			}

			report.state = ConnectionState.ERROR;
			report.diagnosticMessage = "Phản hồi từ thiết bị không khớp chuẩn: " + resp.getCommandId();

		} catch (Exception e) {
			report.diagnosticMessage = "Lỗi kết nối Socket: " + e.getClass().getSimpleName() + " - " + e.getMessage();
		}
		return report;
	}

	/**
	 * Kết nối socket và khởi tạo phiên làm việc với thiết bị.
	 */
	public synchronized void connect() throws IOException {
		ProbeReport r = probe();
		this.connectionState = r.state;
		if (r.state != ConnectionState.CONNECTED_STANDARD && r.state != ConnectionState.AUTH_SUCCESS) {
			throw new IOException("Không thể thiết lập phiên với thiết bị mới (" + host + ":" + port + "): " + r.diagnosticMessage);
		}
		connected = true;
	}

	/**
	 * Gửi một gói tin ZK qua luồng TCP.
	 */
	public synchronized void sendPacket(int commandId, byte[] payload) throws IOException {
		sendPacket(commandId, payload, this.secureMode);
	}

	private synchronized void sendPacket(int commandId, byte[] payload, boolean encrypted) throws IOException {
		if (out == null || closed) {
			throw new IOException("Socket chưa khởi tạo hoặc đã bị đóng");
		}
		ZkNewPacket packet = new ZkNewPacket(commandId, this.sessionId, this.replyId, payload);
		byte[] frame;
		if (encrypted) {
			if (aesKey == null) {
				throw new IOException("Secure mode đã bật nhưng AES key chưa sẵn sàng");
			}
			try {
				frame = ZkNewCrypto.encryptSecureTcpFrame(packet.toZkBytes(), aesKey);
			} catch (Exception e) {
				throw new IOException("Không thể mã hóa secure frame", e);
			}
		} else {
			frame = packet.toTcpFrame(ZkNewConstants.TCP_MAGIC);
		}
		out.write(frame);
		out.flush();
		this.replyId = (this.replyId + 1) & 0xFFFF;
	}

	/**
	 * Đọc một gói tin phản hồi từ thiết bị qua socket.
	 */
	public synchronized ZkNewPacket receivePacket() throws IOException {
		if (in == null || closed) {
			throw new IOException("Socket chưa khởi tạo hoặc đã bị đóng");
		}

		byte[] tcpHeader = new byte[ZkNewConstants.TCP_HEADER_SIZE];
		int magic = readTcpHeader(in, tcpHeader);

		int payloadLen = (tcpHeader[4] & 0xFF)
				| ((tcpHeader[5] & 0xFF) << 8)
				| ((tcpHeader[6] & 0xFF) << 16)
				| ((tcpHeader[7] & 0xFF) << 24);

		if (payloadLen < ZkNewConstants.ZK_HEADER_SIZE || payloadLen > ZkNewConstants.MAX_PACKET_SIZE) {
			throw new IOException("Độ dài gói tin không hợp lệ: " + payloadLen + " bytes");
		}

		byte[] payloadBytes = new byte[payloadLen];
		readFully(in, payloadBytes, 0, payloadLen);
		byte[] zkBytes = payloadBytes;
		if (magic == ZkNewConstants.TCP_MAGIC_ALT) {
			try {
				zkBytes = ZkNewCrypto.decryptSecurePayload(payloadBytes, aesKey);
			} catch (Exception e) {
				throw new IOException("Không thể giải mã secure frame", e);
			}
		}
		return ZkNewPacket.parseZkBytes(zkBytes, 0, zkBytes.length);
	}

	private void negotiateSecureSession(ProbeReport report) throws Exception {
		KeyPair clientKeyPair = ZkNewCrypto.generateClientRsaKeyPair();
		String clientPem = ZkNewCrypto.toPkcs1PublicPem((RSAPublicKey) clientKeyPair.getPublic());
		byte[] clientDmc = ZkNewCrypto.buildDmcPayload(3, clientPem.getBytes(StandardCharsets.US_ASCII));

		sendPacket(ZkNewConstants.CMD_CRYPTO_DMC_EXCHANGE, clientDmc, false);
		ZkNewPacket publicKeyResp = receivePacket();
		if (!publicKeyResp.isOk()) {
			throw new IOException("10063 bị từ chối: " + publicKeyResp.getCommandId());
		}
		ZkNewCrypto.DmcMessage deviceKeyMessage = ZkNewCrypto.parseDmcPayload(publicKeyResp.getPayload());
		String devicePem = new String(deviceKeyMessage.getBody(), StandardCharsets.US_ASCII);
		RSAPublicKey devicePublicKey = ZkNewCrypto.parsePkcs1PublicPem(devicePem);

		int clientSecret = ZkNewCrypto.nextClientSecret();
		byte[] secretPayload = ZkNewCrypto.buildRsaEncryptedDmcPayload(
				2, ZkNewCrypto.int32LE(clientSecret), devicePublicKey);
		sendPacket(ZkNewConstants.CMD_CRYPTO_KEY_EXCHANGE, secretPayload, false);
		ZkNewPacket secretResp = receivePacket();
		if (!secretResp.isOk()) {
			throw new IOException("10064 bị từ chối: " + secretResp.getCommandId());
		}
		ZkNewCrypto.DmcMessage serverSecretMessage = ZkNewCrypto.parseRsaEncryptedDmcPayload(
				secretResp.getPayload(), clientKeyPair.getPrivate());
		byte[] serverSecretBytes = serverSecretMessage.getBody();
		if (serverSecretBytes.length < 4) {
			throw new IOException("10064 response thiếu server secret");
		}
		int serverSecret = readInt32LE(serverSecretBytes, 0);
		this.aesKey = ZkNewCrypto.deriveAesKey(clientSecret, serverSecret);

		sendPacket(ZkNewConstants.CMD_CRYPTO_CONFIRM_SESSION, new byte[4], false);
		ZkNewPacket confirmResp = receivePacket();
		if (!confirmResp.isOk()) {
			throw new IOException("10065 bị từ chối: " + confirmResp.getCommandId());
		}
		this.secureMode = true;
		sleepQuietly(50);
	}

	private boolean authenticateModernSession(ProbeReport report) throws IOException {
		byte[] stage1 = ZkNewCrypto.makeCommKeyWithCurrentTick(this.password, this.sessionId);
		sendPacket(ZkNewConstants.CMD_AUTH, stage1, true);
		ZkNewPacket authResp = receivePacket();
		report.authResponseCode = authResp.getCommandId();
		if (authResp.isOk()) {
			return true;
		}
		if (authResp.isAuthLock2032()) {
			report.state = ConnectionState.AUTH_LOCK_2032;
			report.diagnosticMessage = "Thiết bị khóa auth sau CMD_AUTH (2032).";
			return false;
		}

		byte[] stage2 = ZkNewCrypto.buildExtendedAuthPayload(this.passwordText);
		sendPacket(ZkNewConstants.CMD_AUTH_EXT, stage2, true);
		ZkNewPacket authExtResp = receivePacket();
		report.authExtResponseCode = authExtResp.getCommandId();
		if (authExtResp.isOk()) {
			sendSdkBuildMarker();
			return true;
		}

		if (authExtResp.isAuthLock2032()) {
			report.state = ConnectionState.AUTH_LOCK_2032;
			report.diagnosticMessage = "Thiết bị khóa auth sau CMD_AUTH_EXT 1106 (2032).";
		} else if (authExtResp.isUnauth() || authExtResp.isError()) {
			report.state = ConnectionState.AUTH_FAILED_UNAUTH;
			report.diagnosticMessage = "Secure tunnel OK nhưng Comm Key bị từ chối. CMD_AUTH="
					+ report.authResponseCode + ", CMD_AUTH_EXT=" + report.authExtResponseCode;
		} else {
			report.state = ConnectionState.ERROR;
			report.diagnosticMessage = "Secure tunnel OK nhưng CMD_AUTH_EXT trả mã lạ: "
					+ report.authExtResponseCode;
		}
		return false;
	}

	private void sendSdkBuildMarker() throws IOException {
		byte[] payload = "SDKBuild=1\0".getBytes(StandardCharsets.US_ASCII);
		sendPacket(ZkNewConstants.CMD_OPTIONS_WRQ, payload, true);
		ZkNewPacket resp = receivePacket();
		if (!resp.isOk()) {
			throw new IOException("CMD_OPTIONS_WRQ SDKBuild bị từ chối: " + resp.getCommandId());
		}
	}

	/**
	 * Lấy thông số cấu hình thiết bị (Option key).
	 */
	public synchronized String getDeviceOption(String key) throws IOException {
		if (!connected) connect();
		byte[] payload = (key + "\0").getBytes(StandardCharsets.US_ASCII);
		sendPacket(ZkNewConstants.CMD_OPTIONS_RRQ, payload);
		ZkNewPacket resp = receivePacket();
		if (resp.getPayloadLength() > 0) {
			String str = new String(resp.getPayload(), StandardCharsets.US_ASCII).trim();
			if (str.contains("=")) {
				return str.split("=", 2)[1].replace("\0", "").trim();
			}
			return str.replace("\0", "").trim();
		}
		return "";
	}

	public synchronized String getFirmwareVersion() throws IOException {
		if (!connected) connect();
		sendPacket(ZkNewConstants.CMD_VERSION, new byte[0]);
		ZkNewPacket resp = receivePacket();
		if (!resp.isOk() && !resp.isData()) {
			throw new IOException("Thiết bị từ chối CMD_VERSION: " + resp.getCommandId());
		}
		return new String(resp.getPayload(), StandardCharsets.US_ASCII).replace("\0", "").trim();
	}

	/**
	 * Đọc dung lượng bộ nhớ thiết bị.
	 */
	public synchronized ZkNewDeviceInfo getDeviceInfo() throws IOException {
		if (!connected) connect();
		ZkNewDeviceInfo info = new ZkNewDeviceInfo();
		info.setSerialNumber(getDeviceOption("~SerialNumber"));
		info.setDeviceModel(getDeviceOption("~DeviceName"));
		if (info.getDeviceModel().isEmpty()) {
			info.setDeviceModel(getDeviceOption("DeviceName"));
		}
		info.setFirmwareVersion(getFirmwareVersion());
		info.setPlatform(getDeviceOption("~Platform"));
		if (info.getPlatform().isEmpty()) {
			info.setPlatform(getDeviceOption("Platform"));
		}
		info.setMacAddress(getDeviceOption("MAC"));

		sendPacket(ZkNewConstants.CMD_GET_FREE_SIZES, new byte[0]);
		ZkNewPacket resp = receivePacket();
		if (resp.getPayloadLength() >= 80) {
			byte[] p = resp.getPayload();
			info.setUserCount(readInt32LE(p, 16));
			info.setFpCount(readInt32LE(p, 24));
			info.setLogCount(readInt32LE(p, 32));
			if (p.length >= 92) {
				info.setFaceCount(readInt32LE(p, 80));
			}
		}
		return info;
	}

	public synchronized List<ZkNewAttendanceLog> getAttendanceLogs() throws IOException {
		if (attendanceLogCache != null) {
			return new ArrayList<>(attendanceLogCache);
		}
		List<ZkNewAttendanceLog> logs = new ArrayList<>();
		streamAttendanceLogs(logs::add);
		attendanceLogCache = new ArrayList<>(logs);
		return logs;
	}

	public synchronized List<ZkNewAttendanceLog> getAllLog() throws IOException {
		return getAttendanceLogs();
	}

	public synchronized List<ZkNewAttendanceLog> getAttendanceLogs(long start, long end) throws IOException {
		return getLogAt(start, end);
	}

	public synchronized List<ZkNewAttendanceLog> getLogAt(long start, long end) throws IOException {
		LocalDateTime startTime = normalizeEpoch(start);
		LocalDateTime endTime = normalizeEpoch(end);
		List<ZkNewAttendanceLog> result = new ArrayList<>();
		for (ZkNewAttendanceLog log : getAttendanceLogs()) {
			LocalDateTime timestamp = log.getTimestamp();
			if (timestamp == null) {
				continue;
			}
			boolean afterStart = startTime == null || !timestamp.isBefore(startTime);
			boolean beforeEnd = endTime == null || !timestamp.isAfter(endTime);
			if (afterStart && beforeEnd) {
				result.add(log);
			}
		}
		return result;
	}

	public synchronized void streamAttendanceLogs(Consumer<ZkNewAttendanceLog> consumer) throws IOException {
		if (consumer == null) {
			return;
		}
		if (!connected) connect();

		byte[] bufferReq = new byte[11];
		bufferReq[0] = 1;
		bufferReq[1] = (byte) (ZkNewConstants.CMD_ATTLOG_RRQ & 0xFF);
		bufferReq[2] = (byte) ((ZkNewConstants.CMD_ATTLOG_RRQ >> 8) & 0xFF);
		byte[] data = readBufferedPayload(bufferReq, "log");
		ZkNewRecordParser.parse(data, consumer);
	}

	public synchronized List<ZkNewUserInfo> getAllUserInfo() throws IOException {
		if (!connected) connect();
		byte[] bufferReq = new byte[] {
				1,
				(byte) (ZkNewConstants.CMD_USERTEMP_RRQ & 0xFF),
				(byte) ((ZkNewConstants.CMD_USERTEMP_RRQ >> 8) & 0xFF),
				(byte) ZkNewConstants.FCT_USER,
				0, 0, 0, 0, 0, 0, 0
		};
		List<ZkNewUserInfo> users = ZkNewUserParser.parse(readBufferedPayload(bufferReq, "user"));
		return enrichUserCreatedAtFromLogs(users);
	}

	public synchronized List<ZkNewUserInfo> getUsers() throws IOException {
		return getAllUserInfo();
	}

	public synchronized List<ZkNewUserInfo> getAllUser() throws IOException {
		return getAllUserInfo();
	}

	public synchronized ZkNewUserInfo getUserInfo(String userId) throws IOException {
		if (userId == null || userId.isBlank()) {
			return null;
		}
		String target = userId.trim();
		for (ZkNewUserInfo user : getAllUserInfo()) {
			if (target.equalsIgnoreCase(user.getUserId())) {
				return user;
			}
		}
		return null;
	}

	public synchronized ZkNewUserInfo getUser(String userId) throws IOException {
		return getUserInfo(userId);
	}

	private byte[] readBufferedPayload(byte[] bufferReq, String label) throws IOException {
		freeDeviceDataBuffer();
		sendPacket(ZkNewConstants.CMD_DATA_WRRQ, bufferReq);
		ZkNewPacket resp = receivePacket();
		if (resp.isError() || resp.isUnauth()) {
			throw new IOException("Thiết bị từ chối đọc " + label + ": " + resp.getCommandId());
		}

		byte[] payload = resp.getPayload();
		if (payload.length < 4) {
			throw new IOException("Phản hồi đọc " + label + " thiếu total size: " + payload.length);
		}

		if (resp.isData() || resp.getCommandId() == ZkNewConstants.CMD_ACK_DATA) {
			freeDeviceDataBuffer();
			return payload;
		}

		int totalSize = extractPreparedBufferSize(payload);
		if (totalSize <= 0) {
			return new byte[0];
		}
		if (totalSize > ZkNewConstants.MAX_PACKET_SIZE) {
			throw new IOException("Dung lượng " + label + " vượt giới hạn an toàn: " + totalSize);
		}

		ByteArrayOutputStream allData = new ByteArrayOutputStream(totalSize);
		int offset = 0;
		while (offset < totalSize) {
			int chunkSize = Math.min(ZkNewConstants.DEFAULT_BUFFER_CHUNK_SIZE, totalSize - offset);
			byte[] chunkReq = new byte[8];
			writeInt32LE(chunkReq, 0, offset);
			writeInt32LE(chunkReq, 4, chunkSize);
			sendPacket(ZkNewConstants.CMD_READ_BUFFER, chunkReq);
			ZkNewPacket chunkResp = receivePacket();
			if (chunkResp.getPayloadLength() <= 0) {
				break;
			}
			byte[] chunk = chunkResp.getPayload();
			if (chunkResp.isPrepareData()) {
				offset += chunk.length;
				continue;
			}
			allData.write(chunk);
			offset += chunk.length;
		}

		freeDeviceDataBuffer();
		return allData.toByteArray();
	}

	private static int extractPreparedBufferSize(byte[] payload) throws IOException {
		if (payload.length >= 9 && payload[0] == 0) {
			int size1 = readInt32LE(payload, 1);
			int size2 = readInt32LE(payload, 5);
			if (size1 > 0 && (size1 == size2 || size2 == 0)) {
				return size1;
			}
		}
		return readInt32LE(payload, 0);
	}

	private void freeDeviceDataBuffer() {
		try {
			sendPacket(ZkNewConstants.CMD_FREE_DATA, new byte[0]);
			receivePacket();
		} catch (Exception ignored) {
		}
	}

	private List<ZkNewUserInfo> enrichUserCreatedAtFromLogs(List<ZkNewUserInfo> users) throws IOException {
		if (users == null || users.isEmpty()) {
			return List.of();
		}
		boolean needsCreatedAt = false;
		for (ZkNewUserInfo user : users) {
			if (user.getCreatedAt() == null) {
				needsCreatedAt = true;
				break;
			}
		}
		if (!needsCreatedAt) {
			return users;
		}

		Map<String, LocalDateTime> firstLogByUser = new HashMap<>();
		for (ZkNewAttendanceLog log : getAttendanceLogs()) {
			if (log.getUserId() == null || log.getUserId().isBlank() || log.getTimestamp() == null) {
				continue;
			}
			firstLogByUser.merge(log.getUserId().trim(), log.getTimestamp(),
					(a, b) -> a.isBefore(b) ? a : b);
		}

		List<ZkNewUserInfo> result = new ArrayList<>(users.size());
		for (ZkNewUserInfo user : users) {
			LocalDateTime createdAt = user.getCreatedAt();
			if (createdAt == null) {
				createdAt = firstLogByUser.get(user.getUserId());
			}
			result.add(user.withCreatedAt(createdAt));
		}
		return result;
	}

	private static LocalDateTime normalizeEpoch(long value) {
		if (value <= 0) {
			return null;
		}
		long millis = value > 10_000_000_000L ? value : value * 1000L;
		return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
	}

	/**
	 * Mở khóa relay kiểm soát ra vào.
	 */
	public synchronized boolean unlock(int delaySeconds) throws IOException {
		if (!connected) connect();
		int delay = Math.max(1, delaySeconds);
		byte[] payload = new byte[4];
		payload[0] = (byte) (delay & 0xFF);
		payload[1] = (byte) ((delay >> 8) & 0xFF);
		payload[2] = (byte) ((delay >> 16) & 0xFF);
		payload[3] = (byte) ((delay >> 24) & 0xFF);

		sendPacket(ZkNewConstants.CMD_UNLOCK, payload);
		ZkNewPacket resp = receivePacket();
		return resp.isOk();
	}

	public boolean isConnected() {
		return connected && socket != null && !socket.isClosed() && !closed;
	}

	public ConnectionState getConnectionState() {
		return connectionState;
	}

	public int getSessionId() {
		return sessionId;
	}

	@Override
	public synchronized void close() {
		if (closed) return;
		closed = true;

		if (connected && socket != null && !socket.isClosed()) {
			try {
				socket.setSoTimeout(1000);
				sendPacket(ZkNewConstants.CMD_EXIT, new byte[0]);
			} catch (Exception ignored) {}
		}
		connected = false;

		if (in != null) {
			try { in.close(); } catch (Exception ignored) {}
			in = null;
		}
		if (out != null) {
			try { out.close(); } catch (Exception ignored) {}
			out = null;
		}
		if (socket != null) {
			try { socket.close(); } catch (Exception ignored) {}
			socket = null;
		}
	}

	// -------------------------------------------------------------------------
	// Helper I/O Methods
	// -------------------------------------------------------------------------

	private static int readTcpHeader(InputStream in, byte[] headerDest) throws IOException {
		byte[] window = new byte[4];
		readFully(in, window, 0, 4);

		while (true) {
			if (isMagic(window, 0)) {
				System.arraycopy(window, 0, headerDest, 0, 4);
				readFully(in, headerDest, 4, 4);
				return ((headerDest[0] & 0xFF) << 24)
						| ((headerDest[1] & 0xFF) << 16)
						| ((headerDest[2] & 0xFF) << 8)
						| (headerDest[3] & 0xFF);
			}
			int next = in.read();
			if (next < 0) {
				throw new EOFException("Mất kết nối khi tìm TCP Magic Header");
			}
			window[0] = window[1];
			window[1] = window[2];
			window[2] = window[3];
			window[3] = (byte) next;
		}
	}

	private static boolean isMagic(byte[] b, int o) {
		if (b == null || o + 4 > b.length) return false;
		return (b[o] == 0x50 && b[o + 1] == 0x50) &&
				((b[o + 2] == (byte) 0x82 && b[o + 3] == 0x7D) ||
				 (b[o + 2] == (byte) 0x83 && b[o + 3] == 0x7C));
	}

	private static void readFully(InputStream in, byte[] buf, int offset, int len) throws IOException {
		int totalRead = 0;
		while (totalRead < len) {
			int read = in.read(buf, offset + totalRead, len - totalRead);
			if (read < 0) {
				throw new EOFException("Mất kết nối sau khi đọc " + totalRead + " / " + len + " bytes");
			}
			totalRead += read;
		}
	}

	private static int readInt32LE(byte[] data, int offset) {
		return (data[offset] & 0xFF)
				| ((data[offset + 1] & 0xFF) << 8)
				| ((data[offset + 2] & 0xFF) << 16)
				| ((data[offset + 3] & 0xFF) << 24);
	}

	private static void writeInt32LE(byte[] data, int offset, int value) {
		data[offset] = (byte) (value & 0xFF);
		data[offset + 1] = (byte) ((value >>> 8) & 0xFF);
		data[offset + 2] = (byte) ((value >>> 16) & 0xFF);
		data[offset + 3] = (byte) ((value >>> 24) & 0xFF);
	}

	private static void sleepQuietly(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
