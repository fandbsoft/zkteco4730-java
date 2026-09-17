package main;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import zkteco.ZKTeco4370_ZkClient;

public class SyncDeviceTimeExample {
    public static void main(String[] args) {
        String ip = "192.168.1.190";
        int port = 4370;
        int password = 111111;

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        System.out.println("================================================================================");
        System.out.printf("  VI DU DONG BO THOI GIAN CHO MAY CHAM CONG: %s:%d%n", ip, port);
        System.out.println("================================================================================");

        try (ZKTeco4370_ZkClient zk = new ZKTeco4370_ZkClient(ip, port, password)) {
            // 1. Ket noi
            zk.connect();
            System.out.println("-> Ket noi thanh cong toi may: " + zk.getProtocolName());

            // 2. Doc gio va UTC hien tai cua thiet bi truoc khi dong bo
            LocalDateTime before = zk.getDeviceTime();
            System.out.println("\n[1] Thoi gian tren mat may cham cong HIEN TAI : " + before.format(fmt));
            System.out.println("    UTC Offset thiet bi hien tai             : " + zk.getUTC());
            System.out.println("    Thoi gian thuc te cua may tinh PC        : " + LocalDateTime.now().format(fmt));

            // 3. Goi lenh dong bo ca thoi gian va UTC xuong may cham cong
            System.out.println("\n[2] Dang gui lenh dong bo ca thoi gian va UTC xuong may cham cong (syncTime)...");
            boolean success = zk.syncTime(); // Tu dong dong bo ca LocalDateTime.now() va ZoneId/UTC offset

            if (success) {
                System.out.println("-> Ket qua dong bo: THANH CONG [PASS]");
            } else {
                System.out.println("-> Ket qua dong bo: THAT BAI [FAIL]");
            }

            // 4. Doc lai gio va UTC cua thiet bi sau khi dong bo de kiem chung
            LocalDateTime after = zk.getDeviceTime();
            System.out.println("\n[3] Thoi gian tren mat may cham cong SAU DONG BO: " + after.format(fmt));
            System.out.println("    UTC Offset thiet bi sau dong bo          : " + zk.getUTC());

        } catch (Exception e) {
            System.err.println("Loi: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n================================================================================");
        System.out.println(" HOAN TAT VI DU DONG BO THOI GIAN");
        System.out.println("================================================================================");
    }
}