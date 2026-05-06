package com.mediamarshal.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Service;

@Service
public class AppVersionProvider {

    private static final String UNKNOWN_VERSION = "unknown";

    private final String version;

    public AppVersionProvider() {
        this.version = loadVersion();
    }

    public String getVersion() {
        return version;
    }

    private String loadVersion() {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("VERSION")) {
            if (inputStream == null) {
                return UNKNOWN_VERSION;
            }
            String rawVersion = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
            return rawVersion.isEmpty() ? UNKNOWN_VERSION : rawVersion;
        } catch (IOException ex) {
            return UNKNOWN_VERSION;
        }
    }
}
