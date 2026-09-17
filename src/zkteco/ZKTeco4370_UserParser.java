package zkteco;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

final class ZKTeco4370_UserParser {
	private ZKTeco4370_UserParser() {}

	static List<ZKTeco4370_UserInfo> parse(byte[] rawData) {
		if (rawData == null || rawData.length < 28) {
			return Collections.emptyList();
		}
		int start = detectStartOffset(rawData);
		int remaining = rawData.length - start;
		int recordSize = detectRecordSize(remaining);
		if (recordSize <= 0) {
			return Collections.emptyList();
		}

		List<ZKTeco4370_UserInfo> users = new ArrayList<>();
		int count = remaining / recordSize;
		for (int i = 0; i < count; i++) {
			ZKTeco4370_UserInfo user = recordSize == 72
					? parseRecord72(rawData, start + i * recordSize)
					: parseRecord28(rawData, start + i * recordSize);
			if (user != null && !user.getUserId().isEmpty()) {
				users.add(user);
			}
		}
		return users;
	}

	static ZKTeco4370_StreamingParser newStreamingParser(Consumer<ZKTeco4370_UserInfo> consumer) {
		return new ZKTeco4370_StreamingParser(consumer);
	}

	static final class ZKTeco4370_StreamingParser {
		private static final byte[] EMPTY = new byte[0];

		private final Consumer<ZKTeco4370_UserInfo> consumer;
		private byte[] pending = EMPTY;
		private int totalBytes;
		private int recordSize;
		private boolean initialized;

		private ZKTeco4370_StreamingParser(Consumer<ZKTeco4370_UserInfo> consumer) {
			if (consumer == null) {
				throw new IllegalArgumentException("User consumer must not be null");
			}
			this.consumer = consumer;
		}

		void start(int totalBytes) {
			this.totalBytes = Math.max(0, totalBytes);
			this.pending = EMPTY;
			this.recordSize = 0;
			this.initialized = false;
		}

		void accept(byte[] data, int offset, int length) {
			if (data == null || length <= 0) {
				return;
			}
			if (!initialized) {
				append(data, offset, length);
				if (initialize(false)) {
					parsePendingRecords();
				}
				return;
			}
			parseChunk(data, offset, length);
		}

		void finish() {
			if (!initialized && initialize(true)) {
				parsePendingRecords();
			}
			pending = EMPTY;
		}

		private boolean initialize(boolean finishing) {
			if (initialized) {
				return true;
			}
			if (totalBytes < 28) {
				initialized = true;
				return true;
			}
			if (pending.length < 4 && !finishing) {
				return false;
			}

			int startOffset = detectStartOffset(pending, totalBytes);
			int dataBytes = Math.max(0, totalBytes - startOffset);
			recordSize = detectRecordSize(dataBytes);
			if (pending.length < startOffset && !finishing) {
				return false;
			}
			if (startOffset > 0) {
				pending = pending.length > startOffset
						? Arrays.copyOfRange(pending, startOffset, pending.length)
						: EMPTY;
			}
			initialized = true;
			return true;
		}

		private static int detectStartOffset(byte[] firstBytes, int totalBytes) {
			if (firstBytes.length < 4 || totalBytes < 32) {
				return 0;
			}
			long header = Integer.toUnsignedLong(ZKTeco4370_RecordParser.readInt32LE(firstBytes, 0));
			long remaining = totalBytes - 4L;
			if (header > 0 && header <= remaining && (header % 72 == 0 || header % 28 == 0)) {
				return 4;
			}
			return 0;
		}

		private void append(byte[] data, int offset, int length) {
			if (length <= 0) {
				return;
			}
			int current = pending.length;
			byte[] combined = Arrays.copyOf(pending, current + length);
			System.arraycopy(data, offset, combined, current, length);
			pending = combined;
		}

		private void parsePendingRecords() {
			if (recordSize <= 0 || pending.length < recordSize) {
				return;
			}
			int parseBytes = (pending.length / recordSize) * recordSize;
			for (int offset = 0; offset < parseBytes; offset += recordSize) {
				ZKTeco4370_UserInfo user = recordSize == 72 ? parseRecord72(pending, offset) : parseRecord28(pending, offset);
				if (user != null && !user.getUserId().isEmpty()) {
					consumer.accept(user);
				}
			}
			pending = pending.length > parseBytes
					? Arrays.copyOfRange(pending, parseBytes, pending.length)
					: EMPTY;
		}

