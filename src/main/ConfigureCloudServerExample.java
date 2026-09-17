package main;

import zkteco.ZKTeco4370_CloudConfig;
import zkteco.ZKTeco4370_ZkClient;

public class ConfigureCloudServerExample {
    public static void main(String[] args) {
        String ip = "192.168.1.190";
        int port = 4370;
        int password = 111111;

        System.out.println("================================================================================");
        System.out.printf("  VI DU CAU HINH MAY CHU DAM MAY (CLOUD SERVER / ADMS) TREN CONG %d%n", port);
        System.out.println("================================================================================");

        try (ZKTeco4370_ZkClient zk = new ZKTeco4370_ZkClient(ip, port, password)) {
            // 1. Ket noi
            zk.connect();
            System.out.println("-> Ket noi thanh cong toi may: " + zk.getProtocolName());

            // 2. Doc cau hinh Cloud hien tai de hien thi Web qua zk.getCloudConfig()
            ZKTeco4370_CloudConfig currentConfig = zk.getCloudConfig();
            System.out.println("\n[1] Thong tin CloudConfig hien tai tren may (hien thi tren Web):");
            System.out.println("    ipServer       : " + currentConfig.getIpServer());
            System.out.println("    portServer     : " + currentConfig.getPortServer());
            System.out.println("    isHttps        : " + currentConfig.isHttps());
            System.out.println("    isPushEnabled  : " + currentConfig.isPushEnabled());
            System.out.println("    isDomainEnabled: " + currentConfig.isDomainEnabled());

            // 3. Tao doi tuong ZKTeco4370_CloudConfig moi (nhan tu form submit tren Web)
            ZKTeco4370_CloudConfig webSubmitConfig = new ZKTeco4370_CloudConfig(
                    "cloud.mycompany.vn", // ipServer (Domain hoac IP)
                    443,                  // portServer
                    true                  // isHttps (true = HTTPS qua SSL/TLS, false = HTTP)
            );

            System.out.println("\n[2] Dang ghi cau hinh CloudConfig moi xuong may cham cong...");
            boolean success = zk.setCloudConfig(webSubmitConfig);
            System.out.println("-> Ket qua ghi cau hinh: " + (success ? "THANH CONG [PASS]" : "THAT BAI [FAIL]"));

            // 4. Doc lai de kiem chung hien thi tren Web sau khi cap nhat
            ZKTeco4370_CloudConfig updatedConfig = zk.getCloudConfig();
            System.out.println("\n[3] Doc lai cau hinh tren may sau khi ghi (cap nhat len Web):");
            System.out.println("    ipServer       : " + updatedConfig.getIpServer());
            System.out.println("    portServer     : " + updatedConfig.getPortServer());
            System.out.println("    isHttps        : " + updatedConfig.isHttps());
            System.out.println("    isPushEnabled  : " + updatedConfig.isPushEnabled());

            // 5. Khoi phuc lai ve trang thai ban dau (tranh anh huong thiet bi dang chay)
            System.out.println("\n[4] Dang khoi phuc lai thiet bi ve trang thai ban dau...");
            zk.disableCloudServer();
            zk.setDeviceOption("WebServerPort", "8080");
            System.out.println("-> Da khoi phuc trang thai ban dau cho may cham cong.");

        } catch (Exception e) {
            System.err.println("Loi: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n================================================================================");
        System.out.println(" HOAN TAT VI DU CAU HINH MAY CHU DAM MAY");
        System.out.println("================================================================================");
    }
}