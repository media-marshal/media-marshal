package com.mediamarshal.service.rename;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 文件操作策略接口（Strategy Pattern）
 *
 * v0.2.x 实现：MoveRenameStrategy、CopyRenameStrategy、
 * HardLinkRenameStrategy、SymbolicLinkRenameStrategy。
 *
 * 扩展方式：
 *   1. 新建实现类，标注 @Component
 *   2. 在 FileOperationStrategyFactory 中自动注册（已通过 Map 注入实现）
 *   3. 用户在 WatchRule.operation 中选择对应操作类型即可切换
 */
public interface FileOperationStrategy {

    /**
     * 执行文件操作：将 source 操作到 target 位置（含新文件名）
     *
     * @param source 源文件路径
     * @param target 目标文件路径（目录不存在时应自动创建）
     * @throws IOException 操作失败时抛出
     */
    void execute(Path source, Path target) throws IOException;

    /**
     * 返回此策略对应的操作类型标识
     */
    OperationType getType();

    enum OperationType {
        MOVE,
        COPY,
        HARD_LINK,
        SYMBOLIC_LINK
    }
}
