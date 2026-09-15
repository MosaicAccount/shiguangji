package com.shiguangji.file.storage;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import org.springframework.lang.NonNull;
import org.springframework.web.multipart.MultipartFile;

/**
 * 内存字节数组的 MultipartFile 适配器
 *
 * 供服务端代抓的远程文件（如豆瓣封面）复用 FileStorageService.upload 的既有校验与存储链路
 *
 * @author shiguangji
 */
public class ByteArrayMultipartFile implements MultipartFile
{
    private final String name;

    private final String originalFilename;

    private final String contentType;

    private final byte[] content;

    public ByteArrayMultipartFile(String name, String originalFilename, String contentType, byte[] content)
    {
        this.name = name;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.content = content;
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public String getOriginalFilename()
    {
        return originalFilename;
    }

    @Override
    public String getContentType()
    {
        return contentType;
    }

    @Override
    public boolean isEmpty()
    {
        return content.length == 0;
    }

    @Override
    public long getSize()
    {
        return content.length;
    }

    @Override
    public byte[] getBytes()
    {
        return content;
    }

    @Override
    public InputStream getInputStream()
    {
        return new ByteArrayInputStream(content);
    }

    @Override
    public void transferTo(@NonNull File dest) throws IOException
    {
        Files.write(dest.toPath(), content);
    }
}
