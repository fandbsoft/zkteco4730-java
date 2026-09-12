package zk4370new;

import java.util.Arrays;

/**
 * Đại diện cho một gói tin giao thức ZKTeco cổng 4370 dành cho Firmware mới.
 * Xử lý khung truyền TCP (Magic + Length) và ZK Header (Command, Checksum, SessionID, ReplyID).
 */
public class ZkNewPacket {
	private final int commandId;
	private final int checksum;
	private final int sessionId;
	private final int replyId;
	private final byte[] payload;

	public ZkNewPacket(int commandId, int sessionId, int replyId, byte[] payload) {
		this.commandId = commandId & 0xFFFF;
		this.sessionId = sessionId & 0xFFFF;
		this.replyId = replyId & 0xFFFF;
		this.payload = (payload != null) ? payload : new byte[0];
		this.checksum = calculateChecksum(this.commandId, this.sessionId, this.replyId, this.payload);
	}

	public ZkNewPacket(int commandId, int checksum, int sessionId, int replyId, byte[] payload) {
		this.commandId = commandId & 0xFFFF;
		this.checksum = checksum & 0xFFFF;
		this.sessionId = sessionId & 0xFFFF;
		this.replyId = replyId & 0xFFFF;
		this.payload = (payload != null) ? payload : new byte[0];
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
		return payload;
	}

	public int getPayloadLength() {
		return payload.length;
	}

	public boolean isOk() {
		return commandId == ZkNewConstants.CMD_ACK_OK;
	}

	public boolean isError() {
		return commandId == ZkNewConstants.CMD_ACK_ERROR;
	}

	public boolean isUnauth() {
		return commandId == ZkNewConstants.CMD_ACK_UNAUTH;
	}

	public boolean isAuthChallenge6001() {
		return commandId == ZkNewConstants.CMD_ACK_CHALLENGE_6001;
	}

	public boolean isAuthLock2032() {
		return commandId == ZkNewConstants.CMD_ACK_AUTH_LOCK;
	}

	public boolean isData() {
		return commandId == ZkNewConstants.CMD_DATA || commandId == ZkNewConstants.CMD_ACK_DATA;
	}

	public boolean isPrepareData() {
		return commandId == ZkNewConstants.CMD_PREPARE_DATA;
	}

	/**
	 * Đóng gói tin ZK thành khung truyền TCP chuẩn với 8 bytes TCP Header (Magic + Length).
	 */
	public byte[] toTcpFrame(int magic) {
		byte[] zkBytes = toZkBytes();
		byte[] frame = new byte[ZkNewConstants.TCP_HEADER_SIZE + zkBytes.length];

		// 1. TCP Magic (4 bytes)
		frame[0] = (byte) ((magic >> 24) & 0xFF);
		frame[1] = (byte) ((magic >> 16) & 0xFF);
		frame[2] = (byte) ((magic >> 8) & 0xFF);
		frame[3] = (byte) (magic & 0xFF);

		// 2. Length (4 bytes Little-Endian uint32)
		frame[4] = (byte) (zkBytes.length & 0xFF);
		frame[5] = (byte) ((zkBytes.length >> 8) & 0xFF);
		frame[6] = (byte) ((zkBytes.length >> 16) & 0xFF);
		frame[7] = (byte) ((zkBytes.length >> 24) & 0xFF);
		System.arraycopy(zkBytes, 0, frame, ZkNewConstants.TCP_HEADER_SIZE, zkBytes.length);

		return frame;
	}

