package com.mediamarshal.service.rename;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 移动+改名策略（MVP 默认实现）
 *
 * 使用 ATOMIC_MOVE 保证跨文件系统时的原子性；
 * 若目标目录不存在，自动递归创建。
 */
@Component
public class MoveRenameStrategy implements FileOperationStrategy {

    @Override
    public void execute(Path source, Path target) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // ATOMIC_MOVE 跨磁盘时可能不支持，降级为普通移动
            Files.move(source, target);
        }
    }

    @Override
    public OperationType getType() {
        return OperationType.MOVE;
    }
}
