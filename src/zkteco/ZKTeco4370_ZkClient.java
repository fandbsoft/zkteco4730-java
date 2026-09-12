package zkteco;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Production-ready pure Java client for ZKTeco standalone devices over TCP 4370.
 * <p>
 * The client auto-detects legacy pull firmware and newer secure SenseFace/Linux
 * firmware, then exposes one compact API for device info, users, attendance
 * logs, range filtering, and door unlock.
 */
public class ZKTeco4370_ZkClient implements AutoCloseable {
	private final String host;
	private final int port;
	private final int password;
	private final String passwordText;
	private final int connectTimeoutMs;
	private final int readTimeoutMs;

	private Socket socket;
	private InputStream in;
	private OutputStream out;
	private int sessionId;
	private int replyId;
	private boolean secureMode;
	private byte[] aesKey;
	private boolean connected;
	private boolean closed = true;
	private ZKTeco4370_ProtocolMode protocolMode = ZKTeco4370_ProtocolMode.UNKNOWN;
	private List<ZKTeco4370_AttendanceLog> attendanceLogCache;
	private boolean attendanceLogCacheEnabled;
	private boolean bulkReadSinceConnect;

	public ZKTeco4370_ZkClient(String host, int port, int password) {
		this(host, port, password, ZKTeco4370_ZkConstants.DEFAULT_CONNECT_TIMEOUT_MS, ZKTeco4370_ZkConstants.DEFAULT_READ_TIMEOUT_MS);
	}

	public ZKTeco4370_ZkClient(String host, int password) {
		this(host, ZKTeco4370_ZkConstants.DEFAULT_PORT, password);
	}

	public ZKTeco4370_ZkClient(String host) {
		this(host, ZKTeco4370_ZkConstants.DEFAULT_PORT, 0);
	}

	public ZKTeco4370_ZkClient(String host, int port, int password, int connectTimeoutMs, int readTimeoutMs) {
		if (host == null || host.isBlank()) {
			throw new IllegalArgumentException("Device IP/host must not be blank");
		}
		if (port <= 0 || port > 65_535) {
			throw new IllegalArgumentException("Invalid TCP port: " + port);
		}
		this.host = host.trim();
		this.port = port;
		this.password = password;
		this.passwordText = Integer.toString(password);
		this.connectTimeoutMs = connectTimeoutMs;
		this.readTimeoutMs = readTimeoutMs;
	}

	public synchronized void connect() throws IOException {
		if (isConnected()) {
			return;
		}
		openSocket();
		try {
			sendPacket(ZKTeco4370_ZkConstants.CMD_CONNECT, new byte[0], false);
			ZKTeco4370_ZkPacket response = receivePacket();
			this.sessionId = response.getSessionId();

			int code = response.getCommandId();
			if (code == ZKTeco4370_ZkConstants.CMD_ACK_OK) {
				this.protocolMode = ZKTeco4370_ProtocolMode.LEGACY_PULL;
				this.connected = true;
				return;
			}
			if (code == ZKTeco4370_ZkConstants.CMD_ACK_UNAUTH) {
				authenticateLegacy();
				this.protocolMode = ZKTeco4370_ProtocolMode.LEGACY_PULL;
				this.connected = true;
				return;
			}
			if (code == ZKTeco4370_ZkConstants.CMD_ACK_CHALLENGE_6001) {
				negotiateSecureSession();
				authenticateSecureSession();
				this.protocolMode = ZKTeco4370_ProtocolMode.SECURE_PULL;
				this.connected = true;
				return;
			}
			if (code == ZKTeco4370_ZkConstants.CMD_ACK_AUTH_LOCK) {
				throw new ZKTeco4370_ZkException("Device requires secure/standalone pull authentication but did not issue a public-key challenge", code);
			}
			throw new ZKTeco4370_ZkException("Unexpected response to CMD_CONNECT", code);
		} catch (IOException | RuntimeException ex) {
			closeSocketOnly();
			throw ex;
		}
	}

	public synchronized ZKTeco4370_DeviceInfo getDeviceInfo() throws IOException {
		ensureConnected();
		String serialNumber = getDeviceOption("~SerialNumber");
		String deviceModel = firstNonBlank(getDeviceOption("~DeviceName"), getDeviceOption("DeviceName"));
		String platform = firstNonBlank(getDeviceOption("~Platform"), getDeviceOption("Platform"));
		String macAddress = getDeviceOption("MAC");
		String firmwareVersion = firstNonBlank(getFirmwareVersion(), getDeviceOption("~Firmware"));

		int[] sizes = readSizes();
		return new ZKTeco4370_DeviceInfo(serialNumber, firmwareVersion, platform, macAddress, deviceModel,
				sizes[0], sizes[1], sizes[3], sizes[2], protocolMode);
	}

