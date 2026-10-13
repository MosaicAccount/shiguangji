package com.shiguangji.common.utils.file;

/**
 * 媒体类型工具类
 * 
 * @author shiguangji
 */
public class MimeTypeUtils
{
    public static final String IMAGE_PNG = "image/png";

    public static final String IMAGE_JPG = "image/jpg";

    public static final String IMAGE_JPEG = "image/jpeg";

    public static final String IMAGE_BMP = "image/bmp";

    public static final String IMAGE_GIF = "image/gif";

    public static final String IMAGE_WEBP = "image/webp";

    /** webp 已是网页图片与截图的常见格式，主流浏览器都能显示（issue #67 起与前端选择器口径一致） */
    public static final String[] IMAGE_EXTENSION = { "bmp", "gif", "jpg", "jpeg", "png", "webp" };

    /**
     * 笔记正文插图允许的图片扩展名（0.0.2 FR-002-9）
     *
     * <p>#67 起 {@link #IMAGE_EXTENSION} 也放行 webp，两个常量内容一致；
     * 保留本常量是因为前端 {@code utils/noteImages.ts} 的 NOTE_IMAGE_EXTENSIONS 按它对齐（改一侧同步另一侧）。</p>
     */
    public static final String[] NOTE_IMAGE_EXTENSION = { "bmp", "gif", "jpg", "jpeg", "png", "webp" };

    public static final String[] FLASH_EXTENSION = { "swf", "flv" };

    public static final String[] MEDIA_EXTENSION = { "swf", "flv", "mp3", "wav", "wma", "wmv", "mid", "avi", "mpg",
            "asf", "rm", "rmvb" };

    public static final String[] VIDEO_EXTENSION = { "mp4", "avi", "rmvb" };

    public static final String[] DEFAULT_ALLOWED_EXTENSION = {
            // 图片（与 IMAGE_EXTENSION 同步放行 webp，相册 / 通用上传与前端选择器口径一致，issue #67）
            "bmp", "gif", "jpg", "jpeg", "png", "webp",
            // word excel powerpoint
            "doc", "docx", "xls", "xlsx", "ppt", "pptx", "html", "htm", "txt",
            // 压缩文件
            "rar", "zip", "gz", "bz2",
            // 视频格式
            "mp4", "avi", "rmvb",
            // pdf
            "pdf" };

    public static String getExtension(String prefix)
    {
        switch (prefix)
        {
            case IMAGE_PNG:
                return "png";
            case IMAGE_JPG:
                return "jpg";
            case IMAGE_JPEG:
                return "jpeg";
            case IMAGE_BMP:
                return "bmp";
            case IMAGE_GIF:
                return "gif";
            case IMAGE_WEBP:
                return "webp";
            default:
                return "";
        }
    }
}
