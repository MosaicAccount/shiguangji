package com.shiguangji.file.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.shiguangji.file.storage.FileStorageService;
import com.shiguangji.file.storage.LocalFileStorage;
import com.shiguangji.file.storage.S3FileStorage;
import com.shiguangji.file.storage.StorageProperties;

/**
 * 文件存储策略装配，由 sgj.storage.type 选择（local=默认本地磁盘，s3=S3兼容存储）
 *
 * @author shiguangji
 */
@Configuration
public class FileStorageConfig
{
    @Bean
    @ConditionalOnProperty(name = "sgj.storage.type", havingValue = "local", matchIfMissing = true)
    public FileStorageService localFileStorage()
    {
        return new LocalFileStorage();
    }

    @Bean
    @ConditionalOnProperty(name = "sgj.storage.type", havingValue = "s3")
    public FileStorageService s3FileStorage(StorageProperties properties)
    {
        return new S3FileStorage(properties.getS3());
    }
}