	public synchronized List<ZKTeco4370_UserInfo> getAllUser() throws IOException {
		ensureConnected();
		List<ZKTeco4370_UserInfo> users = new ArrayList<>();
		ZKTeco4370_UserParser.ZKTeco4370_StreamingParser parser = ZKTeco4370_UserParser.newStreamingParser(users::add);
		streamBufferedPayload(buildUserRequest(), "users", new ZKTeco4370_PayloadHandler() {
			@Override
			public void onStart(int totalSize) {
				parser.start(totalSize);
			}

			@Override
			public void onChunk(byte[] data, int offset, int length) {
				parser.accept(data, offset, length);
			}

			@Override
			public void onFinish() {
				parser.finish();
			}
		});
		return enrichUserCreatedAt(users);
	}

	public synchronized List<ZKTeco4370_UserInfo> getAllUserInfo() throws IOException {
		return getAllUser();
	}

	public synchronized ZKTeco4370_UserInfo getUser(String userId) throws IOException {
		if (userId == null || userId.isBlank()) {
			return null;
		}
		String target = userId.trim();
		for (ZKTeco4370_UserInfo user : getAllUser()) {
			if (target.equalsIgnoreCase(user.getUserId())) {
				return user;
			}
		}
		return null;
	}

	public synchronized List<ZKTeco4370_AttendanceLog> getAllLog() throws IOException {
		if (attendanceLogCacheEnabled && attendanceLogCache != null) {
			return new ArrayList<>(attendanceLogCache);
		}
		List<ZKTeco4370_AttendanceLog> logs = new ArrayList<>();
		streamAllLog(logs::add);
		if (attendanceLogCacheEnabled) {
			attendanceLogCache = new ArrayList<>(logs);
		}
		return logs;
	}

	public synchronized List<ZKTeco4370_AttendanceLog> getAttendanceLogs() throws IOException {
		return getAllLog();
	}

	public synchronized List<ZKTeco4370_AttendanceLog> getLogAt(long start, long end) throws IOException {
		if (attendanceLogCacheEnabled && attendanceLogCache != null) {
			long startMillis = start <= 0 ? Long.MIN_VALUE : toEpochMillis(start);
			long endMillis = end <= 0 ? Long.MAX_VALUE : toEpochMillis(end);
			long min = Math.min(startMillis, endMillis);
			long max = Math.max(startMillis, endMillis);

			List<ZKTeco4370_AttendanceLog> result = new ArrayList<>();
			for (ZKTeco4370_AttendanceLog log : attendanceLogCache) {
				long timestamp = log.getTimestampEpochMilli();
				if (timestamp > 0 && timestamp >= min && timestamp <= max) {
					result.add(log);
				}
			}
			return result;
		}

		List<ZKTeco4370_AttendanceLog> result = new ArrayList<>();
		streamLogAt(start, end, result::add);
		return result;
	}

	public synchronized void streamAllLog(Consumer<ZKTeco4370_AttendanceLog> consumer) throws IOException {
		Objects.requireNonNull(consumer, "Attendance log consumer must not be null");
		ensureConnected();
		ZKTeco4370_RecordParser.ZKTeco4370_StreamingParser parser = ZKTeco4370_RecordParser.newStreamingParser(consumer);
		streamBufferedPayload(buildAttendanceLogRequest(), "attendance logs", new ZKTeco4370_PayloadHandler() {
			@Override
			public void onStart(int totalSize) {
				parser.start(totalSize);
			}

			@Override
			public void onChunk(byte[] data, int offset, int length) {
				parser.accept(data, offset, length);
			}

			@Override
			public void onFinish() {
				parser.finish();
			}
		});
	}

