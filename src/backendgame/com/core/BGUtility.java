package backendgame.com.core;

public class BGUtility {
    public static void trace(Object obj) {
        if (obj == null) {
            System.out.println("[TRACE] null");
            return;
        }
        try {
            Class<?> clazz = Class.forName("com.google.gson.GsonBuilder");
            Object builder = clazz.getDeclaredConstructor().newInstance();
            java.lang.reflect.Method pretty = clazz.getMethod("setPrettyPrinting");
            builder = pretty.invoke(builder);
            java.lang.reflect.Method create = clazz.getMethod("create");
            Object gson = create.invoke(builder);
            java.lang.reflect.Method toJson = gson.getClass().getMethod("toJson", Object.class);
            System.out.println("[TRACE] " + toJson.invoke(gson, obj));
        } catch (Throwable t) {
            System.out.println("[TRACE] " + obj);
        }
    }
}