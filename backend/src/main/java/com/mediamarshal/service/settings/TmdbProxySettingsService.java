package com.mediamarshal.service.settings;

import com.mediamarshal.model.dto.EffectiveSettingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TmdbProxySettingsService {

    public static final String KEY_ENABLED = "tmdb.proxy.enabled";
    public static final String KEY_HTTP_URL = "tmdb.proxy.http-url";

    private static final String DESCRIPTION_ENABLED = "TMDB HTTP proxy enabled";
    private static final String DESCRIPTION_HTTP_URL = "TMDB HTTP proxy URL";

    private final SettingsService settingsService;

    public boolean supports(String key) {
        return KEY_ENABLED.equals(key) || KEY_HTTP_URL.equals(key);
    }

    public TmdbProxyConfig resolve() {
        RawProxySettings raw = resolveRaw();
        boolean enabled = resolveEnabled(raw);
        if (!enabled) {
            return TmdbProxyConfig.disabled(raw.httpUrl());
        }

        URI uri = parseAndValidate(raw.httpUrl());
        return TmdbProxyConfig.enabled(raw.httpUrl().trim(), uri.getHost(), uri.getPort());
    }

    public List<EffectiveSettingResponse> getEffectiveSettings(List<String> keys) {
        RawProxySettings raw = resolveRaw();
        boolean enabled = resolveEnabledLenient(raw);
        return keys.stream()
                .filter(this::supports)
                .map(key -> toEffectiveSetting(key, raw, enabled))
                .toList();
    }

    public void save(boolean enabled, String httpUrl) {
        String normalizedUrl = normalize(httpUrl);
        if (enabled) {
            parseAndValidate(normalizedUrl);
        }

        settingsService.set(KEY_HTTP_URL, normalizedUrl, DESCRIPTION_HTTP_URL, false);
        settingsService.set(KEY_ENABLED, String.valueOf(enabled), DESCRIPTION_ENABLED, false);
    }

    private EffectiveSettingResponse toEffectiveSetting(String key, RawProxySettings raw, boolean enabled) {
        if (KEY_ENABLED.equals(key)) {
            String source = raw.enabledRaw().isBlank() && enabled ? "INFERRED_FROM_HTTP_URL" : raw.enabledSource();
            return new EffectiveSettingResponse(key, String.valueOf(enabled), source, raw.overriddenByDatabase());
        }
        return new EffectiveSettingResponse(key, raw.httpUrl(), raw.httpUrlSource(), raw.overriddenByDatabase());
    }

    private RawProxySettings resolveRaw() {
        Optional<String> databaseEnabled = settingsService.getDatabaseValue(KEY_ENABLED);
        Optional<String> databaseHttpUrl = settingsService.getDatabaseValue(KEY_HTTP_URL);

        if (databaseEnabled.isPresent()) {
            return new RawProxySettings(
                    normalize(databaseEnabled.get()),
                    normalize(databaseHttpUrl.orElse("")),
                    "DATABASE",
                    databaseHttpUrl.isPresent() ? "DATABASE" : "DEFAULT",
                    true
            );
        }

        if (databaseHttpUrl.isPresent() && !databaseHttpUrl.get().isBlank()) {
            return new RawProxySettings(
                    "",
                    normalize(databaseHttpUrl.get()),
                    "DEFAULT",
                    "DATABASE",
                    true
            );
        }

        SettingsService.EffectiveValue enabled = settingsService.getEffectiveValue(KEY_ENABLED, "");
        SettingsService.EffectiveValue httpUrl = settingsService.getEffectiveValue(KEY_HTTP_URL, "");
        return new RawProxySettings(
                normalize(enabled.value()),
                normalize(httpUrl.value()),
                enabled.source(),
                httpUrl.source(),
                false
        );
    }

    private boolean resolveEnabled(RawProxySettings raw) {
        if (raw.enabledRaw().isBlank()) {
            return !raw.httpUrl().isBlank();
        }
        return parseBoolean(raw.enabledRaw());
    }

    private boolean resolveEnabledLenient(RawProxySettings raw) {
        if (raw.enabledRaw().isBlank()) {
            return !raw.httpUrl().isBlank();
        }
        return "true".equalsIgnoreCase(raw.enabledRaw().trim());
    }

    private boolean parseBoolean(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized)) {
            return false;
        }
        throw new IllegalStateException("TMDB 代理开关配置无效，应为 true 或 false");
    }

    private URI parseAndValidate(String httpUrl) {
        String normalizedUrl = normalize(httpUrl);
        if (normalizedUrl.isBlank()) {
            throw new IllegalArgumentException("启用 TMDB 代理时必须填写代理地址");
        }

        URI uri;
        try {
            uri = new URI(normalizedUrl);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("TMDB 代理地址格式不正确，请使用 http://host:port", e);
        }

        if (!"http".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("TMDB 代理当前仅支持 HTTP 代理地址，请使用 http://host:port");
        }
        if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
            throw new IllegalArgumentException("TMDB 代理当前不支持认证信息，请使用不带用户名密码的 HTTP 代理地址");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("TMDB 代理地址缺少主机名，请使用 http://host:port");
        }
        if (uri.getPort() <= 0) {
            throw new IllegalArgumentException("TMDB 代理地址需要包含端口，例如 http://127.0.0.1:7890");
        }
        return uri;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public record TmdbProxyConfig(boolean enabled, String httpUrl, String host, int port) {
        static TmdbProxyConfig disabled(String httpUrl) {
            return new TmdbProxyConfig(false, httpUrl, null, -1);
        }

        static TmdbProxyConfig enabled(String httpUrl, String host, int port) {
            return new TmdbProxyConfig(true, httpUrl, host, port);
        }
    }

    private record RawProxySettings(
            String enabledRaw,
            String httpUrl,
            String enabledSource,
            String httpUrlSource,
            boolean overriddenByDatabase
    ) {
    }
}