	public synchronized void streamLogAt(long start, long end, Consumer<ZKTeco4370_AttendanceLog> consumer) throws IOException {
		Objects.requireNonNull(consumer, "Attendance log consumer must not be null");
		long startMillis = start <= 0 ? Long.MIN_VALUE : toEpochMillis(start);
		long endMillis = end <= 0 ? Long.MAX_VALUE : toEpochMillis(end);
		long min = Math.min(startMillis, endMillis);
		long max = Math.max(startMillis, endMillis);

		streamAllLog(log -> {
			long timestamp = log.getTimestampEpochMilli();
			if (timestamp > 0 && timestamp >= min && timestamp <= max) {
				consumer.accept(log);
			}
		});
	}

	public synchronized boolean unlock(int delaySeconds) throws IOException {
		ensureConnected();
		if (protocolMode == ZKTeco4370_ProtocolMode.SECURE_PULL && bulkReadSinceConnect) {
			reconnectPreservingCache();
		}
		return unlockOnce(delaySeconds);
	}

	private boolean unlockOnce(int delaySeconds) throws IOException {
		int delay = Math.max(1, delaySeconds);
		byte[] payload = new byte[4];
		writeInt32LE(payload, 0, delay);
		sendPacket(ZKTeco4370_ZkConstants.CMD_UNLOCK, payload);
		ZKTeco4370_ZkPacket response = receivePacket();
		return response.isOk();
	}

	public synchronized boolean unlockDoor(int delaySeconds) throws IOException {
		return unlock(delaySeconds);
	}

	public synchronized boolean unlock() throws IOException {
		return unlock(ZKTeco4370_ZkConstants.DEFAULT_UNLOCK_DELAY_SECONDS);
	}

	public synchronized String getDeviceOption(String key) throws IOException {
		ensureConnected();
		byte[] payload = ((key != null ? key : "") + "\0").getBytes(StandardCharsets.US_ASCII);
		sendPacket(ZKTeco4370_ZkConstants.CMD_OPTIONS_RRQ, payload);
		ZKTeco4370_ZkPacket response = receivePacket();
		if (response.getPayloadLength() == 0) {
			return "";
		}
		String text = new String(response.payloadView(), StandardCharsets.US_ASCII)
				.replace("\0", "")
				.trim();
		int equals = text.indexOf('=');
		return equals >= 0 ? text.substring(equals + 1).trim() : text;
	}

	public synchronized String getFirmwareVersion() throws IOException {
		ensureConnected();
		sendPacket(ZKTeco4370_ZkConstants.CMD_VERSION, new byte[0]);
		ZKTeco4370_ZkPacket response = receivePacket();
		if (response.getPayloadLength() > 0 && (response.isOk() || response.isData())) {
			return new String(response.payloadView(), StandardCharsets.US_ASCII).replace("\0", "").trim();
		}
		return "";
	}

	public synchronized void clearCache() {
		attendanceLogCache = null;
	}

	public synchronized boolean isAttendanceLogCacheEnabled() {
		return attendanceLogCacheEnabled;
	}

	public synchronized ZKTeco4370_ZkClient setAttendanceLogCacheEnabled(boolean enabled) {
		this.attendanceLogCacheEnabled = enabled;
		if (!enabled) {
			clearCache();
		}
		return this;
	}

	public boolean isConnected() {
		return connected && socket != null && !socket.isClosed() && !closed;
	}

	public ZKTeco4370_ProtocolMode getProtocolMode() {
		return protocolMode;
	}

	public String getProtocolName() {
		return switch (protocolMode) {
			case LEGACY_PULL -> "ZKTeco legacy pull protocol";
			case SECURE_PULL -> "ZKTeco secure pull protocol";
			default -> "Unknown";
		};
	}

	public int getSessionId() {
		return sessionId;
	}

	private void openSocket() throws IOException {
		closeSocketOnly();
		this.closed = false;
		this.connected = false;
		this.secureMode = false;
		this.aesKey = null;
		this.protocolMode = ZKTeco4370_ProtocolMode.UNKNOWN;
		this.attendanceLogCache = null;
		this.bulkReadSinceConnect = false;
		this.sessionId = 0;
		this.replyId = 0;

		socket = new Socket();
		socket.setTcpNoDelay(true);
		socket.setSoTimeout(readTimeoutMs);
		socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
		in = socket.getInputStream();
		out = socket.getOutputStream();
	}

