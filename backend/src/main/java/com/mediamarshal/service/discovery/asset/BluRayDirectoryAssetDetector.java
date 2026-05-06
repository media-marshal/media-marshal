package com.mediamarshal.service.discovery.asset;

import com.mediamarshal.model.entity.MediaAssetType;
import com.mediamarshal.model.entity.WatchRule;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

@Component
public class BluRayDirectoryAssetDetector implements MediaAssetDetector {

    public static final String NON_STANDARD_BLURAY_REASON = "非标解析失败：蓝光原盘缺少影片外层目录";

    @Override
    public Optional<MediaAsset> detect(Path path, WatchRule rule) {
        Path sourceRoot = Path.of(rule.getSourceDir()).toAbsolutePath().normalize();
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(sourceRoot)) {
            return Optional.empty();
        }

        Optional<MediaAsset> naked = detectNakedBdmv(normalized, sourceRoot);
        if (naked.isPresent()) {
            return naked;
        }

        Path current = Files.isDirectory(normalized) ? normalized : normalized.getParent();
        while (current != null && current.startsWith(sourceRoot)) {
            if (isStandardBluRayRoot(current, sourceRoot)) {
                return Optional.of(new MediaAsset(
                        current,
                        MediaAssetType.BLURAY_DIRECTORY,
                        current.getFileName().toString(),
                        true
                ));
            }
            if (current.equals(sourceRoot)) {
                break;
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    @Override
    public int priority() {
        return 100;
    }

    private Optional<MediaAsset> detectNakedBdmv(Path path, Path sourceRoot) {
        Path bdmv = resolveChildIgnoreCase(sourceRoot, "BDMV");
        if (!Files.isDirectory(bdmv) || !path.startsWith(bdmv) || !hasStrongBluRayFeature(sourceRoot)) {
            return Optional.empty();
        }
        return Optional.of(new MediaAsset(
                bdmv,
                MediaAssetType.BLURAY_DIRECTORY,
                bdmv.getFileName().toString(),
                true,
                NON_STANDARD_BLURAY_REASON
        ));
    }

    private boolean isStandardBluRayRoot(Path candidate, Path sourceRoot) {
        if (candidate.equals(sourceRoot) || isName(candidate, "BDMV")) {
            return false;
        }
        Path bdmv = resolveChildIgnoreCase(candidate, "BDMV");
        return Files.isDirectory(bdmv) && hasStrongBluRayFeature(candidate);
    }

    private boolean hasStrongBluRayFeature(Path root) {
        Path bdmv = resolveChildIgnoreCase(root, "BDMV");
        return Files.exists(resolveChildIgnoreCase(bdmv, "index.bdmv"))
                || Files.exists(resolveChildIgnoreCase(bdmv, "MovieObject.bdmv"))
                || hasStreamM2ts(bdmv);
    }

    private boolean hasStreamM2ts(Path bdmv) {
        Path streamDir = resolveChildIgnoreCase(bdmv, "STREAM");
        if (!Files.isDirectory(streamDir)) {
            return false;
        }
        try (Stream<Path> stream = Files.list(streamDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString().toLowerCase())
                    .anyMatch(name -> name.endsWith(".m2ts"));
        } catch (IOException e) {
            return false;
        }
    }

    private Path resolveChildIgnoreCase(Path dir, String childName) {
        if (!Files.isDirectory(dir)) {
            return dir.resolve(childName);
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(child -> isName(child, childName))
                    .findFirst()
                    .orElse(dir.resolve(childName));
        } catch (IOException e) {
            return dir.resolve(childName);
        }
    }

    private boolean isName(Path path, String expected) {
        Path filename = path.getFileName();
        return filename != null && filename.toString().equalsIgnoreCase(expected);
    }
}
