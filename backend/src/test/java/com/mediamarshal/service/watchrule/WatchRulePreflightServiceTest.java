package com.mediamarshal.service.watchrule;

import com.mediamarshal.controller.WatchRuleController;
import com.mediamarshal.service.rename.FileOperationStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WatchRulePreflightServiceTest {

    @TempDir
    Path tempDir;

    private final WatchRulePreflightService service = new WatchRulePreflightService();

    @Test
    void validateFailsWhenSourceDirectoryDoesNotExist() {
        WatchRuleController.RuleRequest request = request(FileOperationStrategy.OperationType.COPY);
        request.setSourceDir(tempDir.resolve("missing").toString());

        var result = service.validate(request);

        assertThat(result.valid()).isFalse();
        assertThat(result.details()).anyMatch(detail -> detail.contains("源目录不存在"));
    }

    @Test
    void validateCopySucceedsForReadableSourceAndWritableTarget() {
        WatchRuleController.RuleRequest request = request(FileOperationStrategy.OperationType.COPY);

        var result = service.validate(request);

        assertThat(result.valid()).isTrue();
    }

    private WatchRuleController.RuleRequest request(FileOperationStrategy.OperationType operation) {
        WatchRuleController.RuleRequest request = new WatchRuleController.RuleRequest();
        request.setName("test");
        request.setSourceDir(tempDir.resolve("source").toString());
        request.setTargetDir(tempDir.resolve("target").toString());
        request.setMediaType(com.mediamarshal.model.entity.WatchRule.RuleMediaType.AUTO);
        request.setOperation(operation);
        tempDir.resolve("source").toFile().mkdirs();
        return request;
    }
}
