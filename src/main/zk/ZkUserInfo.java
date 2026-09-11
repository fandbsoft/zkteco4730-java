package main.zk;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Class ZkUserInfo trong package main.zk:
 * Vừa là đối tượng người dùng (Data Model) chứa userId, name, createdAt,
 * vừa là Client truy vấn danh sách người dùng từ máy chấm công để map vào MyBM.
 */
public class ZkUserInfo implements AutoCloseable {

	// =========================================================================
	// PHẦN A: CÁC TRƯỜNG DỮ LIỆU CỦA NGƯỜI DÙNG (DATA MODEL)
	// =========================================================================
	private final String userId;
	private final String name;
	private final LocalDateTime createdAt;

	// =========================================================================
	// PHẦN B: CÁC THÔNG SỐ KẾT NỐI CLIENT
	// =========================================================================
	private final String ip;
	private final int port;
	private final int password;
	private final int machineNumber;

	// CONSTRUCTOR CHO ĐỐI TƯỢNG NGƯỜI DÙNG (DATA MODEL)
	public ZkUserInfo(String userId, String name, LocalDateTime createdAt) {
		this.userId = (userId != null) ? userId.trim() : "";
		this.name = (name != null) ? name.trim() : "";
		this.createdAt = createdAt;

		this.ip = null;
		this.port = 0;
		this.password = 0;
		this.machineNumber = 0;
	}

	// CONSTRUCTOR CHO CLIENT KẾT NỐI
	public ZkUserInfo(String ip, int port, int password, int machineNumber) {
		if (ip == null || ip.isBlank()) {
			throw new IllegalArgumentException("Địa chỉ IP không được để trống");
		}
		this.ip = ip.trim();
		this.port = (port > 0) ? port : ZkConstants.DEFAULT_PORT;
		this.password = password;
		this.machineNumber = (machineNumber > 0) ? machineNumber : 1;

		this.userId = null;
		this.name = null;
		this.createdAt = null;
	}

	public ZkUserInfo(String ip, int port, int password) {
		this(ip, port, password, 1);
	}

	public ZkUserInfo(String ip, int password) {
		this(ip, ZkConstants.DEFAULT_PORT, password, 1);
	}

	public ZkUserInfo(String ip) {
		this(ip, ZkConstants.DEFAULT_PORT, 0, 1);
	}

	/**
	 * Lấy toàn bộ danh sách người dùng từ máy chấm công.
	 *
	 * @return Danh sách các đối tượng {@link ZkUserInfo}.
	 * @throws IOException Nếu xảy ra lỗi kết nối.
	 */
	public synchronized List<ZkUserInfo> getAllUser() throws IOException {
		List<ZkUserInfo> list = new ArrayList<>();

		// 1. Thử đọc qua Pure Java TCP Socket
		try (ZkLegacySocketAdapter legacy = new ZkLegacySocketAdapter(ip, port, password)) {
			legacy.connect();
			legacy.readUsers(list::add);
			return list;
		} catch (ZkAuthChallengeException challenge) {
			try (ZkSmartAdapter smart = new ZkSmartAdapter(ip, port, password)) {
				smart.connect();
				smart.readUsers(list::add);
				return list;
			}
		}
	}

	/**
	 * Tìm thông tin người dùng theo userId.
	 *
	 * @param userId Mã ID người dùng cần tìm.
	 * @return Đối tượng {@link ZkUserInfo} nếu tìm thấy, hoặc null nếu không tồn tại.
	 * @throws IOException Nếu xảy ra lỗi kết nối.
	 */
	public synchronized ZkUserInfo getUser(String userId) throws IOException {
		if (userId == null || userId.isBlank()) {
			return null;
		}
		String targetId = userId.trim();
		for (ZkUserInfo user : getAllUser()) {
			if (targetId.equalsIgnoreCase(user.getUserId())) {
				return user;
			}
		}
		return null;
	}

	/**
	 * Hàm static tiện ích lấy nhanh danh sách người dùng một dòng.
	 */
	public static List<ZkUserInfo> quickGetAllUser(String ip, int port, int password) {
		try (ZkUserInfo client = new ZkUserInfo(ip, port, password)) {
			return client.getAllUser();
		} catch (Exception e) {
			return List.of();
		}
	}

	// =========================================================================
	// GETTERS CHO DATA MODEL
	// =========================================================================

	public String getUserId() {
		return userId;
	}

	public String getName() {
		return name;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public long getCreatedAtEpochMilli() {
		return (createdAt != null)
				? createdAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
				: 0L;
	}

	public long getCreatedAtEpochSecond() {
		return (createdAt != null)
				? createdAt.atZone(ZoneId.systemDefault()).toEpochSecond()
				: 0L;
	}

	@Override
	public String toString() {
		if (ip != null) {
			return "ZkUserInfo[client=" + ip + ":" + port + "]";
		}
		return "ZkUserInfo{" +
				"userId='" + userId + '\'' +
				", name='" + name + '\'' +
				", createdAt=" + createdAt +
				'}';
	}

	@Override
	public synchronized void close() {
		// Cleanup resources
	}
}
