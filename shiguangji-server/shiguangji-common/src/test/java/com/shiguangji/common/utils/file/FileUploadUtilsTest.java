package com.shiguangji.common.utils.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.shiguangji.common.exception.file.InvalidExtensionException;

/**
 * 上传白名单口径单测（issue #67：前端选择器列了 webp，后端白名单必须有它）。
 *
 * <p>只钉「扩展名是否放行」这一件事；走真机 HTTP 的上传链路由各模块冒烟测试覆盖。</p>
 *
 * @author shiguangji
 */
class FileUploadUtilsTest
{
    @Test
    void imageExtensionAcceptsWebp()
    {
        MockMultipartFile webp = new MockMultipartFile("file", "a.webp", "image/webp", new byte[] { 1 });
        assertThatCode(() -> FileUploadUtils.assertAllowed(webp, MimeTypeUtils.IMAGE_EXTENSION))
                .doesNotThrowAnyException();
    }

    @Test
    void defaultAllowedExtensionAcceptsWebp()
    {
        MockMultipartFile webp = new MockMultipartFile("file", "a.webp", "image/webp", new byte[] { 1 });
        assertThatCode(() -> FileUploadUtils.assertAllowed(webp, MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION))
                .doesNotThrowAnyException();
    }

    @Test
    void imageExtensionStillRejectsNonImage()
    {
        MockMultipartFile txt = new MockMultipartFile("file", "a.txt", "text/plain", new byte[] { 1 });
        assertThatThrownBy(() -> FileUploadUtils.assertAllowed(txt, MimeTypeUtils.IMAGE_EXTENSION))
                .isInstanceOf(InvalidExtensionException.InvalidImageExtensionException.class);
    }

    @Test
    void webpContentTypeMapsToWebpExtension()
    {
        assertThat(MimeTypeUtils.getExtension(MimeTypeUtils.IMAGE_WEBP)).isEqualTo("webp");
    }
}
