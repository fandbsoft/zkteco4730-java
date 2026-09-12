package zkteco;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Tiện ích xử lý mã hóa, scrambling Comm Key và phân tích bảo mật cho Firmware mới.
 */
public final class ZkCrypto {
	private ZkCrypto() {}

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final int DMC_CRC_SEED = 0xFFEEBBAA;
	private static final int[] CRC32_TABLE = buildCrc32Table();

	/*
	 * Keystream của hàm DMC transform trong ZKEMCrypto.dll (RVA 0x39D0).
	 * Hàm này là XOR stream đối xứng; 2048 bytes đủ cho các payload DMC của
	 * handshake 10063/10064 (public key + secret). Không gọi DLL ở runtime.
	 */
	private static final byte[] DMC_KEYSTREAM = Base64.getDecoder().decode(
			"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");

	public static final class DmcMessage {
		private final int type;
		private final byte[] body;

		private DmcMessage(int type, byte[] body) {
			this.type = type & 0xFF;
			this.body = body != null ? body.clone() : new byte[0];
		}

		public int getType() {
			return type;
		}

		public byte[] getBody() {
			return body.clone();
		}
	}

	/**
	 * Tạo payload 4-byte scrambled key cho lệnh CMD_AUTH (1102) từ password và sessionId.
	 *
	 * @param password Mã Comm Key (ví dụ: 111111, 0, 123456)
	 * @param sessionId Session ID được cấp từ thiết bị sau khi kết nối
	 * @param ticks Salt timing (chuẩn mặc định: 50, hoặc 0)
	 * @return 4 bytes mã khóa xác thực
	 */
	public static byte[] makeCommKey(int password, int sessionId, int ticks) {
		long k = 0;
		for (int i = 0; i < 32; i++) {
			if ((password & (1 << i)) != 0) {
				k = (k << 1) | 1L;
			} else {
				k = k << 1;
			}
		}
		k = (k + (sessionId & 0xFFFFL)) & 0xFFFFFFFFL;

		// Hoán vị 2 từ 16-bit
		int s0 = (int) ((k >> 16) & 0xFF);
		int s1 = (int) ((k >> 24) & 0xFF);
		int s2 = (int) (k & 0xFF);
		int s3 = (int) ((k >> 8) & 0xFF);

		// XOR với chuỗi khóa định danh 'S', 'O', 'Z', 'K'
		s0 ^= 'S';
		s1 ^= 'O';
		s2 ^= 'Z';
		s3 ^= 'K';

		// Trộn byte salt B (ticks & 0xFF) tại vị trí byte thứ 3
		int b = ticks & 0xFF;
		byte[] result = new byte[4];
		result[0] = (byte) (s0 ^ b);
		result[1] = (byte) (s1 ^ b);
		result[2] = (byte) b;
		result[3] = (byte) (s3 ^ b);
		return result;
	}

	public static byte[] makeCommKeyWithCurrentTick(int password, int sessionId) {
		return makeCommKeyNativeOrder(password, sessionId, resolveAuthTickLowByte());
	}

	private static int resolveAuthTickLowByte() {
		String override = System.getProperty("zkteco.authTick");
		if (override != null && !override.isBlank()) {
			return Integer.decode(override.trim()) & 0xFF;
		}
		return (int) ((System.nanoTime() / 1_000_000L) & 0xFF);
	}

	/**
	 * Biến thể đúng byte-order của commpro.dll+0x58C0. Một số firmware mới chỉ dùng
	 * lệnh này như stage thử trước khi chuyển sang CMD_AUTH_EXT (1106).
	 */
	public static byte[] makeCommKeyNativeOrder(int password, int sessionId, int tickLowByte) {
		long key = 0;
		long bit = 1;
		for (int i = 0; i < 32; i++) {
			key = (key << 1) & 0xFFFFFFFFL;
			if (((long) password & bit) != 0) {
				key |= 1L;
			}
			bit = (bit << 1) & 0xFFFFFFFFL;
			if (bit == 0) {
				bit = 1;
			}
		}
		key = (key + (sessionId & 0xFFFFL)) & 0xFFFFFFFFL;

		int b0 = ((int) key & 0xFF) ^ 0x5A;
		int b1 = (((int) key >>> 8) & 0xFF) ^ 0x4B;
		int b2 = (((int) key >>> 16) & 0xFF) ^ 0x53;
		int b3 = (((int) key >>> 24) & 0xFF) ^ 0x4F;
		int tick = tickLowByte & 0xFF;
		return new byte[] {
				(byte) (b2 ^ tick),
				(byte) (b3 ^ tick),
				(byte) tick,
				(byte) (b1 ^ tick)
		};
	}

