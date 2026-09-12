package zkteco;

/**
 * Constants for ZKTeco standalone TCP pull communication on port 4370.
 */
public final class ZkConstants {
	private ZkConstants() {}

	public static final int DEFAULT_PORT = 4370;
	public static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
	public static final int DEFAULT_READ_TIMEOUT_MS = 30_000;
	public static final int DEFAULT_UNLOCK_DELAY_SECONDS = 5;

	public static final int TCP_MAGIC = 0x5050827D;
	public static final int TCP_MAGIC_ALT = 0x5050837C;

	public static final int CMD_CONNECT = 1000;
	public static final int CMD_EXIT = 1001;
	public static final int CMD_ENABLEDEVICE = 1002;
	public static final int CMD_DISABLEDEVICE = 1003;
	public static final int CMD_RESTART = 1004;
	public static final int CMD_POWEROFF = 1005;
	public static final int CMD_SLEEP = 1006;
	public static final int CMD_RESUME = 1007;
	public static final int CMD_TESTVOICE = 1017;
	public static final int CMD_OPTIONS_RRQ = 11;
	public static final int CMD_OPTIONS_WRQ = 12;
	public static final int CMD_ATTLOG_RRQ = 13;
	public static final int CMD_CLEAR_DATA = 14;
	public static final int CMD_CLEAR_ATTLOG = 15;
	public static final int CMD_USER_RRQ = 8;
	public static final int CMD_USERTEMP_RRQ = 9;
	public static final int CMD_UNLOCK = 31;
	public static final int CMD_ACUNLOCK = 31;
	public static final int CMD_GET_FREE_SIZES = 50;
	public static final int CMD_VERSION = 1100;
	public static final int CMD_AUTH = 1102;
	public static final int CMD_AUTH_EXT = 1106;

	public static final int FCT_ATTLOG = 1;
	public static final int FCT_FINGERTMP = 2;
	public static final int FCT_USER = 5;

	public static final int CMD_PREPARE_DATA = 1500;
	public static final int CMD_DATA = 1501;
	public static final int CMD_FREE_DATA = 1502;
	public static final int CMD_DATA_WRRQ = 1503;
	public static final int CMD_READ_BUFFER = 1504;

	public static final int CMD_CRYPTO_DMC_EXCHANGE = 10063;
	public static final int CMD_CRYPTO_KEY_EXCHANGE = 10064;
	public static final int CMD_CRYPTO_CONFIRM_SESSION = 10065;

	public static final int CMD_ACK_OK = 2000;
	public static final int CMD_ACK_ERROR = 2001;
	public static final int CMD_ACK_DATA = 2002;
	public static final int CMD_ACK_RETRY = 2003;
	public static final int CMD_ACK_REPEAT = 2004;
	public static final int CMD_ACK_UNAUTH = 2005;
	public static final int CMD_ACK_AUTH_LOCK = 2032;
	public static final int CMD_ACK_CHALLENGE_6001 = 6001;

	public static final int TCP_HEADER_SIZE = 8;
	public static final int ZK_HEADER_SIZE = 8;
	public static final int DEFAULT_BUFFER_CHUNK_SIZE = 16 * 1024;
	public static final int MAX_FRAME_PAYLOAD_SIZE = 16 * 1024 * 1024;
	public static final int MAX_BULK_TRANSFER_SIZE = 128 * 1024 * 1024;

	/**
	 * Kept for source compatibility with earlier builds. Use
	 * {@link #MAX_FRAME_PAYLOAD_SIZE} for one TCP frame and
	 * {@link #MAX_BULK_TRANSFER_SIZE} for prepared-buffer transfers.
	 */
	@Deprecated
	public static final int MAX_PACKET_SIZE = MAX_FRAME_PAYLOAD_SIZE;
}
