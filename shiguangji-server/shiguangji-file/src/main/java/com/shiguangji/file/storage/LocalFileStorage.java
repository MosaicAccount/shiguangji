package com.shiguangji.file.storage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.shiguangji.common.config.ShiGuangJiConfig;

/**
 * 本地磁盘存储实现（sgj.storage.type=local，默认）
 *
 * @author shiguangji
 */
public class LocalFileStorage extends AbstractFileStorage
{
    @Override
    public String type()
    {
        return "local";
    }

    @Override
    protected void doStore(String objectKey, InputStream in, long size, String contentType) throws IOException
    {
        Path target = Paths.get(ShiGuangJiConfig.getProfile(), objectKey);
        Files.createDirectories(target.getParent());
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void delete(String objectKey) throws IOException
    {
        Path root = Paths.get(ShiGuangJiConfig.getProfile()).normalize();
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root))
        {
            throw new IOException("非法的文件key：" + objectKey);
        }
        Files.deleteIfExists(target);
    }

    @Override
    protected InputStream doGetContent(String objectKey) throws IOException
    {
        Path root = Paths.get(ShiGuangJiConfig.getProfile()).normalize();
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root))
        {
            throw new IOException("非法的文件key：" + objectKey);
        }
        return Files.newInputStream(target);
    }

    @Override
    public Map<String, Instant> list(String category) throws IOException
    {
        Path root = Paths.get(ShiGuangJiConfig.getProfile()).normalize();
        Path dir = root.resolve(category).normalize();
        if (!dir.startsWith(root))
        {
            throw new IOException("非法的文件归类：" + category);
        }
        Map<String, Instant> objects = new LinkedHashMap<>();
        if (!Files.isDirectory(dir))
        {
            return objects;
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(dir))
        {
            files = walk.filter(Files::isRegularFile).collect(Collectors.toList());
        }
        for (Path file : files)
        {
            String relative = dir.relativize(file).toString().replace(File.separatorChar, '/');
            objects.put(category + "/" + relative, Files.getLastModifiedTime(file).toInstant());
        }
        return objects;
    }
}
