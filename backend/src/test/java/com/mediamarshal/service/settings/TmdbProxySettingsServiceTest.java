package com.mediamarshal.service.settings;

import com.mediamarshal.model.dto.EffectiveSettingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TmdbProxySettingsServiceTest {

    private final SettingsService settingsService = mock(SettingsService.class);
    private final TmdbProxySettingsService service = new TmdbProxySettingsService(settingsService);

    @BeforeEach
    void setUp() {
        when(settingsService.getDatabaseValue(anyString())).thenReturn(Optional.empty());
        when(settingsService.getEffectiveValue(anyString(), anyString()))
                .thenAnswer(invocation -> new SettingsService.EffectiveValue(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        "DEFAULT",
                        false
                ));
    }

    @Test
    void databaseDisabledForcesDirectConnection() {
        when(settingsService.getDatabaseValue(TmdbProxySettingsService.KEY_ENABLED))
                .thenReturn(Optional.of("false"));
        when(settingsService.getEffectiveValue(TmdbProxySettingsService.KEY_HTTP_URL, ""))
                .thenReturn(new SettingsService.EffectiveValue(
                        TmdbProxySettingsService.KEY_HTTP_URL,
                        "http://env-proxy:7890",
                        "ENVIRONMENT",
                        false
                ));

        TmdbProxySettingsService.TmdbProxyConfig config = service.resolve();

        assertThat(config.enabled()).isFalse();
        assertThat(config.httpUrl()).isBlank();
    }

    @Test
    void databaseHttpUrlWithoutEnabledInfersProxyEnabled() {
        when(settingsService.getDatabaseValue(TmdbProxySettingsService.KEY_HTTP_URL))
                .thenReturn(Optional.of("http://127.0.0.1:7890"));

        TmdbProxySettingsService.TmdbProxyConfig config = service.resolve();
        List<EffectiveSettingResponse> effective = service.getEffectiveSettings(List.of(
                TmdbProxySettingsService.KEY_ENABLED,
                TmdbProxySettingsService.KEY_HTTP_URL
        ));

        assertThat(config.enabled()).isTrue();
        assertThat(config.host()).isEqualTo("127.0.0.1");
        assertThat(config.port()).isEqualTo(7890);
        assertThat(effective)
                .extracting(EffectiveSettingResponse::key, EffectiveSettingResponse::value, EffectiveSettingResponse::source)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                TmdbProxySettingsService.KEY_ENABLED,
                                "true",
                                "INFERRED_FROM_HTTP_URL"
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                TmdbProxySettingsService.KEY_HTTP_URL,
                                "http://127.0.0.1:7890",
                                "DATABASE"
                        )
                );
    }

    @Test
    void externalHttpUrlWithoutEnabledInfersProxyEnabled() {
        when(settingsService.getEffectiveValue(TmdbProxySettingsService.KEY_HTTP_URL, ""))
                .thenReturn(new SettingsService.EffectiveValue(
                        TmdbProxySettingsService.KEY_HTTP_URL,
                        "http://proxy.local:7890",
                        "ENVIRONMENT",
                        false
                ));

        TmdbProxySettingsService.TmdbProxyConfig config = service.resolve();

        assertThat(config.enabled()).isTrue();
        assertThat(config.host()).isEqualTo("proxy.local");
        assertThat(config.port()).isEqualTo(7890);
    }

    @Test
    void enabledProxyRequiresHttpUrl() {
        when(settingsService.getDatabaseValue(TmdbProxySettingsService.KEY_ENABLED))
                .thenReturn(Optional.of("true"));

        assertThatThrownBy(service::resolve)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须填写代理地址");
    }

    @Test
    void rejectsUnsupportedProxyScheme() {
        when(settingsService.getDatabaseValue(TmdbProxySettingsService.KEY_ENABLED))
                .thenReturn(Optional.of("true"));
        when(settingsService.getDatabaseValue(TmdbProxySettingsService.KEY_HTTP_URL))
                .thenReturn(Optional.of("socks5://127.0.0.1:7890"));

        assertThatThrownBy(service::resolve)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("仅支持 HTTP");
    }

    @Test
    void saveAllowsDisabledProxyWithoutUrl() {
        service.save(false, "");

        verify(settingsService).set(
                TmdbProxySettingsService.KEY_HTTP_URL,
                "",
                "TMDB HTTP proxy URL",
                false
        );
        verify(settingsService).set(
                TmdbProxySettingsService.KEY_ENABLED,
                "false",
                "TMDB HTTP proxy enabled",
                false
        );
    }
}
