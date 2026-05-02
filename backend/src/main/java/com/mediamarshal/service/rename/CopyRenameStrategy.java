package com.mediamarshal.service.rename;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 复制策略。
 *
 * 不使用 REPLACE_EXISTING，目标文件存在时让 Files.copy 抛出异常，遵守 ADR-010。
 */
@Component
public class CopyRenameStrategy implements FileOperationStrategy {

    @Override
    public void execute(Path source, Path target) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.copy(source, target);
    }

    @Override
    public OperationType getType() {
        return OperationType.COPY;
    }
}