	private void authenticateLegacy() throws IOException {
		int[] ticks = { 50, 0 };
		int lastCode = -1;
		for (int tick : ticks) {
			sendPacket(ZKTeco4370_ZkConstants.CMD_AUTH, ZKTeco4370_ZkCrypto.makeCommKey(password, sessionId, tick), false);
			ZKTeco4370_ZkPacket response = receivePacket();
			if (response.isOk()) {
				return;
			}
			lastCode = response.getCommandId();
			if (response.isAuthChallenge() || response.isAuthLock()) {
				throw new ZKTeco4370_ZkException("Legacy authentication rejected by secure firmware", lastCode);
			}
		}
		throw new ZKTeco4370_ZkException("Legacy Comm Key authentication failed", lastCode);
	}

	private void negotiateSecureSession() throws IOException {
		try {
			KeyPair clientKeyPair = ZKTeco4370_ZkCrypto.generateClientRsaKeyPair();
			String clientPem = ZKTeco4370_ZkCrypto.toPkcs1PublicPem((RSAPublicKey) clientKeyPair.getPublic());
			byte[] clientDmc = ZKTeco4370_ZkCrypto.buildDmcPayload(3, clientPem.getBytes(StandardCharsets.US_ASCII));

			sendPacket(ZKTeco4370_ZkConstants.CMD_CRYPTO_DMC_EXCHANGE, clientDmc, false);
			ZKTeco4370_ZkPacket publicKeyResponse = receivePacket();
			if (!publicKeyResponse.isOk()) {
				throw new ZKTeco4370_ZkException("Secure public-key exchange failed", publicKeyResponse.getCommandId());
			}
			ZKTeco4370_ZkCrypto.ZKTeco4370_DmcMessage deviceKeyMessage = ZKTeco4370_ZkCrypto.parseDmcPayload(publicKeyResponse.payloadView());
			String devicePem = new String(deviceKeyMessage.getBody(), StandardCharsets.US_ASCII);
			RSAPublicKey devicePublicKey = ZKTeco4370_ZkCrypto.parsePkcs1PublicPem(devicePem);

			int clientSecret = ZKTeco4370_ZkCrypto.nextClientSecret();
			byte[] secretPayload = ZKTeco4370_ZkCrypto.buildRsaEncryptedDmcPayload(
					2, ZKTeco4370_ZkCrypto.int32LE(clientSecret), devicePublicKey);
			sendPacket(ZKTeco4370_ZkConstants.CMD_CRYPTO_KEY_EXCHANGE, secretPayload, false);
			ZKTeco4370_ZkPacket secretResponse = receivePacket();
			if (!secretResponse.isOk()) {
				throw new ZKTeco4370_ZkException("Secure session-key exchange failed", secretResponse.getCommandId());
			}
			ZKTeco4370_ZkCrypto.ZKTeco4370_DmcMessage serverSecretMessage = ZKTeco4370_ZkCrypto.parseRsaEncryptedDmcPayload(
					secretResponse.payloadView(), clientKeyPair.getPrivate());
			byte[] serverSecretBytes = serverSecretMessage.getBody();
			if (serverSecretBytes.length < 4) {
				throw new ZKTeco4370_ZkException("Secure session-key response is too short");
			}
			int serverSecret = readInt32LE(serverSecretBytes, 0);
			this.aesKey = ZKTeco4370_ZkCrypto.deriveAesKey(clientSecret, serverSecret);

			sendPacket(ZKTeco4370_ZkConstants.CMD_CRYPTO_CONFIRM_SESSION, new byte[4], false);
			ZKTeco4370_ZkPacket confirmResponse = receivePacket();
			if (!confirmResponse.isOk()) {
				throw new ZKTeco4370_ZkException("Secure session confirmation failed", confirmResponse.getCommandId());
			}
			this.secureMode = true;
			sleepQuietly(50);
		} catch (ZKTeco4370_ZkException e) {
			throw e;
		} catch (Exception e) {
			throw new ZKTeco4370_ZkException("Secure handshake failed", e);
		}
	}

	private void authenticateSecureSession() throws IOException {
		byte[] stage1 = ZKTeco4370_ZkCrypto.makeCommKeyWithCurrentTick(password, sessionId);
		sendPacket(ZKTeco4370_ZkConstants.CMD_AUTH, stage1, true);
		ZKTeco4370_ZkPacket stage1Response = receivePacket();
		if (stage1Response.isOk()) {
			sendSdkBuildMarker();
			return;
		}
		if (stage1Response.isAuthLock()) {
			throw new ZKTeco4370_ZkException("Secure firmware locked CMD_AUTH", stage1Response.getCommandId());
		}

		byte[] stage2 = ZKTeco4370_ZkCrypto.buildExtendedAuthPayload(passwordText);
		sendPacket(ZKTeco4370_ZkConstants.CMD_AUTH_EXT, stage2, true);
		ZKTeco4370_ZkPacket stage2Response = receivePacket();
		if (!stage2Response.isOk()) {
			throw new ZKTeco4370_ZkException("Secure Comm Key authentication failed", stage2Response.getCommandId());
		}
		sendSdkBuildMarker();
	}

