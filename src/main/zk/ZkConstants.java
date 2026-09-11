package main.zk;

/**
 * Constants used in the ZKTeco Standalone Socket Protocol (Port 4370).
 * <p>
 * Defines networking defaults, TCP magic framing signatures, command opcodes,
 * status acknowledgment codes, and modern ZKCommuCrypto encryption opcodes.
 */
public final class ZkConstants {
	private ZkConstants() {}

	// Networking defaults
	public static final int DEFAULT_PORT = 4370;
	public static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
	public static final int DEFAULT_READ_TIMEOUT_MS = 30_000;

	// Magic framing headers for TCP mode
	public static final int TCP_MAGIC = 0x5050827D;     // 50 50 82 7D on wire (7D 82 50 50 in Little-Endian)
	public static final int TCP_MAGIC_ALT = 0x5050837C; // 50 50 83 7C on wire (7C 83 50 50 in Little-Endian)
	public static final byte[] TCP_MAGIC_BYTES = new byte[] { 0x50, 0x50, (byte) 0x82, 0x7D };
	public static final byte[] TCP_MAGIC_ALT_BYTES = new byte[] { 0x50, 0x50, (byte) 0x83, 0x7C };

	// Legacy & Standard Protocol Commands
	public static final int CMD_CONNECT = 1000;
	public static final int CMD_EXIT = 1001;
	public static final int CMD_ENABLEDEVICE = 1002;
	public static final int CMD_DISABLEDEVICE = 1003;
	public static final int CMD_RESTART = 1004;
	public static final int CMD_POWEROFF = 1005;
	public static final int CMD_SLEEP = 1006;
	public static final int CMD_RESUME = 1007;
	public static final int CMD_TESTVOICE = 1017;
	public static final int CMD_VERSION = 1100;
	public static final int CMD_AUTH = 1102;
	public static final int CMD_UNLOCK = 31;
	public static final int CMD_ACUNLOCK = 31;
	public static final int CMD_DEVICE = 11;
	public static final int CMD_OPTIONS_RRQ = 11;
	public static final int CMD_ATTLOG_RRQ = 13;
	public static final int CMD_CLEAR_DATA = 14;
	public static final int CMD_CLEAR_ATTLOG = 15;
	public static final int CMD_USER_RRQ = 8;
	public static final int CMD_USERTEMP_RRQ = 9;
	public static final int CMD_GET_FREE_SIZES = 50;

	// Buffer Function Codes (FCT)
	public static final int FCT_ATTLOG = 1;
	public static final int FCT_FINGERTMP = 2;
	public static final int FCT_USER = 5;

	// Modern Encrypted Firmware (ZKCommuCrypto) Commands
	public static final int CMD_CRYPTO_DMC_EXCHANGE = 10063; // 0x274F DMC Public Key exchange
	public static final int CMD_CRYPTO_KEY_EXCHANGE = 10064; // 0x2750 Session key exchange
	public static final int CMD_CRYPTO_CONFIRM_SESSION = 10065; // 0x2751 Session confirmation

	// Data exchange commands
	public static final int CMD_PREPARE_DATA = 1500;
	public static final int CMD_DATA = 1501;
	public static final int CMD_FREE_DATA = 1502;
	public static final int CMD_DATA_WRRQ = 1503;       // Prepare read buffer
	public static final int CMD_READ_BUFFER = 1504;     // Read chunk from buffer

	// Protocol Acknowledgment and Status codes
	public static final int CMD_ACK_OK = 2000;          // Operation success
	public static final int CMD_ACK_ERROR = 2001;       // Operation failed
	public static final int CMD_ACK_DATA = 2002;        // Data response
	public static final int CMD_ACK_RETRY = 2003;
	public static final int CMD_ACK_REPEAT = 2004;
	public static final int CMD_ACK_UNAUTH = 2005;      // Unauthorized (legacy challenge)
	public static final int CMD_ACK_AUTH_LOCK = 2032;   // 0x07F0 Modern firmware unauthorized session lock
	public static final int CMD_ACK_CHALLENGE_6001 = 6001; // 0x1771 Modern firmware auth challenge / legacy rejected
	public static final int CMD_ACK_UNKNOWN = 65535;

	// Header and buffer dimensions
	public static final int ZK_HEADER_SIZE = 8;
	public static final int TCP_HEADER_SIZE = 8;
	public static final int DEFAULT_BUFFER_CHUNK_SIZE = 16 * 1024; // 16KB per buffer chunk
}
