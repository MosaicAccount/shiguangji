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
    
    public static final String[] IMAGE_EXTENSION = { "bmp", "gif", "jpg", "jpeg", "png" };

    /**
     * 笔记正文插图允许的图片扩展名（0.0.2 FR-002-9）
     *
     * <p>在 {@link #IMAGE_EXTENSION} 基础上放行 webp——它已是网页图片与截图的常见格式。
     * 注意 {@code FileUploadUtils.assertAllowed} 用引用相等挑「图片格式」专用异常文案，
     * 所以传本常量会走通用异常（提示里仍会列出允许的后缀）；要统一文案应扩展 IMAGE_EXTENSION 本身，
     * 那会同时放宽封面 / 头像，属另一个 issue（#67）。</p>
     */
    public static final String[] NOTE_IMAGE_EXTENSION = { "bmp", "gif", "jpg", "jpeg", "png", "webp" };

    public static final String[] FLASH_EXTENSION = { "swf", "flv" };

    public static final String[] MEDIA_EXTENSION = { "swf", "flv", "mp3", "wav", "wma", "wmv", "mid", "avi", "mpg",
            "asf", "rm", "rmvb" };

    public static final String[] VIDEO_EXTENSION = { "mp4", "avi", "rmvb" };

    public static final String[] DEFAULT_ALLOWED_EXTENSION = {
            // 图片
            "bmp", "gif", "jpg", "jpeg", "png",
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
            default:
                return "";
        }
    }
}
