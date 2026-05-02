package com.mediamarshal.service.rename;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 硬链接策略。
 *
 * 如果源路径和目标路径不在支持硬链接的同一文件系统，创建会失败；不 fallback 到其他策略。
 */
@Component
public class HardLinkRenameStrategy implements FileOperationStrategy {

    @Override
    public void execute(Path source, Path target) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.createLink(target, source);
    }

    @Override
    public OperationType getType() {
        return OperationType.HARD_LINK;
    }
}