	/**
	 * Đóng gói phần ZK Packet thô (8-byte header + payload), chưa có TCP header.
	 */
	public byte[] toZkBytes() {
		byte[] zkBytes = new byte[ZkNewConstants.ZK_HEADER_SIZE + payload.length];
		zkBytes[0] = (byte) (commandId & 0xFF);
		zkBytes[1] = (byte) ((commandId >> 8) & 0xFF);
		zkBytes[2] = (byte) (checksum & 0xFF);
		zkBytes[3] = (byte) ((checksum >> 8) & 0xFF);
		zkBytes[4] = (byte) (sessionId & 0xFF);
		zkBytes[5] = (byte) ((sessionId >> 8) & 0xFF);
		zkBytes[6] = (byte) (replyId & 0xFF);
		zkBytes[7] = (byte) ((replyId >> 8) & 0xFF);
		if (payload.length > 0) {
			System.arraycopy(payload, 0, zkBytes, ZkNewConstants.ZK_HEADER_SIZE, payload.length);
		}
		return zkBytes;
	}

	/**
	 * Phân tích mảng byte nhận được từ socket thành đối tượng ZkNewPacket.
	 */
	public static ZkNewPacket parseZkBytes(byte[] zkBytes, int offset, int length) {
		if (length < ZkNewConstants.ZK_HEADER_SIZE) {
			throw new IllegalArgumentException("Dữ liệu ZK không đủ 8 bytes header: " + length);
		}

		int cmd = (zkBytes[offset] & 0xFF) | ((zkBytes[offset + 1] & 0xFF) << 8);
		int chk = (zkBytes[offset + 2] & 0xFF) | ((zkBytes[offset + 3] & 0xFF) << 8);
		int sess = (zkBytes[offset + 4] & 0xFF) | ((zkBytes[offset + 5] & 0xFF) << 8);
		int rep = (zkBytes[offset + 6] & 0xFF) | ((zkBytes[offset + 7] & 0xFF) << 8);

		int dataLen = length - ZkNewConstants.ZK_HEADER_SIZE;
		byte[] data = new byte[dataLen];
		if (dataLen > 0) {
			System.arraycopy(zkBytes, offset + ZkNewConstants.ZK_HEADER_SIZE, data, 0, dataLen);
		}

		return new ZkNewPacket(cmd, chk, sess, rep, data);
	}

	/**
	 * Thuật toán tính Checksum 16-bit 1's complement chuẩn của ZKTeco.
	 */
	public static int calculateChecksum(int commandId, int sessionId, int replyId, byte[] payload) {
		byte[] headerAndData = new byte[ZkNewConstants.ZK_HEADER_SIZE + (payload != null ? payload.length : 0)];
		headerAndData[0] = (byte) (commandId & 0xFF);
		headerAndData[1] = (byte) ((commandId >> 8) & 0xFF);
		headerAndData[2] = 0; // Checksum = 0 khi tính toán
		headerAndData[3] = 0;
		headerAndData[4] = (byte) (sessionId & 0xFF);
		headerAndData[5] = (byte) ((sessionId >> 8) & 0xFF);
		headerAndData[6] = (byte) (replyId & 0xFF);
		headerAndData[7] = (byte) ((replyId >> 8) & 0xFF);

		if (payload != null && payload.length > 0) {
			System.arraycopy(payload, 0, headerAndData, 8, payload.length);
		}

		int sum = 0;
		int len = headerAndData.length;
		int i = 0;

		while (len > 1) {
			sum += ((headerAndData[i + 1] & 0xFF) << 8) | (headerAndData[i] & 0xFF);
			i += 2;
			len -= 2;
			if ((sum & 0x80000000) != 0) {
				sum = (sum & 0xFFFF) + (sum >> 16);
			}
		}

		if (len > 0) {
			sum += (headerAndData[i] & 0xFF);
		}

		while ((sum >> 16) != 0) {
			sum = (sum & 0xFFFF) + (sum >> 16);
		}

		return (~sum) & 0xFFFF;
	}

	@Override
	public String toString() {
		return String.format("ZkNewPacket[Cmd=%d (0x%04X), Sess=%d, Rep=%d, Chk=0x%04X, PayloadLen=%d]",
				commandId, commandId, sessionId, replyId, checksum, payload.length);
	}
}
