package com.shiguangji.web.service.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.shiguangji.common.exception.ServiceException;
import com.shiguangji.file.storage.ByteArrayMultipartFile;
import com.shiguangji.file.storage.FileStorageService;

/**
 * 外链转存服务单测（issue #68，测试用例 T-54）：内容校验矩阵与落盘参数。
 *
 * <p>抓取器与存储都是桩，不依赖 Spring 容器与数据库；端点层面的鉴权 / 台账 / 限流
 * 由 {@code ExternalImageApiSmokeTest} 覆盖。</p>
 */
class ExternalImageTransferServiceTest
{
    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3, 4 };

    private static final byte[] JPG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4, 5, 6, 7, 8 };

    private static final byte[] GIF = { 'G', 'I', 'F', '8', '9', 'a', 1, 2, 3, 4, 5, 6 };

    private static final byte[] BMP = { 'B', 'M', 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };

    private static final byte[] WEBP = { 'R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P' };

    private ExternalImageFetcher fetcher;

    private FileStorageService storage;

    private ExternalImageTransferService service;

    @BeforeEach
    void setUp()
    {
        fetcher = mock(ExternalImageFetcher.class);
        storage = mock(FileStorageService.class);
        service = new ExternalImageTransferService();
        ReflectionTestUtils.setField(service, "fetcher", fetcher);
        ReflectionTestUtils.setField(service, "fileStorageService", storage);
    }

    @Test
    void sniffRecognizesAllFiveFormats()
    {
        assertThat(ExternalImageTransferService.sniffImageExtension(PNG, "image/png")).isEqualTo("png");
        assertThat(ExternalImageTransferService.sniffImageExtension(JPG, "image/jpg")).isEqualTo("jpg");
        assertThat(ExternalImageTransferService.sniffImageExtension(GIF, "image/gif")).isEqualTo("gif");
        assertThat(ExternalImageTransferService.sniffImageExtension(BMP, "image/bmp")).isEqualTo("bmp");
        assertThat(ExternalImageTransferService.sniffImageExtension(WEBP, "image/webp")).isEqualTo("webp");
    }

    @Test
    void nonImageContentTypeRejected()
    {
        assertThatThrownBy(() -> ExternalImageTransferService.sniffImageExtension(PNG, "text/html"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不是图片");
    }

    @Test
    void unknownMagicRejected()
    {
        byte[] junk = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13 };
        assertThatThrownBy(() -> ExternalImageTransferService.sniffImageExtension(junk, "image/png"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("图片内容校验失败");
        assertThatThrownBy(() -> ExternalImageTransferService.sniffImageExtension(new byte[3], "image/png"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("图片内容校验失败");
    }

    @Test
    void magicWinsOverDeclaredType()
    {
        // 声明 png 实为 webp 字节：以魔数为准，落盘后缀即真实格式
        assertThat(ExternalImageTransferService.sniffImageExtension(WEBP, "image/png")).isEqualTo("webp");
    }

    @Test
    void transferUploadsUuidNamedUnderUploadCategory() throws IOException
    {
        when(fetcher.fetch("https://example.com/a.png"))
                .thenReturn(new ExternalImageFetcher.FetchedImage(PNG, "image/png"));
        when(storage.upload(eq("upload"), any(), any(), eq(true)))
                .thenReturn("/profile/upload/2026/10/13/abc.png");

        ExternalImageTransferService.TransferResult result = service.transfer("https://example.com/a.png");

        assertThat(result.path()).isEqualTo("/profile/upload/2026/10/13/abc.png");
        assertThat(result.file().getOriginalFilename()).isEqualTo("external.png");
    }

    @Test
    void fetchFailurePropagatesReadableMessage()
    {
        when(fetcher.fetch(any())).thenThrow(new ServiceException("内网地址不允许抓取"));
        assertThatThrownBy(() -> service.transfer("https://example.com/a.png"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("内网地址不允许抓取");
    }

    @Test
    void blankOrOverlongUrlRejectedBeforeFetch()
    {
        assertThatThrownBy(() -> service.transfer(""))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能为空");
        assertThatCode(() -> service.transfer("https://example.com/" + "a".repeat(2048)))
                .isInstanceOf(ServiceException.class);
    }
}
