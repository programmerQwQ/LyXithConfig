package org.lyxith.lyxithconfig.auto;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.lyxith.lyxithconfig.LyXithConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@SuppressWarnings("unused")
public class LyXithConfigManager {
    private static final Object LOCK = new Object();
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    private static final String JSON_SUFFIX = ".json";
    private static final String TMP_SUFFIX = ".tmp";

    public static <T> T load(Class<T> clazz) {
        var configPath = getConfigPath(clazz);
        var configExists = Files.exists(configPath);

        try {
            if (configExists) {
                var configJson = Files.readString(configPath);
                return GSON.fromJson(configJson, clazz);
            } else {
                var constructor = clazz.getDeclaredConstructor();
                var instance = constructor.newInstance();
                save(instance);
                return constructor.newInstance();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config", e);
        }
    }

    public static <T> void save(T config) {
        var configClazz = config.getClass();
        var configPath = getConfigPath(configClazz);
        var parentPath = configPath.getParent();

        synchronized (LOCK) {
            try {
                Files.createDirectories(parentPath);

                var configJson = GSON.toJson(config);
                var configTempFile = Files.createTempFile(
                        configPath.getParent(),
                        configPath.getFileName().toString(),
                        TMP_SUFFIX
                );

                Files.writeString(configTempFile, configJson);
                Files.move(
                        configTempFile,
                        configPath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (IOException e) {
                throw new RuntimeException("Failed to save config", e);
            }
        }
    }

    private static <T> Path getConfigPath(Class<T> clazz) {
        var configAnnotation = clazz.getDeclaredAnnotation(Config.class);
        var className = clazz.getName();

        if (configAnnotation == null) {
            throw new RuntimeException("Missing @Config annotation on class: " + className);
        }

        var modId = configAnnotation.modId();
        var configName = configAnnotation.configPath().endsWith(JSON_SUFFIX) ?
                configAnnotation.configPath() :
                configAnnotation.configPath() + JSON_SUFFIX;

        return LyXithConfig.configPath
                .resolve(modId)
                .resolve(configName);
    }
}
