package backendgame.com.core;

import com.google.gson.GsonBuilder;

public class BGUtility {
    public static void trace(Object obj) {
        System.out.println("[TRACE] " + new GsonBuilder().setPrettyPrinting().create().toJson(obj));
    }
}