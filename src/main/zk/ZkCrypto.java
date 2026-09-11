package main.zk;

/**
 * Cryptographic and key scrambling utilities for ZKTeco protocol authentication.
 * Implements the MakeKey algorithm from ZKTeco commpro.c specification.
 */
public final class ZkCrypto {
	private ZkCrypto() {}

	/**
	 * Scrambles communication password and session ID to create the authentication key
	 * for CMD_AUTH (1102).
	 *
	 * @param password The device communication password (e.g. 111111)
	 * @param sessionId The session ID received during CMD_CONNECT handshake
	 * @param ticks Timing/salt parameter (standard default is 50)
	 * @return 4-byte scrambled key payload to be sent in CMD_AUTH
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

		// Hoán vị 2 từ 16-bit (word swap) của số 32-bit LE
		int s0 = (int) ((k >> 16) & 0xFF);
		int s1 = (int) ((k >> 24) & 0xFF);
		int s2 = (int) (k & 0xFF);
		int s3 = (int) ((k >> 8) & 0xFF);

		// XOR với chuỗi khóa 'S', 'O', 'Z', 'K'
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
}