	private void sendSdkBuildMarker() throws IOException {
		sendPacket(ZKTeco4370_ZkConstants.CMD_OPTIONS_WRQ, "SDKBuild=1\0".getBytes(StandardCharsets.US_ASCII), true);
		ZKTeco4370_ZkPacket response = receivePacket();
		if (!response.isOk()) {
			throw new ZKTeco4370_ZkException("Device rejected SDKBuild marker", response.getCommandId());
		}
	}

	private int[] readSizes() throws IOException {
		sendPacket(ZKTeco4370_ZkConstants.CMD_GET_FREE_SIZES, new byte[0]);
		ZKTeco4370_ZkPacket response = receivePacket();
		byte[] payload = response.payloadView();
		if (payload.length < 80) {
			throw new ZKTeco4370_ZkException("Invalid device size response", response.getCommandId());
		}
		int users = readInt32LE(payload, 16);
		int fingers = readInt32LE(payload, 24);
		int logs = readInt32LE(payload, 32);
		int faces = payload.length >= 92 ? readInt32LE(payload, 80) : 0;
		return new int[] { users, fingers, logs, faces };
	}

	private interface ZKTeco4370_PayloadHandler {
		void onStart(int totalSize) throws IOException;
		void onChunk(byte[] data, int offset, int length) throws IOException;
		void onFinish() throws IOException;
	}

	private void streamBufferedPayload(byte[] request, String label, ZKTeco4370_PayloadHandler handler) throws IOException {
		Objects.requireNonNull(handler, "Payload handler must not be null");
		ensureBulkReadReady();
		freeDeviceDataBuffer();
		boolean releaseDeviceBuffer = false;
		try {
			sendPacket(ZKTeco4370_ZkConstants.CMD_DATA_WRRQ, request);
			releaseDeviceBuffer = true;
			ZKTeco4370_ZkPacket response = receivePacket();
			if (response.isError() || response.isUnauth() || response.isAuthLock()) {
				throw new ZKTeco4370_ZkException("Device rejected " + label + " read", response.getCommandId());
			}

			byte[] payload = response.payloadView();
			if (response.isData()) {
				validateBulkPayloadSize(label, payload.length);
				handler.onStart(payload.length);
				if (payload.length > 0) {
					handler.onChunk(payload, 0, payload.length);
				}
				handler.onFinish();
				return;
			}
			if (payload.length == 0) {
				handler.onStart(0);
				handler.onFinish();
				return;
			}
			if (payload.length < 4) {
				throw new IOException("Invalid " + label + " prepared-buffer descriptor length: " + payload.length);
			}

			int totalSize = extractPreparedBufferSize(payload);
			validateBulkPayloadSize(label, totalSize);
			handler.onStart(Math.max(0, totalSize));
			if (totalSize <= 0) {
				handler.onFinish();
				return;
			}

			int offset = 0;
			int prepareSkips = 0;
			while (offset < totalSize) {
				int chunkSize = Math.min(ZKTeco4370_ZkConstants.DEFAULT_BUFFER_CHUNK_SIZE, totalSize - offset);
				byte[] chunkRequest = new byte[8];
				writeInt32LE(chunkRequest, 0, offset);
				writeInt32LE(chunkRequest, 4, chunkSize);
				sendPacket(ZKTeco4370_ZkConstants.CMD_READ_BUFFER, chunkRequest);
				ZKTeco4370_ZkPacket chunkResponse = receivePacket();
				if (chunkResponse.isError() || chunkResponse.isUnauth() || chunkResponse.isAuthLock()) {
					throw new ZKTeco4370_ZkException("Device rejected " + label + " buffer chunk", chunkResponse.getCommandId());
				}
				if (chunkResponse.isPrepareData()) {
					if (++prepareSkips > 3) {
						throw new IOException("Device repeatedly returned prepare marker while reading " + label);
					}
					continue;
				}
				prepareSkips = 0;
				byte[] chunk = chunkResponse.payloadView();
				if (chunk.length == 0) {
					throw new EOFException("Device ended " + label + " buffer at " + offset + " / " + totalSize + " bytes");
				}
				int bytesToConsume = Math.min(chunk.length, totalSize - offset);
				handler.onChunk(chunk, 0, bytesToConsume);
				offset += bytesToConsume;
			}
			handler.onFinish();
		} finally {
			if (releaseDeviceBuffer) {
				bulkReadSinceConnect = true;
				freeDeviceDataBuffer();
			}
		}
	}

