package com.mediamarshal.service.watchrule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamarshal.model.dto.WatchRuleImportPackage;
import com.mediamarshal.model.dto.WatchRuleImportPreviewItem.Status;
import com.mediamarshal.model.dto.WatchRuleImportRequest;
import com.mediamarshal.model.dto.WatchRuleImportRule;
import com.mediamarshal.model.dto.WatchRuleValidationResult;
import com.mediamarshal.model.entity.WatchRule;
import com.mediamarshal.repository.WatchRuleRepository;
import com.mediamarshal.service.AppVersionProvider;
import com.mediamarshal.service.rename.FileOperationStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WatchRuleImportExportServiceTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WatchRuleRepository watchRuleRepository = mock(WatchRuleRepository.class);
    private final WatchRulePreflightService watchRulePreflightService = mock(WatchRulePreflightService.class);
    private final AppVersionProvider appVersionProvider = mock(AppVersionProvider.class);
    private final WatchRuleImportExportService service = new WatchRuleImportExportService(
            watchRuleRepository,
            watchRulePreflightService,
            appVersionProvider
    );

    @BeforeEach
    void setUp() {
        when(appVersionProvider.getVersion()).thenReturn("v0.2.9");
        when(watchRulePreflightService.validate(any())).thenReturn(WatchRuleValidationResult.ok());
    }

    @Test
    void exportRulesReturnsCleanWatchRulePackage() throws Exception {
        WatchRule existing = existingRule(source("movies"), target("movies"), FileOperationStrategy.OperationType.COPY);
        existing.setId(42L);
        existing.setName("Movie Rule");
        existing.setEnabled(true);
        when(watchRuleRepository.findAll()).thenReturn(List.of(existing));

        WatchRuleImportPackage exported = service.exportRules();

        assertThat(exported.getKind()).isEqualTo(WatchRuleImportExportService.PACKAGE_KIND);
        assertThat(exported.getSchemaVersion()).isEqualTo(1);
        assertThat(exported.getAppVersion()).isEqualTo("v0.2.9");
        assertThat(exported.getRules()).hasSize(1);
        assertThat(exported.getRules().getFirst().getSourceDir()).isEqualTo(existing.getSourceDir());

        String json = objectMapper.writeValueAsString(exported);
        assertThat(json).doesNotContain("id", "userId", "createdAt", "updatedAt");
    }

    @Test
    void previewSkipsExistingDuplicateWithoutChangingEnabledState() throws Exception {
        WatchRule existing = existingRule(source("movies"), target("movies"), FileOperationStrategy.OperationType.COPY);
        existing.setName("Existing");
        existing.setEnabled(true);
        when(watchRuleRepository.findAll()).thenReturn(List.of(existing));

        WatchRuleImportRule imported = importRule("Imported Name", existing.getSourceDir() + "/", existing.getTargetDir(), "COPY");
        imported.setEnabled(false);

        var preview = service.preview(request(packageOf(imported), false));

        assertThat(preview.isHasBlockingIssues()).isFalse();
        assertThat(preview.getSummary().getSkipped()).isEqualTo(1);
        assertThat(preview.getItems().getFirst().getStatus()).isEqualTo(Status.SKIPPED_DUPLICATE);
    }

    @Test
    void importDefaultsNewRulesToDisabled() throws Exception {
        when(watchRuleRepository.findAll()).thenReturn(List.of());
        AtomicLong id = new AtomicLong(1);
        when(watchRuleRepository.save(any())).thenAnswer(invocation -> {
            WatchRule rule = invocation.getArgument(0);
            rule.setId(id.getAndIncrement());
            return rule;
        });

        WatchRuleImportRule imported = importRule("New", source("movies"), target("movies"), "COPY");
        imported.setEnabled(true);

        var result = service.importRules(request(packageOf(imported), false));

        assertThat(result.getCreatedCount()).isEqualTo(1);
        assertThat(result.getCreatedRules().getFirst().getEnabled()).isFalse();
        verify(watchRulePreflightService, never()).validate(any());
    }

    @Test
    void previewRejectsSameSourceWithDifferentBehavior() throws Exception {
        WatchRule existing = existingRule(source("movies"), target("movies"), FileOperationStrategy.OperationType.COPY);
        when(watchRuleRepository.findAll()).thenReturn(List.of(existing));

        WatchRuleImportRule imported = importRule("Conflict", existing.getSourceDir(), existing.getTargetDir(), "MOVE");

        var preview = service.preview(request(packageOf(imported), false));

        assertThat(preview.isHasBlockingIssues()).isTrue();
        assertThat(preview.getSummary().getConflicts()).isEqualTo(1);
        assertThat(preview.getItems().getFirst().getMessage()).contains("源目录");
    }

    @Test
    void previewRejectsEnabledParentChildSourceWhenPreservingEnabledState() throws Exception {
        WatchRule existing = existingRule(source("media"), target("library"), FileOperationStrategy.OperationType.COPY);
        existing.setEnabled(true);
        when(watchRuleRepository.findAll()).thenReturn(List.of(existing));

        WatchRuleImportRule imported = importRule("Nested", source("media/incoming"), target("nested"), "COPY");
        imported.setEnabled(true);

        var preview = service.preview(request(packageOf(imported), true));

        assertThat(preview.isHasBlockingIssues()).isTrue();
        assertThat(preview.getItems().getFirst().getStatus()).isEqualTo(Status.CONFLICT);
        assertThat(preview.getItems().getFirst().getMessage()).contains("父子");
    }

    @Test
    void previewSkipsDuplicateInsideImportPackage() throws Exception {
        when(watchRuleRepository.findAll()).thenReturn(List.of());
        WatchRuleImportRule first = importRule("First", source("movies"), target("movies"), "COPY");
        WatchRuleImportRule second = importRule("Second", source("movies/"), target("movies"), "copy");

        var preview = service.preview(request(packageOf(first, second), false));

        assertThat(preview.isHasBlockingIssues()).isFalse();
        assertThat(preview.getSummary().getImportable()).isEqualTo(1);
        assertThat(preview.getSummary().getSkipped()).isEqualTo(1);
        assertThat(preview.getItems()).extracting("status")
                .containsExactly(Status.READY, Status.SKIPPED_DUPLICATE);
    }

    @Test
    void previewRejectsUnknownEnumValueAsInvalidItem() throws Exception {
        when(watchRuleRepository.findAll()).thenReturn(List.of());
        WatchRuleImportRule imported = importRule("Bad", source("movies"), target("movies"), "COPY");
        imported.setMediaType("ANIME");

        var preview = service.preview(request(packageOf(imported), false));

        assertThat(preview.isHasBlockingIssues()).isTrue();
        assertThat(preview.getItems().getFirst().getStatus()).isEqualTo(Status.INVALID);
        assertThat(preview.getItems().getFirst().getMessage()).contains("mediaType");
    }

    private WatchRuleImportRequest request(WatchRuleImportPackage importPackage, boolean preserveEnabledState) {
        WatchRuleImportRequest request = new WatchRuleImportRequest();
        request.setImportPackage(objectMapper.valueToTree(importPackage));
        request.setPreserveEnabledState(preserveEnabledState);
        return request;
    }

    private WatchRuleImportPackage packageOf(WatchRuleImportRule... rules) {
        return WatchRuleImportPackage.builder()
                .kind(WatchRuleImportExportService.PACKAGE_KIND)
                .schemaVersion(WatchRuleImportExportService.SUPPORTED_SCHEMA_VERSION)
                .appVersion("v0.2.9")
                .exportedAt("2026-06-13T12:00:00+08:00")
                .rules(List.of(rules))
                .build();
    }

    private WatchRule existingRule(Path sourceDir, Path targetDir, FileOperationStrategy.OperationType operation) {
        WatchRule rule = new WatchRule();
        rule.setName("Existing");
        rule.setSourceDir(sourceDir.toString());
        rule.setTargetDir(targetDir.toString());
        rule.setMediaType(WatchRule.RuleMediaType.AUTO);
        rule.setMoviePathTemplate("{title} ({year})/{title} ({year}){ext}");
        rule.setTvPathTemplate("{title} ({year})/S{season:02d}/{title} - S{season:02d}E{episode:02d}{ext}");
        rule.setOperation(operation);
        rule.setEnabled(false);
        rule.setMoveAssociatedFiles(true);
        rule.setCleanupEmptyDirs(false);
        rule.setGenerateNfo(false);
        rule.setIgnoredFilePatterns(null);
        rule.setDiscoveryMode(WatchRule.DiscoveryMode.HYBRID);
        rule.setScanIntervalMinutes(10);
        rule.setWebhookEnabled(false);
        return rule;
    }

    private WatchRuleImportRule importRule(String name, Path sourceDir, Path targetDir, String operation) {
        return importRule(name, sourceDir.toString(), targetDir.toString(), operation);
    }

    private WatchRuleImportRule importRule(String name, String sourceDir, String targetDir, String operation) {
        return WatchRuleImportRule.builder()
                .name(name)
                .sourceDir(sourceDir)
                .targetDir(targetDir)
                .mediaType("AUTO")
                .moviePathTemplate("{title} ({year})/{title} ({year}){ext}")
                .tvPathTemplate("{title} ({year})/S{season:02d}/{title} - S{season:02d}E{episode:02d}{ext}")
                .operation(operation)
                .enabled(true)
                .moveAssociatedFiles(true)
                .cleanupEmptyDirs(false)
                .generateNfo(false)
                .ignoredFilePatterns(null)
                .discoveryMode("HYBRID")
                .scanIntervalMinutes(10)
                .webhookEnabled(false)
                .build();
    }

    private Path source(String name) throws Exception {
        Path path = tempDir.resolve(name);
        Files.createDirectories(path);
        return path;
    }

    private Path target(String name) throws Exception {
        Path path = tempDir.resolve("target").resolve(name);
        Files.createDirectories(path);
        return path;
    }
}
