package com.mediamarshal.service.nfo;

import com.mediamarshal.model.dto.MatchResult;
import com.mediamarshal.model.entity.MediaTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
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
 * TODO:
 *  - 实现 generateMovieNfo() 生成电影 NFO
 *  - 实现 generateEpisodeNfo() 生成剧集 NFO
 *  - 下载海报图（poster.jpg / folder.jpg）
 *  - 支持 Plex 的 .nfo 格式差异（如有）
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
        if (task.getMediaType() == MediaTask.MediaType.MOVIE) {
            generateMovieNfo(matchResult, mediaFile);
        } else {
            generateEpisodeNfo(task, matchResult, mediaFile);
        }
    }

    private void generateMovieNfo(MatchResult match, Path mediaFile) throws IOException {
        // TODO: implement movie NFO generation
        // 输出文件：mediaFile 同目录，扩展名改为 .nfo
        throw new UnsupportedOperationException("Movie NFO generation not yet implemented");
    }

    private void generateEpisodeNfo(MediaTask task, MatchResult match, Path mediaFile) throws IOException {
        // TODO: implement episode NFO generation
        throw new UnsupportedOperationException("Episode NFO generation not yet implemented");
    }
}
