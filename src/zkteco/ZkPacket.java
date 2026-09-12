package zkteco;

/**
 * ZKTeco protocol packet: 8-byte ZK header plus TCP framing.
 */
final class ZkPacket {
	private final int commandId;
	private final int checksum;
	private final int sessionId;
	private final int replyId;
	private final byte[] payload;

	ZkPacket(int commandId, int sessionId, int replyId, byte[] payload) {
		this(commandId, 0, sessionId, replyId, payload);
	}

	ZkPacket(int commandId, int checksum, int sessionId, int replyId, byte[] payload) {
		this(commandId, checksum, sessionId, replyId, payload, false);
	}

	private ZkPacket(int commandId, int checksum, int sessionId, int replyId, byte[] payload, boolean trustedPayload) {
		this.commandId = commandId & 0xFFFF;
		this.checksum = checksum & 0xFFFF;
		this.sessionId = sessionId & 0xFFFF;
		this.replyId = replyId & 0xFFFF;
		this.payload = payload == null ? new byte[0] : (trustedPayload ? payload : payload.clone());
	}

	int getCommandId() { return commandId; }
	int getChecksum() { return checksum; }
	int getSessionId() { return sessionId; }
	int getReplyId() { return replyId; }
	byte[] getPayload() { return payload.clone(); }
	byte[] payloadView() { return payload; }
	int getPayloadLength() { return payload.length; }

	boolean isOk() { return commandId == ZkConstants.CMD_ACK_OK; }
	boolean isError() { return commandId == ZkConstants.CMD_ACK_ERROR; }
	boolean isUnauth() { return commandId == ZkConstants.CMD_ACK_UNAUTH; }
	boolean isAuthChallenge() { return commandId == ZkConstants.CMD_ACK_CHALLENGE_6001; }
	boolean isAuthLock() { return commandId == ZkConstants.CMD_ACK_AUTH_LOCK; }
	boolean isData() { return commandId == ZkConstants.CMD_DATA || commandId == ZkConstants.CMD_ACK_DATA; }
	boolean isPrepareData() { return commandId == ZkConstants.CMD_PREPARE_DATA; }

	byte[] toZkBytes() {
		byte[] packet = new byte[ZkConstants.ZK_HEADER_SIZE + payload.length];
		packet[0] = (byte) (commandId & 0xFF);
		packet[1] = (byte) ((commandId >>> 8) & 0xFF);
		packet[2] = 0;
		packet[3] = 0;
		packet[4] = (byte) (sessionId & 0xFF);
		packet[5] = (byte) ((sessionId >>> 8) & 0xFF);
		packet[6] = (byte) (replyId & 0xFF);
		packet[7] = (byte) ((replyId >>> 8) & 0xFF);
		if (payload.length > 0) {
			System.arraycopy(payload, 0, packet, ZkConstants.ZK_HEADER_SIZE, payload.length);
		}

		int calculatedChecksum = calculateChecksum(packet);
		packet[2] = (byte) (calculatedChecksum & 0xFF);
		packet[3] = (byte) ((calculatedChecksum >>> 8) & 0xFF);
		return packet;
	}

	byte[] toTcpFrame(int magic) {
		byte[] zkBytes = toZkBytes();
		byte[] frame = new byte[ZkConstants.TCP_HEADER_SIZE + zkBytes.length];
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
		writeInt32LE(frame, 4, zkBytes.length);
		System.arraycopy(zkBytes, 0, frame, ZkConstants.TCP_HEADER_SIZE, zkBytes.length);
		return frame;
	}

	static ZkPacket parseZkBytes(byte[] raw, int offset, int length) {
		if (raw == null || length < ZkConstants.ZK_HEADER_SIZE) {
			throw new IllegalArgumentException("Invalid ZK packet length: " + length);
		}
		int cmd = readUInt16LE(raw, offset);
		int chk = readUInt16LE(raw, offset + 2);
		int sess = readUInt16LE(raw, offset + 4);
		int reply = readUInt16LE(raw, offset + 6);
		int payloadLength = length - ZkConstants.ZK_HEADER_SIZE;
		byte[] payload = new byte[payloadLength];
		if (payloadLength > 0) {
			System.arraycopy(raw, offset + ZkConstants.ZK_HEADER_SIZE, payload, 0, payloadLength);
		}
		return new ZkPacket(cmd, chk, sess, reply, payload, true);
	}

	private static int calculateChecksum(byte[] data) {
		int length = data.length;
		int sum = 0;
		int i = 0;
		while (length > 1) {
			sum += (data[i] & 0xFF) | ((data[i + 1] & 0xFF) << 8);
			i += 2;
			length -= 2;
		}
		if (length > 0) {
			sum += data[i] & 0xFF;
		}
		while ((sum >>> 16) > 0) {
			sum = (sum & 0xFFFF) + (sum >>> 16);
		}
		return (~sum) & 0xFFFF;
	}

	private static int readUInt16LE(byte[] data, int offset) {
		return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
	}

	private static void writeInt32LE(byte[] data, int offset, int value) {
		data[offset] = (byte) (value & 0xFF);
		data[offset + 1] = (byte) ((value >>> 8) & 0xFF);
		data[offset + 2] = (byte) ((value >>> 16) & 0xFF);
		data[offset + 3] = (byte) ((value >>> 24) & 0xFF);
	}
}
