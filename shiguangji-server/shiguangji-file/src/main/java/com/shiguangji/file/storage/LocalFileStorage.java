package com.shiguangji.file.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
    protected void doStore(String objectKey, InputStream in, long size) throws IOException
    {
        Path target = Paths.get(ShiGuangJiConfig.getProfile(), objectKey);
        Files.createDirectories(target.getParent());
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
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
}