	public static byte[] buildExtendedAuthPayload(String passwordText) {
		byte[] result = new byte[36];
		byte[] authKey = makeZkemSdkUtilsKey(passwordText);
		System.arraycopy(authKey, 0, result, 0, authKey.length);
		result[32] = (byte) 0xB3;
		result[33] = (byte) 0xF6;
		result[34] = 0x00;
		result[35] = 0x01;
		return result;
	}

	/**
	 * Port thuần Java của export ZKEMSDKUtils_MakeKey trong zkemsdkutils.dll.
	 * Công thức được đối chiếu với DLL hãng:
	 *
	 * <pre>
	 * seed = hex_lower(HMAC_SHA256("ZKTeco-secret-key", "StandalongComm"))
	 * key  = HMAC_SHA256(seed, hex_lower(MD5(password)))
	 * </pre>
	 */
	public static byte[] makeZkemSdkUtilsKey(String passwordText) {
		try {
			byte[] passwordBytes = (passwordText == null ? "" : passwordText).getBytes(StandardCharsets.US_ASCII);
			byte[] seed = toLowerHex(hmacSha256(
					"ZKTeco-secret-key".getBytes(StandardCharsets.US_ASCII),
					"StandalongComm".getBytes(StandardCharsets.US_ASCII)))
					.getBytes(StandardCharsets.US_ASCII);
			byte[] passwordMd5Hex = toLowerHex(MessageDigest.getInstance("MD5").digest(passwordBytes))
					.getBytes(StandardCharsets.US_ASCII);
			return hmacSha256(seed, passwordMd5Hex);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Không thể tạo ZKEMSDKUtils MakeKey", e);
		}
	}