	private static byte[] buildUserRequest() {
		return new byte[] {
				1,
				(byte) (ZKTeco4370_ZkConstants.CMD_USERTEMP_RRQ & 0xFF),
				(byte) ((ZKTeco4370_ZkConstants.CMD_USERTEMP_RRQ >>> 8) & 0xFF),
				(byte) ZKTeco4370_ZkConstants.FCT_USER,
				0, 0, 0, 0, 0, 0, 0
		};
	}

	private static byte[] buildAttendanceLogRequest() {
		byte[] request = new byte[11];
		request[0] = 1;
		request[1] = (byte) (ZKTeco4370_ZkConstants.CMD_ATTLOG_RRQ & 0xFF);
		request[2] = (byte) ((ZKTeco4370_ZkConstants.CMD_ATTLOG_RRQ >>> 8) & 0xFF);
		return request;
	}

	private static void validateBulkPayloadSize(String label, int totalSize) throws IOException {
		if (totalSize < 0) {
			throw new IOException("Invalid " + label + " payload size: " + totalSize);
		}
		if (totalSize > ZKTeco4370_ZkConstants.MAX_BULK_TRANSFER_SIZE) {
			throw new IOException("Refusing oversized " + label + " payload: " + totalSize + " bytes");
		}
	}

	private List<ZKTeco4370_UserInfo> enrichUserCreatedAt(List<ZKTeco4370_UserInfo> users) {
		if (users == null || users.isEmpty()) {
			return List.of();
		}
		boolean needsCreatedAt = users.stream().anyMatch(user -> user.getCreatedAt() == null);
		if (!needsCreatedAt) {
			return users;
		}
		try {
			Map<String, LocalDateTime> firstLogByUser = new HashMap<>();
			streamAllLog(log -> {
				if (log.getUserId().isEmpty() || log.getTimestamp() == null) {
					return;
				}
				firstLogByUser.merge(log.getUserId(), log.getTimestamp(), (a, b) -> a.isBefore(b) ? a : b);
			});
			List<ZKTeco4370_UserInfo> enriched = new ArrayList<>(users.size());
			for (ZKTeco4370_UserInfo user : users) {
				enriched.add(user.withCreatedAt(firstLogByUser.get(user.getUserId())));
			}
			return enriched;
		} catch (IOException ex) {
			return users;
		}
	}

	private void sendPacket(int commandId, byte[] payload) throws IOException {
		sendPacket(commandId, payload, secureMode);
	}

	private void sendPacket(int commandId, byte[] payload, boolean encrypted) throws IOException {
		if (out == null || closed) {
			throw new IOException("Socket is not open");
		}
		ZKTeco4370_ZkPacket packet = new ZKTeco4370_ZkPacket(commandId, sessionId, replyId, payload);
		byte[] frame;
		if (encrypted) {
			if (aesKey == null) {
				throw new IOException("Secure mode requested before AES key is ready");
			}
			try {
				frame = ZKTeco4370_ZkCrypto.encryptSecureTcpFrame(packet.toZkBytes(), aesKey);
			} catch (Exception e) {
				throw new IOException("Unable to encrypt secure frame", e);
			}
		} else {
			frame = packet.toTcpFrame(ZKTeco4370_ZkConstants.TCP_MAGIC);
		}
		out.write(frame);
		out.flush();
		replyId = (replyId + 1) & 0xFFFF;
	}

