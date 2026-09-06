package com.shiguangji.file.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

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
}
