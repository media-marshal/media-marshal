"""
guessit sidecar - FastAPI 封装

提供 HTTP 接口供 Java 主服务调用，将视频文件名解析为结构化 JSON。

接口：
  GET /parse?filename={filename}   解析单个文件名
  GET /health                      健康检查

响应示例（电影）：
  {
    "title": "The Dark Knight",
    "year": 2008,
    "type": "movie",
    "screen_size": "1080p",
    "video_codec": "H.264"
  }

响应示例（剧集）：
  {
    "title": "Breaking Bad",
    "season": 3,
    "episode": 7,
    "type": "episode"
  }
"""

from fastapi import FastAPI, HTTPException, Query
from guessit import guessit
import logging
from pathlib import Path

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

APP_VERSION_FALLBACK = "0.2.4"


def load_app_version() -> str:
    version_file = Path(__file__).with_name("VERSION")
    if not version_file.exists():
        return APP_VERSION_FALLBACK
    version = version_file.read_text(encoding="utf-8").strip()
    return version.removeprefix("v") or APP_VERSION_FALLBACK


app = FastAPI(
    title="Media Marshal Parser",
    description="guessit HTTP sidecar for Media Marshal",
    version=load_app_version(),
)


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/parse")
def parse(filename: str = Query(..., description="视频文件名（不含路径）")):
    """
    解析视频文件名，返回 guessit 识别结果。

    guessit 会自动识别以下信息：
      - title：标题
      - year：年份
      - season/episode：季集号
      - type："movie" 或 "episode"
      - screen_size、video_codec、release_group 等技术参数
    """
    if not filename:
        raise HTTPException(status_code=400, detail="filename is required")

    logger.info(f"Parsing: {filename}")
    try:
        result = guessit(filename)
        # guessit 返回 MatchesDict，转为普通 dict 以便 JSON 序列化
        return dict(result)
    except Exception as e:
        logger.error(f"Parse failed for '{filename}': {e}")
        raise HTTPException(status_code=500, detail=str(e))


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
