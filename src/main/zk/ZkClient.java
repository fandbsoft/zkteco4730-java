package main.zk;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * Lớp tương thích ngược (Backward Compatible Wrapper) kế thừa từ {@link ZkAttendanceLog}.
 * Giữ nguyên các hàm cũ để các ứng dụng đã viết bằng ZkClient vẫn tiếp tục hoạt động trơn tru.
 */
public class ZkClient extends ZkAttendanceLog {

	public ZkClient(String ip, int port, int password, int machineNumber) {
		super(ip, port, password, machineNumber);
	}

	public ZkClient(String ip, int port, int password) {
		super(ip, port, password);
	}

	public ZkClient(String ip, int password) {
		super(ip, password);
	}

	public ZkClient(String ip) {
		super(ip);
	}

	public ZkClient(String ip, int port, int password, int... machineNumbers) {
		super(ip, port, password, (machineNumbers != null && machineNumbers.length > 0) ? machineNumbers[0] : 1);
	}

	public synchronized List<ZkAttendanceLog> readAttendanceLogs() throws IOException {
		return getAllLog();
	}

	public synchronized void streamAttendanceLogs(Consumer<ZkAttendanceLog> consumer) throws IOException {
		streamAllLog(consumer);
	}
}
