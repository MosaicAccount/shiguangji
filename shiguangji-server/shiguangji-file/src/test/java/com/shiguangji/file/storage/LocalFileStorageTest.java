package com.shiguangji.file.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.shiguangji.common.config.ShiGuangJiConfig;
import com.shiguangji.common.constant.Constants;
import com.shiguangji.common.utils.file.MimeTypeUtils;

/**
 * 本地存储策略单元测试（不依赖 Spring 容器与数据库）
 */
class LocalFileStorageTest
{
    @TempDir
    Path tempDir;

    private final LocalFileStorage storage = new LocalFileStorage();

    @BeforeEach
    void setUp()
    {
        new ShiGuangJiConfig().setProfile(tempDir.toString());
    }

    @Test
    void uploadStoresFileAndReturnsProfileKey() throws Exception
    {
        MultipartFile file = new MockMultipartFile("file", "封面.png", "image/png", "data".getBytes(StandardCharsets.UTF_8));

        String key = storage.upload("upload", file, MimeTypeUtils.IMAGE_EXTENSION, true);

        assertTrue(key.startsWith("/profile/upload/"), "返回路径应带 /profile 前缀：" + key);
        assertTrue(key.endsWith(".png"), "应保留原始扩展名：" + key);
        try (InputStream in = storage.getContent(key.substring(Constants.RESOURCE_PREFIX.length() + 1)))
        {
            assertEquals("data", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void uploadRejectsDisallowedExtension()
    {
        MultipartFile file = new MockMultipartFile("file", "shell.exe", "application/octet-stream", "data".getBytes(StandardCharsets.UTF_8));

        assertThrows(Exception.class, () -> storage.upload("upload", file, MimeTypeUtils.IMAGE_EXTENSION, false));
    }

    @Test
    void getContentRejectsPathTraversal()
    {
        assertThrows(Exception.class, () -> storage.getContent("../secret.txt"));
        assertThrows(Exception.class, () -> storage.getContent("upload/../../secret.txt"));
    }

    @Test
    void deleteRemovesFileAndIgnoresMissing() throws Exception
    {
        MultipartFile file = new MockMultipartFile("file", "a.png", "image/png", "data".getBytes(StandardCharsets.UTF_8));
        String key = storage.upload("upload", file, MimeTypeUtils.IMAGE_EXTENSION, false);

        storage.delete(key.substring(Constants.RESOURCE_PREFIX.length() + 1));
        assertThrows(Exception.class, () -> storage.getContent(key.substring(Constants.RESOURCE_PREFIX.length() + 1)));
        // 再删一次不抛异常
        storage.delete(key.substring(Constants.RESOURCE_PREFIX.length() + 1));
    }

    @Test
    void listReturnsStoredObjectsKeyedWithoutProfilePrefix() throws Exception
    {
        MultipartFile file = new MockMultipartFile("file", "封面.png", "image/png", "data".getBytes(StandardCharsets.UTF_8));
        String path = storage.upload("cover", file, MimeTypeUtils.IMAGE_EXTENSION, true);
        // list 的 key 必须与 delete/getContent 的参数一致，否则清理任务删不到文件
        String objectKey = path.substring(Constants.RESOURCE_PREFIX.length() + 1);

        Map<String, Instant> objects = storage.list("cover");

        assertEquals(Set.of(objectKey), objects.keySet());
        assertNotNull(objects.get(objectKey), "应带最后修改时间供保护期判断");
    }

    @Test
    void listOnlyReturnsRequestedCategory() throws Exception
    {
        MultipartFile file = new MockMultipartFile("file", "a.png", "image/png", "data".getBytes(StandardCharsets.UTF_8));
        storage.upload("upload", file, MimeTypeUtils.IMAGE_EXTENSION, false);

        assertTrue(storage.list("cover").isEmpty(), "不应把其他归类的文件算进本归类");
    }

    @Test
    void listReturnsEmptyForMissingCategory() throws Exception
    {
        assertTrue(storage.list("avatar").isEmpty(), "归类目录不存在时应返回空集合而非报错");
    }

    @Test
    void listRejectsPathTraversalCategory()
    {
        assertThrows(Exception.class, () -> storage.list("../"));
    }
}
