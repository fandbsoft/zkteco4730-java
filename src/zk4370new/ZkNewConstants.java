package zk4370new;

/**
 * Hằng số giao thức chuyên dụng cho các dòng máy ZKTeco Firmware mới (Modern Firmware / SenseFace / Linux OS).
 * Bao gồm các mã lệnh, mã phản hồi bảo mật và thông số khung truyền trên cổng 4370.
 */
public final class ZkNewConstants {
	private ZkNewConstants() {}

	// Cấu hình mạng mặc định
	public static final int DEFAULT_PORT = 4370;
	public static final int DEFAULT_CONNECT_TIMEOUT_MS = 5_000;
	public static final int DEFAULT_READ_TIMEOUT_MS = 10_000;
	public static final int DEFAULT_UNLOCK_DELAY_SECONDS = 5;

	// Magic Header cho TCP Framing (Little-Endian & Wire Bytes)
	public static final int TCP_MAGIC = 0x5050827D;     // 50 50 82 7D (ASCII "PP\x82}") -> Little Endian: 0x7D825050
	public static final int TCP_MAGIC_ALT = 0x5050837C; // 50 50 83 7C -> Little Endian: 0x7C835050
	public static final byte[] TCP_MAGIC_BYTES = new byte[] { 0x50, 0x50, (byte) 0x82, 0x7D };

	// Các mã lệnh (Command Opcodes)
	public static final int CMD_CONNECT = 1000;
	public static final int CMD_EXIT = 1001;
	public static final int CMD_ENABLEDEVICE = 1002;
	public static final int CMD_DISABLEDEVICE = 1003;
	public static final int CMD_RESTART = 1004;
	public static final int CMD_POWEROFF = 1005;
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
	public static final int FCT_USER = 5;

	// Lệnh đệm dữ liệu (Big Data Buffering)
	public static final int CMD_PREPARE_DATA = 1500;
	public static final int CMD_DATA = 1501;
	public static final int CMD_FREE_DATA = 1502;
	public static final int CMD_DATA_WRRQ = 1503;
	public static final int CMD_READ_BUFFER = 1504;

	// Lệnh đàm phán mã hóa phiên mới (ZKCommuCrypto)
	public static final int CMD_CRYPTO_DMC_EXCHANGE = 10063; // 0x274F Trao đổi public key
	public static final int CMD_CRYPTO_KEY_EXCHANGE = 10064; // 0x2750 Trao đổi session key
	public static final int CMD_CRYPTO_CONFIRM_SESSION = 10065; // 0x2751 Xác nhận phiên mã hóa

	// Mã phản hồi và Trạng thái (Acknowledgment Codes)
	public static final int CMD_ACK_OK = 2000;          // 0x07D0 Thành công
	public static final int CMD_ACK_ERROR = 2001;       // 0x07D1 Thất bại / Lỗi
	public static final int CMD_ACK_DATA = 2002;        // 0x07D2 Trả về dữ liệu
	public static final int CMD_ACK_RETRY = 2003;       // 0x07D3 Thử lại
	public static final int CMD_ACK_REPEAT = 2004;      // 0x07D4 Gửi lại
	public static final int CMD_ACK_UNAUTH = 2005;      // 0x07D5 Chưa xác thực Comm Key (Legacy)
	public static final int CMD_ACK_AUTH_LOCK = 2032;   // 0x07F0 Khóa xác thực trên Firmware mới (Modern Lock)
	public static final int CMD_ACK_CHALLENGE_6001 = 6001; // 0x1771 Thách thức bảo mật Firmware mới
	public static final int CMD_ACK_UNKNOWN = 65535;

	// Kích thước Header & Buffer
	public static final int TCP_HEADER_SIZE = 8;
	public static final int ZK_HEADER_SIZE = 8;
	public static final int MAX_PACKET_SIZE = 16 * 1024 * 1024;
	public static final int DEFAULT_BUFFER_CHUNK_SIZE = 16 * 1024; // 16KB chunk
}
