package main.zk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
public interface ZkProtocolAdapter extends AutoCloseable {

	/**
	 * Thiết lập kết nối socket TCP và hoàn tất chuỗi bắt tay xác thực.
	 *
	 * @throws IOException Nếu kết nối hoặc xác thực thất bại.
	 */
	void connect() throws IOException;

	/**
	 * Đọc toàn bộ dữ liệu chấm công theo cơ chế Streaming (Zero-Memory Accumulation).
	 * Bản ghi sau khi giải mã từ stream nhị phân sẽ được chuyển giao ngay lập tức cho Consumer.
	 *
	 * @param consumer Callback xử lý từng bản ghi chấm công.
	 * @throws IOException Nếu xảy ra lỗi truyền thông.
	 */
	void readAttendanceLogs(Consumer<ZkAttendanceLog> consumer) throws IOException;

	/**
	 * Đọc toàn bộ dữ liệu chấm công và trả về danh sách List.
	 *
	 * @return Danh sách các bản ghi chấm công.
	 * @throws IOException Nếu xảy ra lỗi truyền thông.
	 */
	default List<ZkAttendanceLog> readAttendanceLogs() throws IOException {
		List<ZkAttendanceLog> logs = new ArrayList<>();
		readAttendanceLogs(logs::add);
		return logs;
	}

	/**
	 * Tạm thời vô hiệu hóa bàn phím và màn hình thiết bị để đảm bảo tính toàn vẹn phiên truyền dữ liệu.
	 *
	 * @throws IOException Nếu lệnh bị lỗi.
	 */
	void disableDevice() throws IOException;

	/**
	 * Mở khóa lại bàn phím và màn hình thiết bị.
	 *
	 * @throws IOException Nếu lệnh bị lỗi.
	 */
	void enableDevice() throws IOException;

	/**
	 * Trả về tên định danh của giao thức đang hoạt động.
	 */
	String getProtocolName();

	/**
	 * Kiểm tra xem kết nối Socket hiện hành có đang mở và sẵn sàng hay không.
	 */
	boolean isConnected();

	/**
	 * Mở cửa rơ-le qua Pure Socket (CMD_UNLOCK = 31).
	 */
	default boolean unlockDoor(int delaySeconds) throws IOException {
		return false;
	}

	/**
	 * Đọc thông tin người dùng từ thiết bị qua Pure Socket.
	 */
	default void readUsers(Consumer<ZkUserInfo> consumer) throws IOException {}

	/**
	 * Đọc cấu hình/thông số phần cứng thiết bị qua Pure Socket (CMD_OPTIONS_RRQ = 11).
	 */
	default String getDeviceOption(String key) throws IOException {
		return "";
	}

	/**
	 * Đọc dung lượng và bộ đếm thiết bị (users, fingers, records, faces) qua Pure Socket (CMD_GET_FREE_SIZES = 50).
	 * @return int[] chứa { users, fingers, records, faces }
	 */
	default int[] readSizes() throws IOException {
		return new int[] { 0, 0, 0, 0 };
	}

	/**
	 * Ngắt kết nối một cách an toàn và giải phóng triệt để tài nguyên Socket, Stream I/O.
	 */
	@Override
	void close();
}