	public static KeyPair generateClientRsaKeyPair() throws GeneralSecurityException {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048, RANDOM);
		return generator.generateKeyPair();
	}

	public static int nextClientSecret() {
		long epochSeconds = System.currentTimeMillis() / 1000L;
		int value = (int) epochSeconds;
		return value != 0 ? value : 1;
	}

	public static byte[] buildDmcPayload(int type, byte[] body) {
		byte[] plain = buildDmcPlain(type, body);
		byte[] transformed = dmcTransform(plain);
		return Base64.getEncoder().encode(transformed);
	}

	public static DmcMessage parseDmcPayload(byte[] asciiPayload) throws IOException {
		byte[] transformed = Base64.getDecoder().decode(new String(asciiPayload, StandardCharsets.US_ASCII).trim());
		return parseDmcPlain(dmcTransform(transformed));
	}

	public static byte[] buildRsaEncryptedDmcPayload(int type, byte[] body, PublicKey publicKey)
			throws GeneralSecurityException {
		byte[] plain = buildDmcPlain(type, body);
		byte[] encrypted = rsaPkcs1Crypt(plain, publicKey, Cipher.ENCRYPT_MODE);
		return Base64.getEncoder().encode(encrypted);
	}

	public static DmcMessage parseRsaEncryptedDmcPayload(byte[] asciiPayload, java.security.PrivateKey privateKey)
			throws GeneralSecurityException, IOException {
		byte[] encrypted = Base64.getDecoder().decode(new String(asciiPayload, StandardCharsets.US_ASCII).trim());
		byte[] plain = rsaPkcs1Crypt(encrypted, privateKey, Cipher.DECRYPT_MODE);
		return parseDmcPlain(plain);
	}

	public static String toPkcs1PublicPem(RSAPublicKey key) {
		try {
			byte[] der = derSequence(
					derInteger(unsignedBytes(key.getModulus())),
					derInteger(unsignedBytes(key.getPublicExponent())));
			String body = Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(der);
			return "-----BEGIN RSA PUBLIC KEY-----\n" + body + "\n-----END RSA PUBLIC KEY-----\n";
		} catch (IOException e) {
			throw new IllegalStateException("Không thể dựng RSA public PEM", e);
		}
	}

	public static RSAPublicKey parsePkcs1PublicPem(String pem) throws GeneralSecurityException, IOException {
		String base64 = pem
				.replace("-----BEGIN RSA PUBLIC KEY-----", "")
				.replace("-----END RSA PUBLIC KEY-----", "")
				.replaceAll("[^A-Za-z0-9+/=]", "");
		byte[] der = Base64.getDecoder().decode(base64);
		DerReader reader = new DerReader(der);
		reader.expect(0x30);
		int sequenceLength = reader.readLength();
		int seqEnd = reader.position() + sequenceLength;
		BigInteger modulus = new BigInteger(1, reader.readInteger());
		BigInteger exponent = new BigInteger(1, reader.readInteger());
		if (reader.position() > seqEnd) {
			throw new IOException("RSA public key DER dư dữ liệu: pos=" + reader.position()
					+ ", seqEnd=" + seqEnd + ", head=" + toHex(der, 0, Math.min(12, der.length)));
		}
		return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
	}

	public static byte[] int32LE(int value) {
		byte[] out = new byte[4];
		writeInt32LE(out, 0, value);
		return out;
	}

	public static byte[] deriveAesKey(int clientSecret, int serverSecret) {
		long c = Integer.toUnsignedLong(clientSecret);
		long s = Integer.toUnsignedLong(serverSecret);
		long a = (c ^ s) & 0xFFFFFFFFL;
		long b = (((s << 8) & 0xFFFFFFFFL) ^ c) & 0xFFFFFFFFL;
		long d = (((c << 8) & 0xFFFFFFFFL) ^ s) & 0xFFFFFFFFL;
		byte[] text = (Long.toUnsignedString(a) + "." + Long.toUnsignedString(b) + "." + Long.toUnsignedString(d))
				.getBytes(StandardCharsets.US_ASCII);
		byte[] key = new byte[32];
		System.arraycopy(text, 0, key, 0, Math.min(text.length, key.length));
		return key;
	}

	public static byte[] encryptSecureTcpFrame(byte[] zkBytes, byte[] aesKey) throws GeneralSecurityException {
		int innerLength = 6 + zkBytes.length + 4;
		int paddedLength = ((innerLength + 15) / 16) * 16;
		byte[] inner = new byte[paddedLength];
		inner[0] = (byte) 0xAB;
		inner[1] = (byte) 0xBB;
		writeInt32LE(inner, 2, zkBytes.length);
		System.arraycopy(zkBytes, 0, inner, 6, zkBytes.length);
		writeInt32LE(inner, 6 + zkBytes.length, crc32Seeded(zkBytes, 0, zkBytes.length));

		byte[] encrypted = aesCbcZeroIv(inner, aesKey, Cipher.ENCRYPT_MODE);
		byte[] frame = new byte[ZkConstants.TCP_HEADER_SIZE + encrypted.length];
		frame[0] = 0x50;
		frame[1] = 0x50;
		frame[2] = (byte) 0x83;
		frame[3] = 0x7C;
		writeInt32LE(frame, 4, encrypted.length);
		System.arraycopy(encrypted, 0, frame, ZkConstants.TCP_HEADER_SIZE, encrypted.length);
		return frame;
	}

	public static byte[] decryptSecurePayload(byte[] encrypted, byte[] aesKey) throws GeneralSecurityException, IOException {
		if (encrypted.length == 0 || (encrypted.length % 16) != 0) {
			throw new IOException("Secure payload length không chia hết cho AES block: " + encrypted.length);
		}
		byte[] inner = aesCbcZeroIv(encrypted, aesKey, Cipher.DECRYPT_MODE);
		if (inner.length < 10 || !((inner[0] == (byte) 0xAA || inner[0] == (byte) 0xAB) && inner[1] == (byte) 0xBB)) {
			throw new IOException("Secure payload magic không hợp lệ");
		}
		int zkLength = readInt32LE(inner, 2);
		if (zkLength < ZkConstants.ZK_HEADER_SIZE || zkLength + 10 > inner.length) {
			throw new IOException("Secure inner ZK length không hợp lệ: " + zkLength);
		}
		int expectedCrc = readInt32LE(inner, 6 + zkLength);
		int actualCrc = crc32Seeded(inner, 6, zkLength);
		if (expectedCrc != 0 && expectedCrc != actualCrc) {
			throw new IOException("Secure inner CRC không khớp");
		}
		return Arrays.copyOfRange(inner, 6, 6 + zkLength);
	}

	private static byte[] buildDmcPlain(int type, byte[] body) {
		byte[] data = body != null ? body : new byte[0];
		byte[] plain = new byte[5 + data.length + 4];
		plain[0] = (byte) 0xAA;
		plain[1] = (byte) 0xBB;
		plain[2] = (byte) (type & 0xFF);
		writeUInt16LE(plain, 3, data.length);
		System.arraycopy(data, 0, plain, 5, data.length);
		writeInt32LE(plain, 5 + data.length, crc32Seeded(plain, 5, data.length));
		return plain;
	}

	private static DmcMessage parseDmcPlain(byte[] plain) throws IOException {
		if (plain.length < 9 || plain[0] != (byte) 0xAA || plain[1] != (byte) 0xBB) {
			throw new IOException("DMC magic không hợp lệ");
		}
		int type = plain[2] & 0xFF;
		int length = readUInt16LE(plain, 3);
		if (length < 0 || 5 + length + 4 > plain.length) {
			throw new IOException("DMC length không hợp lệ: " + length);
		}
		int expectedCrc = readInt32LE(plain, 5 + length);
		int actualCrc = crc32Seeded(plain, 5, length);
		if (expectedCrc != actualCrc) {
			throw new IOException("DMC CRC không khớp");
		}
		return new DmcMessage(type, Arrays.copyOfRange(plain, 5, 5 + length));
	}

	private static byte[] dmcTransform(byte[] input) {
		if (input.length > DMC_KEYSTREAM.length) {
			throw new IllegalArgumentException("DMC payload vượt giới hạn keystream thuần Java: " + input.length);
		}
		byte[] out = input.clone();
		for (int i = 0; i < out.length; i++) {
			out[i] ^= DMC_KEYSTREAM[i];
		}
		return out;
	}

	private static byte[] rsaPkcs1Crypt(byte[] input, java.security.Key key, int mode) throws GeneralSecurityException {
		Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
		cipher.init(mode, key);
		return cipher.doFinal(input);
	}

	private static byte[] aesEcb(byte[] input, byte[] key, int mode) throws GeneralSecurityException {
		Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
		cipher.init(mode, new SecretKeySpec(key, "AES"));
		return cipher.doFinal(input);
	}

	private static byte[] aesCbcZeroIv(byte[] input, byte[] key, int mode) throws GeneralSecurityException {
		Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
		cipher.init(mode, new SecretKeySpec(key, "AES"), new IvParameterSpec(new byte[16]));
		return cipher.doFinal(input);
	}

	private static byte[] hmacSha256(byte[] key, byte[] message) throws GeneralSecurityException {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(key, "HmacSHA256"));
		return mac.doFinal(message);
	}

	private static String toLowerHex(byte[] data) {
		char[] out = new char[data.length * 2];
		final char[] hex = "0123456789abcdef".toCharArray();
		for (int i = 0; i < data.length; i++) {
			int value = data[i] & 0xFF;
			out[i * 2] = hex[value >>> 4];
			out[i * 2 + 1] = hex[value & 0x0F];
		}
		return new String(out);
	}

	private static int crc32Seeded(byte[] data, int offset, int length) {
		int crc = DMC_CRC_SEED;
		for (int i = 0; i < length; i++) {
			crc = (crc >>> 8) ^ CRC32_TABLE[(data[offset + i] ^ crc) & 0xFF];
		}
		return crc;
	}

	private static int[] buildCrc32Table() {
		int[] table = new int[256];
		for (int i = 0; i < table.length; i++) {
			int c = i;
			for (int j = 0; j < 8; j++) {
				c = ((c & 1) != 0) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
			}
			table[i] = c;
		}
		return table;
	}

	private static byte[] unsignedBytes(BigInteger value) {
		byte[] bytes = value.toByteArray();
		if (bytes.length > 1 && bytes[0] == 0) {
			return Arrays.copyOfRange(bytes, 1, bytes.length);
		}
		return bytes;
	}

	private static byte[] derSequence(byte[]... parts) throws IOException {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		for (byte[] part : parts) {
			body.write(part);
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(0x30);
		writeDerLength(out, body.size());
		out.write(body.toByteArray());
		return out.toByteArray();
	}

	private static byte[] derInteger(byte[] value) throws IOException {
		int start = 0;
		while (start < value.length - 1 && value[start] == 0) {
			start++;
		}
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		if ((value[start] & 0x80) != 0) {
			body.write(0);
		}
		body.write(value, start, value.length - start);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(0x02);
		writeDerLength(out, body.size());
		out.write(body.toByteArray());
		return out.toByteArray();
	}

	private static void writeDerLength(ByteArrayOutputStream out, int length) {
		if (length < 128) {
			out.write(length);
			return;
		}
		int bytes = 0;
		int tmp = length;
		while (tmp > 0) {
			bytes++;
			tmp >>>= 8;
		}
		out.write(0x80 | bytes);
		for (int i = bytes - 1; i >= 0; i--) {
			out.write((length >>> (8 * i)) & 0xFF);
		}
	}

	private static int readUInt16LE(byte[] b, int offset) {
		return (b[offset] & 0xFF) | ((b[offset + 1] & 0xFF) << 8);
	}

	private static int readInt32LE(byte[] b, int offset) {
		return (b[offset] & 0xFF)
				| ((b[offset + 1] & 0xFF) << 8)
				| ((b[offset + 2] & 0xFF) << 16)
				| ((b[offset + 3] & 0xFF) << 24);
	}

	private static void writeUInt16LE(byte[] b, int offset, int value) {
		b[offset] = (byte) (value & 0xFF);
		b[offset + 1] = (byte) ((value >>> 8) & 0xFF);
	}

	private static void writeInt32LE(byte[] b, int offset, int value) {
		b[offset] = (byte) (value & 0xFF);
		b[offset + 1] = (byte) ((value >>> 8) & 0xFF);
		b[offset + 2] = (byte) ((value >>> 16) & 0xFF);
		b[offset + 3] = (byte) ((value >>> 24) & 0xFF);
	}

	private static String toHex(byte[] data, int offset, int length) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < length; i++) {
			if (i > 0) sb.append(' ');
			sb.append(String.format("%02X", data[offset + i]));
		}
		return sb.toString();
	}

	private static final class DerReader {
		private final byte[] data;
		private int pos;

		private DerReader(byte[] data) {
			this.data = data;
		}

		private int position() {
			return pos;
		}

		private void expect(int expected) throws IOException {
			int actual = read();
			if (actual != expected) {
				throw new IOException("DER tag không hợp lệ: " + actual);
			}
		}

		private int readLength() throws IOException {
			int first = read();
			if ((first & 0x80) == 0) {
				return first;
			}
			int count = first & 0x7F;
			if (count == 0 || count > 4) {
				throw new IOException("DER length không hợp lệ");
			}
			int value = 0;
			for (int i = 0; i < count; i++) {
				value = (value << 8) | read();
			}
			return value;
		}

		private byte[] readInteger() throws IOException {
			expect(0x02);
			int length = readLength();
			if (length <= 0 || pos + length > data.length) {
				throw new IOException("DER integer length không hợp lệ");
			}
			byte[] value = Arrays.copyOfRange(data, pos, pos + length);
			pos += length;
			if (value.length > 1 && value[0] == 0) {
				value = Arrays.copyOfRange(value, 1, value.length);
			}
			return value;
		}

		private int read() throws IOException {
			if (pos >= data.length) {
				throw new IOException("DER hết dữ liệu");
			}
			return data[pos++] & 0xFF;
		}
	}
}

