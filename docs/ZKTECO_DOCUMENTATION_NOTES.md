# Ghi nhớ tài liệu ZKTeco cổng 4370

Ngày kiểm tra: 2026-09-13. Người dùng yêu cầu đọc và ghi nhớ các tài liệu đã liệt kê trong cuộc trao đổi.

Đây là ghi chú tham chiếu lưu trong dự án, không phải bản sao tài liệu hãng hoặc cam kết đã đọc toàn bộ mọi trang. Đã đọc các phần giao tiếp truy cập được; một số nguồn chỉ còn trích đoạn được lập chỉ mục. Khi triển khai chi tiết cần mở lại đúng mục và đối chiếu model/firmware.

## Nguồn và mức độ đã đọc

1. [Technical Manual for Lower Communication Protocol V1.0, June 2008, ZKEMSDK 6.1](https://www.scribd.com/document/75892043/TechnicalX86-ZKEMSDK-6-1): đọc bản văn giao thức, bảng lệnh, cấu trúc dữ liệu và sự kiện; tài liệu mang tên ZKSoftware, lưu trên Scribd.
   - UDP server cổng 4370. Header gồm bốn trường 16 bit: Command, CheckSum, SessionID, ReplyID; tiếp theo là payload.
   - SessionID do máy cấp. Ví dụ kết nối trong bản này bắt đầu ReplyID=1.
   - Có CMD_CONNECT=1000, CMD_EXIT=1001, CMD_AUTH=1102; truyền khối bằng PREPARE_DATA/DATA/FREE_DATA (1500/1501/1502).
   - Dữ liệu user/log phụ thuộc firmware; có log thường và mở rộng, timestamp mã hóa riêng. Sự kiện thời gian thực dùng trường SessionID cho loại sự kiện.
   - Công thức thời gian và mô tả checksum trong bản trích xuất có thể bị lỗi trình bày; không chép máy móc thành mã nguồn.

2. [Standalone SDK Development Manual V2.1 Rev.A.2](https://studylib.net/doc/25367562/zkteco-standalone-sdk-development-manual-v2.1-a.2-en): trang mở được nhưng không cung cấp toàn văn qua bộ đọc. Trích đoạn tìm kiếm trước đó xác nhận Connect_Net dùng cổng mặc định 4370. Chưa đọc toàn bộ API.

3. [ZK Communication SDK Manual, file V6.12](https://ptc2003.hostilla.pl/biofinger.pl/download/ZKTeco/Manuals/zkemsdk_manual_V6.12.pdf): liên kết trả 404. Không coi đây là tài liệu đã đọc.

4. [ZK Spain Communication SDK Manual V6.12.2](https://www.scribd.com/document/366039673/Zk-Spain-Sdk-Manual-v6-12-2): đọc mục kết nối 4.3.4 và các mục lân cận, chưa đọc toàn bộ 6.817 dòng bản văn.
   - Connect_Net nhận địa chỉ IP và port, thường là 4370; trả boolean.
   - Connect_Com dành cho RS232/RS485. Disconnect giải phóng kết nối; EnableDevice điều khiển trạng thái ngoại vi.
   - Đây là tài liệu API SDK, không đồng nghĩa với đặc tả mọi gói tin TCP.

5. [PullSDK Interfaces User Guide V2.0, January 2012](https://www.scribd.com/document/442591279/PullSDK-User-Guide-EN-V2-0-201201-1-doc): mở trực tiếp lỗi; đọc được phần giới thiệu và Connect/Disconnect/SetDeviceParam qua [bản lưu khác](https://www.scribd.com/document/252696187/PullSDK-User-Guide-En-V2-0-201201).
   - API hướng đến bảng điều khiển truy cập, hỗ trợ TCP/IP và RS485; không mặc định đồng nhất với giao thức terminal standalone.
   - Connect nhận chuỗi protocol, ipaddress, port, timeout, passwd; TCP mặc định 4370, timeout tính bằng ms, tên tham số phân biệt hoa thường.
   - Connect trả handle hoặc 0 khi thất bại. Không diễn giải giá trị 0 của mọi hàm giống nhau: SetDeviceParam trả 0 khi thành công.

6. [F18 User Manual V1.1, February 2023](https://zkteco.me/download-file/2687): đọc chương 7, trang in 30-32. TCP COMM Port mặc định 4370 và đổi được; Comm Key mặc định 0, dài 1-6 chữ số. Device ID liên quan kết nối serial. Cloud Server/ADMS có cấu hình địa chỉ và port riêng.

7. [F35 User Manual](https://www.zkteco.jo/download-file/2073): mở trực tiếp lỗi; mới có trích đoạn cổng TCP mặc định 4370 từ lượt tìm trước. Chưa đọc toàn văn.

8. [G4 Pro Series V1.1 tại Indonesia](https://www.zkteco.co.id/wp-content/uploads/download-manager-files/G4-Pro-ACC_User-Manual-V1.1_20220331.pdf): mở lỗi. [Bản G4 Pro tại Saudi Arabia](https://zkteco.sa/download-file/2815) truy cập được; đọc phần Comm. connection settings. Mật khẩu PC dùng cho Offline/PULL SDK. Không mặc định hai bản có cùng số trang/phiên bản.

9. [SenseFace 4 Series V1.0, 2024](https://www.zkteco.co.id/wp-content/uploads/download-manager-files/ZK_SenseFace-4-Series_UM_EN_v1.0_20240327.pdf): mở trực tiếp lỗi; đọc trích đoạn PC Connection được lập chỉ mục: TCP 4370 đổi được, Comm Key và HTTPS có mục riêng. Có [bản 2025](https://zkteco.sa/download-file/2972), không thay thế âm thầm bản 2024. Mục HTTPS không đủ để suy ra đặc tả mã hóa socket 4370.

10. [2.4-inch Visible Light Terminal](https://www.zkteco.me/download-file/2750): hiện trả 404; đọc trích đoạn Ethernet được lập chỉ mục, trang in 57, cổng 4370 đổi được. [Bản lưu khác tìm được](https://zkteco.pro/files/docs/zksfv3l/ZK_2_4inchVisibleLightTerminal_user_manual_1_0_en.pdf), chưa đọc toàn văn.

11. [adrobinoga/zk-protocol, terminal.md](https://github.com/adrobinoga/zk-protocol/blob/master/sections/terminal.md): nguồn cộng đồng; đã đọc kết nối/ngắt kết nối và trạng thái máy. Ví dụ TCP bắt đầu ReplyID=0 và gửi SDKBuild=1 sau kết nối. Phần Comm Key thừa nhận chưa biết hàm xác thực; không dùng nhận định này như giới hạn hiện tại của toàn hệ sinh thái. Mô tả status 92 byte chỉ là cấu trúc được nguồn này quan sát.

12. [nurkarim/zkteco-sdk-php README](https://github.com/nurkarim/zkteco-sdk-php/blob/master/README.md): đọc README; thư viện cộng đồng UDP 4370, có user/log và ví dụ vòng đời kết nối. Chưa review mã nguồn. Nhận định port không đổi được không áp dụng chung cho các máy mới có menu đổi TCP port.

## Cách sử dụng trong dự án

- Tra ghi chú này trước khi dùng tài liệu ngoài cho thay đổi giao tiếp 4370.
- Phân biệt UDP legacy, TCP terminal, API PULL controller và secure firmware; số cổng giống nhau không chứng minh giao thức giống nhau.
- Đối chiếu với `ZKTECO_PROTOCOL_SPECIFICATION.md` và `FIELD_NOTES_SENSEFACE_2A.md` cùng thư mục. Đây là tài liệu nội bộ dự án, không phải nguồn hãng đã được xác minh trong lượt đọc này.
- Các chi tiết challenge 6001, DMC, RSA/AES trong ghi chú SenseFace hiện có chưa được các manual ngoài ở trên chứng minh. Lượt này không kết nối thiết bị hay xác nhận lại kết quả thử nghiệm cũ.
- Khác biệt ReplyID khởi đầu 0/1 cần xác minh bằng giao thức và phản hồi thực tế; không chọn một giá trị làm chuẩn cho mọi thiết bị.
- Chưa hoàn tất đọc toàn văn các tài liệu SDK dài và các PDF lỗi. Khi cần một hàm/cấu trúc cụ thể, lấy bản đầy đủ hoặc mục tương ứng trước khi triển khai.
