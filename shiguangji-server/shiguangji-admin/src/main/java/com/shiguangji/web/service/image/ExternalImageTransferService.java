package com.shiguangji.web.service.image;

import java.io.IOException;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.shiguangji.common.exception.ServiceException;
import com.shiguangji.common.utils.StringUtils;
import com.shiguangji.common.utils.file.MimeTypeUtils;
import com.shiguangji.file.storage.ByteArrayMultipartFile;
import com.shiguangji.file.storage.FileStorageService;

/**
 * 外链图片转存服务（0.0.6 FR-006-1，issue #68）
 *
 * <p>职责边界（设计 D-34）：传输安全（协议 / IP 段 / 重定向 / 超时 / 体积）全部在
 * {@link ExternalImageFetcher} 实现内；本服务做落盘前最后一道内容校验
 * （Content-Type + 魔数）与存储、台账由控制器登记。</p>
 *
 * @author shiguangji
 */
@Service
public class ExternalImageTransferService
{
    @Autowired
    private ExternalImageFetcher fetcher;

    @Autowired
    private FileStorageService fileStorageService;

    /** 转存产物：path 回给前端改写引用；file 供控制器登记 sys_file 台账 */
    public record TransferResult(String path, ByteArrayMultipartFile file)
    {
    }

    /**
     * 转存一张外链图片：抓取 → 内容校验 → 落 upload 分类（uuid 命名，与笔记插图同链路）
     *
     * @param url 绝对 http(s) 地址（协议相对 // 由前端补全 https: 后传入）
     * @return 站内 /profile 路径与上传产物
     */
    public TransferResult transfer(String url)
    {
        if (StringUtils.isEmpty(url) || url.length() > 2048)
        {
            throw new ServiceException("图片地址不能为空且不超过 2048 字符");
        }
        ExternalImageFetcher.FetchedImage image = fetcher.fetch(url);
        String ext = sniffImageExtension(image.bytes(), image.contentType());
        ByteArrayMultipartFile file = new ByteArrayMultipartFile(
                "file", "external." + ext, image.contentType(), image.bytes());
        try
        {
            String path = fileStorageService.upload("upload", file, MimeTypeUtils.NOTE_IMAGE_EXTENSION, true);
            return new TransferResult(path, file);
        }
        catch (IOException e)
        {
            throw new ServiceException("转存图片保存失败：" + e.getMessage());
        }
    }

    /**
     * 落盘前最后一道校验：Content-Type 必须 image/*，字节按魔数认出 png/jpg/gif/webp/bmp 之一。
     * 两者都过才返回扩展名；魔数与声明的类型不一致时以魔数为准（存储后缀即真实格式）。
     */
    static String sniffImageExtension(byte[] bytes, String contentType)
    {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (!type.startsWith("image/"))
        {
            throw new ServiceException("对方返回的不是图片（Content-Type: " + contentType + "）");
        }
        if (bytes == null || bytes.length < 12)
        {
            throw new ServiceException("图片内容校验失败：不是受支持的图片格式");
        }
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47)
        {
            return "png";
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF)
        {
            return "jpg";
        }
        if (bytes[0] == 0x47 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x38)
        {
            return "gif";
        }
        if (bytes[0] == 0x42 && bytes[1] == 0x4D)
        {
            return "bmp";
        }
        if (bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46
                && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50)
        {
            return "webp";
        }
        throw new ServiceException("图片内容校验失败：不是受支持的图片格式");
    }
}