	private ZKTeco4370_ZkPacket receivePacket() throws IOException {
		if (in == null || closed) {
			throw new IOException("Socket is not open");
		}
		byte[] tcpHeader = new byte[ZKTeco4370_ZkConstants.TCP_HEADER_SIZE];
		int magic = readTcpHeader(in, tcpHeader);
		int payloadLength = readInt32LE(tcpHeader, 4);
		if (payloadLength < ZKTeco4370_ZkConstants.ZK_HEADER_SIZE || payloadLength > ZKTeco4370_ZkConstants.MAX_FRAME_PAYLOAD_SIZE) {
			throw new IOException("Invalid packet length: " + payloadLength);
		}
		byte[] payload = new byte[payloadLength];
		readFully(in, payload, 0, payloadLength);
		byte[] zkBytes = payload;
		if (magic == ZKTeco4370_ZkConstants.TCP_MAGIC_ALT) {
			try {
				zkBytes = ZKTeco4370_ZkCrypto.decryptSecurePayload(payload, aesKey);
			} catch (Exception e) {
				throw new IOException("Unable to decrypt secure frame", e);
			}
		}
		return ZKTeco4370_ZkPacket.parseZkBytes(zkBytes, 0, zkBytes.length);
	}

	private void ensureConnected() throws IOException {
		if (!isConnected()) {
			connect();
		}
	}

	private void ensureBulkReadReady() throws IOException {
		ensureConnected();
		if (protocolMode == ZKTeco4370_ProtocolMode.SECURE_PULL && bulkReadSinceConnect) {
			reconnectPreservingCache();
		}
	}

	private void reconnectPreservingCache() throws IOException {
		List<ZKTeco4370_AttendanceLog> cachedLogs = attendanceLogCache;
		close();
		connect();
		attendanceLogCache = cachedLogs;
	}

	private void freeDeviceDataBuffer() {
		try {
			sendPacket(ZKTeco4370_ZkConstants.CMD_FREE_DATA, new byte[0]);
			receivePacket();
		} catch (Exception ignored) {
		}
	}

	private static int extractPreparedBufferSize(byte[] payload) {
		if (payload.length >= 9 && payload[0] == 0) {
			int size1 = readInt32LE(payload, 1);
			int size2 = readInt32LE(payload, 5);
			if (size1 > 0 && (size1 == size2 || size2 == 0)) {
				return size1;
			}
		}
		return readInt32LE(payload, 0);
	}

	private static int readTcpHeader(InputStream input, byte[] headerDest) throws IOException {
		byte[] window = new byte[4];
		readFully(input, window, 0, 4);
		while (true) {
			if (isMagic(window, 0)) {
				System.arraycopy(window, 0, headerDest, 0, 4);
				readFully(input, headerDest, 4, 4);
				return ((headerDest[0] & 0xFF) << 24)
						| ((headerDest[1] & 0xFF) << 16)
						| ((headerDest[2] & 0xFF) << 8)
						| (headerDest[3] & 0xFF);
			}
			int next = input.read();
			if (next < 0) {
				throw new EOFException("Connection closed while searching for TCP magic header");
			}
			window[0] = window[1];
			window[1] = window[2];
			window[2] = window[3];
			window[3] = (byte) next;
		}
	}

	private static boolean isMagic(byte[] data, int offset) {
		return data != null && offset + 4 <= data.length
				&& data[offset] == 0x50
				&& data[offset + 1] == 0x50
				&& ((data[offset + 2] == (byte) 0x82 && data[offset + 3] == 0x7D)
						|| (data[offset + 2] == (byte) 0x83 && data[offset + 3] == 0x7C));
	}

	private static void readFully(InputStream input, byte[] buffer, int offset, int length) throws IOException {
		int total = 0;
		while (total < length) {
			int read = input.read(buffer, offset + total, length - total);
			if (read < 0) {
				throw new EOFException("Connection closed after reading " + total + " / " + length + " bytes");
			}
			total += read;
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

	private static long toEpochMillis(long value) {
		return value > 0 && value < 100_000_000_000L ? value * 1000L : value;
	}

	private static String firstNonBlank(String first, String second) {
		return first != null && !first.isBlank() ? first : (second != null ? second : "");
	}

	private static void sleepQuietly(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public synchronized void close() {
		if (closed) {
			return;
		}
		closed = true;
		if (connected && socket != null && !socket.isClosed()) {
			try {
				socket.setSoTimeout(1000);
				sendPacket(ZKTeco4370_ZkConstants.CMD_EXIT, new byte[0], secureMode);
			} catch (Exception ignored) {
			}
		}
		closeSocketOnly();
	}

	private void closeSocketOnly() {
		connected = false;
		secureMode = false;
		bulkReadSinceConnect = false;
		if (aesKey != null) {
			Arrays.fill(aesKey, (byte) 0);
			aesKey = null;
		}
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
}
