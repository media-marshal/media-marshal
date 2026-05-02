package com.mediamarshal.service.rename;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 符号链接策略。
 *
 * 目标路径指向源文件绝对路径；如果运行环境不允许创建 symlink，直接失败。
 */
@Component
public class SymbolicLinkRenameStrategy implements FileOperationStrategy {

    @Override
    public void execute(Path source, Path target) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.createSymbolicLink(target, source.toAbsolutePath().normalize());
    }

    @Override
    public OperationType getType() {
        return OperationType.SYMBOLIC_LINK;
    }
}
