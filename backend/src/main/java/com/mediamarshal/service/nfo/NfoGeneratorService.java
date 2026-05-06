package com.mediamarshal.service.nfo;

import com.mediamarshal.model.dto.MatchResult;
import com.mediamarshal.model.entity.MediaAssetType;
import com.mediamarshal.model.entity.MediaTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * NFO 元数据文件生成服务
 *
 * 生成 Kodi/Emby/Jellyfin 通用 XML 格式的 .nfo 文件，
 * 与媒体文件同目录放置，文件名与媒体文件名相同（仅扩展名不同）。
 *
 * NFO 规范参考：
 *  - 电影：https://kodi.wiki/view/NFO_files/Movies
 *  - 剧集：https://kodi.wiki/view/NFO_files/TV_shows
 *
 * v1 先生成最小可用字段，保证 Emby/Jellyfin/Plex 能识别核心元数据。
 * 海报下载、演员、分类等增强信息后续再扩展。
 */
@Slf4j
@Service
public class NfoGeneratorService {

    /**
     * 为完成重命名的媒体文件生成对应的 NFO 文件
     *
     * @param task        已完成的媒体任务
     * @param matchResult TMDB 匹配到的元数据
     * @param mediaFile   重命名后的媒体文件路径
     */
    public void generate(MediaTask task, MatchResult matchResult, Path mediaFile) throws IOException {
        if (MediaTask.MediaType.MOVIE.equals(task.getMediaType())) {
            generateMovieNfo(task, matchResult, mediaFile);
        } else {
            generateEpisodeNfo(task, matchResult, mediaFile);
        }
    }

    private void generateMovieNfo(MediaTask task, MatchResult match, Path mediaFile) throws IOException {
        String xml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <movie>
                  <title>%s</title>
                  <originaltitle>%s</originaltitle>
                  <year>%s</year>
                  <plot>%s</plot>
                  <tmdbid>%s</tmdbid>
                  <thumb>%s</thumb>
                </movie>
                """.formatted(
                escape(match.getTitle()),
                escape(match.getOriginalTitle()),
                match.getYear() != null ? match.getYear() : "",
                escape(match.getOverview()),
                escape(match.getSourceId()),
                escape(match.getPosterUrl())
        );
        writeNfo(task, mediaFile, xml);
    }

    private void generateEpisodeNfo(MediaTask task, MatchResult match, Path mediaFile) throws IOException {
        String xml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <episodedetails>
                  <title>%s</title>
                  <showtitle>%s</showtitle>
                  <season>%s</season>
                  <episode>%s</episode>
                  <plot>%s</plot>
                  <tmdbid>%s</tmdbid>
                  <thumb>%s</thumb>
                </episodedetails>
                """.formatted(
                escape(match.getTitle()),
                escape(match.getTitle()),
                task.getParsedSeason() != null ? task.getParsedSeason() : "",
                task.getParsedEpisode() != null ? task.getParsedEpisode() : "",
                escape(match.getOverview()),
                escape(match.getSourceId()),
                escape(match.getPosterUrl())
        );
        writeNfo(task, mediaFile, xml);
    }

    private void writeNfo(MediaTask task, Path mediaFile, String xml) throws IOException {
        Path nfoFile = MediaAssetType.BLURAY_DIRECTORY.equals(task.getAssetType())
                ? mediaFile.resolve("movie.nfo")
                : replaceExtension(mediaFile, ".nfo");
        Path parent = nfoFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(nfoFile, xml, StandardCharsets.UTF_8);
        log.info("NFO generated: {}", nfoFile);
    }

    private Path replaceExtension(Path file, String newExtension) {
        String filename = file.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        String nfoName = (dot > 0 ? filename.substring(0, dot) : filename) + newExtension;
        return file.resolveSibling(nfoName);
    }

    private String escape(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
