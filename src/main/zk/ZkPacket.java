package main.zk;

/**
 * Encapsulates a ZKTeco Protocol Packet and its TCP framing.
 * <p>
 * Standard ZK packet structure (8 bytes header + payload):
 * <ul>
 *   <li>Bytes 0..1: Command ID / Response Code (uint16 LE)</li>
 *   <li>Bytes 2..3: Checksum (uint16 LE, 16-bit 1's complement)</li>
 *   <li>Bytes 4..5: Session ID (uint16 LE)</li>
 *   <li>Bytes 6..7: Reply ID / Sequence number (uint16 LE)</li>
 *   <li>Bytes 8..N: Payload data</li>
 * </ul>
 * <p>
 * TCP mode prepends an 8-byte framing header:
 * <ul>
 *   <li>Bytes 0..3: TCP Magic (0x5050827D standard or 0x5050837C crypto in LE)</li>
 *   <li>Bytes 4..7: Packet Length (uint32 LE, 8 + payload.length)</li>
 * </ul>
 */
public final class ZkPacket {
	private final int commandId;
	private final int checksum;
	private final int sessionId;
	private final int replyId;
	private final byte[] payload;

	public ZkPacket(int commandId, int sessionId, int replyId, byte[] payload) {
		this(commandId, 0, sessionId, replyId, payload);
	}

	public ZkPacket(int commandId, int checksum, int sessionId, int replyId, byte[] payload) {
		this.commandId = commandId & 0xFFFF;
		this.checksum = checksum & 0xFFFF;
		this.sessionId = sessionId & 0xFFFF;
		this.replyId = replyId & 0xFFFF;
		this.payload = payload != null ? payload.clone() : new byte[0];
	}

	public int getCommandId() {
		return commandId;
	}

	public int getChecksum() {
		return checksum;
	}

	public int getSessionId() {
		return sessionId;
	}

	public int getReplyId() {
		return replyId;
	}

	public byte[] getPayload() {
		return payload.clone();
	}

	public int getPayloadLength() {
		return payload.length;
	}

	public boolean isOk() {
		return commandId == ZkConstants.CMD_ACK_OK;
	}

	public boolean isUnauth() {
		return commandId == ZkConstants.CMD_ACK_UNAUTH || commandId == ZkConstants.CMD_ACK_AUTH_LOCK;
	}

	public boolean isChallenge() {
		return commandId == ZkConstants.CMD_ACK_CHALLENGE_6001 || commandId == ZkConstants.CMD_ACK_AUTH_LOCK;
	}

	public boolean isPrepareData() {
		return commandId == ZkConstants.CMD_PREPARE_DATA;
	}

	public boolean isData() {
		return commandId == ZkConstants.CMD_DATA;
	}

	public boolean isError() {
		return commandId == ZkConstants.CMD_ACK_ERROR;
	}

	/**
	 * Builds an 8-byte ZK packet with a newly calculated 16-bit 1's complement checksum.
	 */
	public byte[] toZkBytes() {
		byte[] packet = new byte[ZkConstants.ZK_HEADER_SIZE + payload.length];

		packet[0] = (byte) (commandId & 0xFF);
		packet[1] = (byte) ((commandId >> 8) & 0xFF);
		packet[2] = 0; // Checksum placeholder
		packet[3] = 0;
		packet[4] = (byte) (sessionId & 0xFF);
		packet[5] = (byte) ((sessionId >> 8) & 0xFF);
		packet[6] = (byte) (replyId & 0xFF);
		packet[7] = (byte) ((replyId >> 8) & 0xFF);

		if (payload.length > 0) {
			System.arraycopy(payload, 0, packet, ZkConstants.ZK_HEADER_SIZE, payload.length);
		}

		int calculatedChecksum = calculateChecksum(packet);
		packet[2] = (byte) (calculatedChecksum & 0xFF);
		packet[3] = (byte) ((calculatedChecksum >> 8) & 0xFF);
		return packet;
	}

	/**
	 * Wraps the ZK packet in a standard TCP framing frame (0x5050827D).
	 */
	public byte[] toTcpFrame() {
		return toTcpFrame(ZkConstants.TCP_MAGIC);
	}

	/**
	 * Wraps the ZK packet in a TCP framing frame with specified magic header.
	 */
	public byte[] toTcpFrame(int magic) {
		byte[] zkBytes = toZkBytes();
		byte[] frame = new byte[ZkConstants.TCP_HEADER_SIZE + zkBytes.length];

		// Magic bytes on wire
		if (magic == ZkConstants.TCP_MAGIC_ALT) {
			frame[0] = 0x50;
			frame[1] = 0x50;
			frame[2] = (byte) 0x83;
			frame[3] = 0x7C;
		} else {
			frame[0] = 0x50;
			frame[1] = 0x50;
			frame[2] = (byte) 0x82;
			frame[3] = 0x7D;
		}

		// 32-bit length in Little-Endian
		int length = zkBytes.length;
		frame[4] = (byte) (length & 0xFF);
		frame[5] = (byte) ((length >> 8) & 0xFF);
		frame[6] = (byte) ((length >> 16) & 0xFF);
		frame[7] = (byte) ((length >> 24) & 0xFF);

		System.arraycopy(zkBytes, 0, frame, ZkConstants.TCP_HEADER_SIZE, zkBytes.length);
		return frame;
	}

	/**
	 * Parses a raw ZK packet from bytes (8-byte header + payload).
	 */
	public static ZkPacket parseZkBytes(byte[] raw, int offset, int length) {
		if (raw == null || length < ZkConstants.ZK_HEADER_SIZE) {
			throw new IllegalArgumentException("Invalid ZK packet length: " + length);
		}

		int cmd = (raw[offset] & 0xFF) | ((raw[offset + 1] & 0xFF) << 8);
		int chk = (raw[offset + 2] & 0xFF) | ((raw[offset + 3] & 0xFF) << 8);
		int sess = (raw[offset + 4] & 0xFF) | ((raw[offset + 5] & 0xFF) << 8);
		int reply = (raw[offset + 6] & 0xFF) | ((raw[offset + 7] & 0xFF) << 8);

		int payloadLen = length - ZkConstants.ZK_HEADER_SIZE;
		byte[] data = new byte[payloadLen];
		if (payloadLen > 0) {
			System.arraycopy(raw, offset + ZkConstants.ZK_HEADER_SIZE, data, 0, payloadLen);
		}

		return new ZkPacket(cmd, chk, sess, reply, data);
	}

	/**
	 * Calculates the 16-bit 1's complement checksum used by ZKTeco hardware.
	 */
	public static int calculateChecksum(byte[] data) {
		int l = data.length;
		int chksum = 0;
		int i = 0;

		while (l > 1) {
			int word = (data[i] & 0xFF) | ((data[i + 1] & 0xFF) << 8);
			chksum += word;
			i += 2;
			l -= 2;
		}

		if (l > 0) {
			chksum += (data[i] & 0xFF);
		}

		while ((chksum >> 16) > 0) {
			chksum = (chksum & 0xFFFF) + (chksum >> 16);
		}

		return (~chksum) & 0xFFFF;
	}

	@Override
	public String toString() {
		return String.format("ZkPacket[cmd=%d, chk=0x%04X, sess=%d, reply=%d, payloadLen=%d]",
				commandId, checksum, sessionId, replyId, payload.length);
	}
}