		private void parseChunk(byte[] data, int offset, int length) {
			if (recordSize <= 0) {
				return;
			}
			int cursor = offset;
			int remaining = length;
			if (pending.length > 0) {
				int needed = recordSize - pending.length;
				int taken = Math.min(needed, remaining);
				append(data, cursor, taken);
				cursor += taken;
				remaining -= taken;
				parsePendingRecords();
			}
			int parseBytes = (remaining / recordSize) * recordSize;
			for (int current = cursor; current < cursor + parseBytes; current += recordSize) {
				ZKTeco4370_UserInfo user = recordSize == 72 ? parseRecord72(data, current) : parseRecord28(data, current);
				if (user != null && !user.getUserId().isEmpty()) {
					consumer.accept(user);
				}
			}
			int tailOffset = cursor + parseBytes;
			int tailLength = remaining - parseBytes;
			if (tailLength > 0) {
				pending = Arrays.copyOfRange(data, tailOffset, tailOffset + tailLength);
			}
		}
	}

	private static int detectStartOffset(byte[] rawData) {
		if (rawData.length >= 32) {
			long header = Integer.toUnsignedLong(ZKTeco4370_RecordParser.readInt32LE(rawData, 0));
			long remaining = rawData.length - 4L;
			if (header > 0 && header <= remaining && (header % 72 == 0 || header % 28 == 0)) {
				return 4;
			}
		}
		return 0;
	}

	private static int detectRecordSize(int bytes) {
		if (bytes > 0 && bytes % 72 == 0) {
			return 72;
		}
		if (bytes > 0 && bytes % 28 == 0) {
			return 28;
		}
		return 0;
	}

	private static ZKTeco4370_UserInfo parseRecord72(byte[] data, int offset) {
		if (offset + 72 > data.length) {
			return null;
		}
		int uid = ZKTeco4370_RecordParser.readUInt16LE(data, offset);
		int rawPrivilege = data[offset + 2] & 0xFF;
		int privilege = rawPrivilege == 0x0E ? 3 : (rawPrivilege & 0x0E) >> 1;
		if (rawPrivilege == 0) {
			privilege = 0;
		}
		boolean enabled = (rawPrivilege & 0x01) == 0;
		String password = readNullTerminated(data, offset + 3, 8, StandardCharsets.US_ASCII);
		String name = readNullTerminated(data, offset + 11, 24, StandardCharsets.UTF_8);
		long cardNumber = Integer.toUnsignedLong(ZKTeco4370_RecordParser.readInt32LE(data, offset + 35));
		int group = data[offset + 40] & 0xFF;
		int timeZone = ZKTeco4370_RecordParser.readUInt16LE(data, offset + 42);
		String pin = readNullTerminated(data, offset + 48, 24, StandardCharsets.US_ASCII);
		if (pin.isEmpty() && uid > 0) {
			pin = String.valueOf(uid);
		}
		return new ZKTeco4370_UserInfo(uid, pin, name, password, cardNumber, privilege, enabled, group, timeZone, null);
	}

	private static ZKTeco4370_UserInfo parseRecord28(byte[] data, int offset) {
		if (offset + 28 > data.length) {
			return null;
		}
		int uid = ZKTeco4370_RecordParser.readUInt16LE(data, offset);
		int rawPrivilege = data[offset + 2] & 0xFF;
		int privilege = rawPrivilege == 0x0E ? 3 : (rawPrivilege & 0x0E) >> 1;
		if (rawPrivilege == 0) {
			privilege = 0;
		}
		boolean enabled = (rawPrivilege & 0x01) == 0;
		String password = readNullTerminated(data, offset + 3, 5, StandardCharsets.US_ASCII);
		String name = readNullTerminated(data, offset + 8, 8, StandardCharsets.UTF_8);
		long cardNumber = Integer.toUnsignedLong(ZKTeco4370_RecordParser.readInt32LE(data, offset + 16));
		int group = data[offset + 21] & 0xFF;
		int timeZone = ZKTeco4370_RecordParser.readUInt16LE(data, offset + 22);
		long pin2 = Integer.toUnsignedLong(ZKTeco4370_RecordParser.readInt32LE(data, offset + 24));
		String pin = pin2 > 0 ? String.valueOf(pin2) : String.valueOf(uid);
		return new ZKTeco4370_UserInfo(uid, pin, name, password, cardNumber, privilege, enabled, group, timeZone, null);
	}

	private static String readNullTerminated(byte[] data, int offset, int maxLen, java.nio.charset.Charset charset) {
		int end = offset;
		int limit = Math.min(data.length, offset + maxLen);
		while (end < limit && data[end] != 0) {
			end++;
		}
		return end > offset ? new String(data, offset, end - offset, charset).trim() : "";
	}
}
