package com.mediamarshal.service.rename;

import com.mediamarshal.model.entity.MediaTask;
import com.mediamarshal.model.exception.MediaTaskFailureException;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplatePathSafetyServiceTest {

    private final TemplatePathSafetyService service = new TemplatePathSafetyService();

    @Test
    void resolvesRelativePathInsideTargetDir() {
        Path targetRoot = Path.of("build", "media-library").toAbsolutePath().normalize();
        Path target = service.resolveSafeTargetPath(targetRoot.toString(), "Movie/Movie.mkv");

        assertThat(target).isEqualTo(targetRoot.resolve("Movie").resolve("Movie.mkv").normalize());
    }

    @Test
    void rejectsParentTraversalSegments() {
        assertThatThrownBy(() -> service.resolveSafeTargetPath("/media/library", "../outside.mkv"))
                .isInstanceOf(MediaTaskFailureException.class)
                .extracting("errorCode")
                .isEqualTo(MediaTask.TaskErrorCode.UNSAFE_TARGET_PATH);
    }

    @Test
    void rejectsWindowsDrivePath() {
        TemplatePathSafetyResult result = service.validateForPreview("/media/library", "D:\\Movies\\Movie.mkv");

        assertThat(result.safe()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("Windows"));
    }

    @Test
    void rejectsUncPath() {
        TemplatePathSafetyResult result = service.validateForPreview("/media/library", "\\\\server\\share\\Movie.mkv");

        assertThat(result.safe()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("UNC"));
    }
}
