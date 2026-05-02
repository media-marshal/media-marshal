package com.mediamarshal.service.rename;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.abort;

class FileOperationStrategyTest {

    @TempDir
    Path tempDir;

    @Test
    void copyKeepsSourceFileAndCreatesTarget() throws Exception {
        Path source = tempDir.resolve("source.mkv");
        Path target = tempDir.resolve("target").resolve("movie.mkv");
        Files.writeString(source, "media");

        new CopyRenameStrategy().execute(source, target);

        assertThat(Files.exists(source)).isTrue();
        assertThat(Files.readString(target)).isEqualTo("media");
    }

    @Test
    void copyDoesNotOverwriteExistingTarget() throws Exception {
        Path source = tempDir.resolve("source.mkv");
        Path target = tempDir.resolve("target.mkv");
        Files.writeString(source, "source");
        Files.writeString(target, "target");

        assertThatThrownBy(() -> new CopyRenameStrategy().execute(source, target))
                .isInstanceOf(Exception.class);
        assertThat(Files.readString(target)).isEqualTo("target");
    }

    @Test
    void hardLinkCreatesSecondDirectoryEntry() throws Exception {
        Path source = tempDir.resolve("source.mkv");
        Path target = tempDir.resolve("target.mkv");
        Files.writeString(source, "media");

        new HardLinkRenameStrategy().execute(source, target);

        assertThat(Files.exists(source)).isTrue();
        assertThat(Files.readString(target)).isEqualTo("media");
        assertThat(Files.isSameFile(source, target)).isTrue();
    }

    @Test
    void symbolicLinkCreatesLinkToSource() throws Exception {
        Path source = tempDir.resolve("source.mkv");
        Path target = tempDir.resolve("target.mkv");
        Files.writeString(source, "media");

        try {
            new SymbolicLinkRenameStrategy().execute(source, target);
        } catch (Exception e) {
            abort("Symbolic links are not available in this environment: " + e.getMessage());
        }

        assertThat(Files.isSymbolicLink(target)).isTrue();
        assertThat(Files.readString(target)).isEqualTo("media");
    }
}
