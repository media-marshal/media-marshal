package com.mediamarshal.service.discovery;

import com.mediamarshal.model.entity.WatchRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoveryIgnoreServiceTest {

    @TempDir
    Path tempDir;

    private final DiscoveryIgnoreService service = new DiscoveryIgnoreService();

    @Test
    void prunesSampleDirectoriesAtAnyDepthCaseInsensitivelyWithDefaults() throws Exception {
        Path nestedSample = Files.createDirectories(tempDir.resolve("release").resolve("SAMPLE"));
        Path nestedSamples = Files.createDirectories(tempDir.resolve("other").resolve("Samples"));
        WatchRule rule = ruleWithPatterns(null);

        assertThat(service.isIgnored(nestedSample, rule)).isTrue();
        assertThat(service.isIgnored(nestedSample.resolve("clip.mkv"), rule)).isTrue();
        assertThat(service.isIgnored(nestedSamples, rule)).isTrue();
    }

    @Test
    void doesNotForceSampleRulesWhenIgnoredPatternsAreEmpty() throws Exception {
        Path sample = Files.createDirectories(tempDir.resolve("Sample"));
        WatchRule rule = ruleWithPatterns(List.of());

        assertThat(service.isIgnored(sample, rule)).isFalse();
        assertThat(service.usesSystemDefaults(rule)).isFalse();
    }

    @Test
    void customRulesOnlyApplyPatternsExplicitlyProvidedByUser() throws Exception {
        Path sample = Files.createDirectories(tempDir.resolve("Sample"));
        Path cache = Files.createDirectories(tempDir.resolve("cache"));
        WatchRule rule = ruleWithPatterns(List.of("cache/"));

        assertThat(service.isIgnored(sample, rule)).isFalse();
        assertThat(service.isIgnored(cache, rule)).isTrue();
    }

    private WatchRule ruleWithPatterns(List<String> patterns) {
        WatchRule rule = new WatchRule();
        rule.setSourceDir(tempDir.toString());
        rule.setIgnoredFilePatterns(patterns);
        return rule;
    }
}
