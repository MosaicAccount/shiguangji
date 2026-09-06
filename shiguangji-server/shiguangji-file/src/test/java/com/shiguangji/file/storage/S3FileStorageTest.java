package com.shiguangji.file.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * S3 存储策略单元测试（不依赖真实 S3 服务）
 */
class S3FileStorageTest
{
    private StorageProperties.S3 props(String endpoint, boolean pathStyle)
    {
        StorageProperties.S3 props = new StorageProperties.S3();
        props.setEndpoint(endpoint);
        props.setRegion("us-east-1");
        props.setBucket("shiguangji-test");
        props.setAccessKey("ak");
        props.setSecretKey("sk");
        props.setPathStyleAccess(pathStyle);
        return props;
    }

    @Test
    void typeIsS3()
    {
        assertEquals("s3", new S3FileStorage(props("http://127.0.0.1:9", true)).type());
    }

    @Test
    void rejectsMissingEndpoint()
    {
        StorageProperties.S3 props = props("", true);
        assertThrows(IllegalArgumentException.class, () -> new S3FileStorage(props));
    }

    @Test
    void uploadFailureWrapsAsIOException()
    {
        // 指向未监听端口，连接立即被拒绝
        S3FileStorage storage = new S3FileStorage(props("http://127.0.0.1:9", true));
        MultipartFile file = new MockMultipartFile("file", "a.png", "image/png", "data".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> storage.upload("upload", file, null, false));
    }
}
