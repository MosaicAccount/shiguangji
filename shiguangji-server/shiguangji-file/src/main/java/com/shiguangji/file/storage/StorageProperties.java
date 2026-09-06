package com.shiguangji.file.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件存储配置
 *
 * @author shiguangji
 */
@Component
@ConfigurationProperties(prefix = "sgj.storage")
public class StorageProperties
{
    /** 存储类型：local（默认）/ s3 */
    private String type = "local";

    /** S3 兼容存储配置 */
    private final S3 s3 = new S3();

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public S3 getS3()
    {
        return s3;
    }

    public static class S3
    {
        /** S3 兼容服务地址：MinIO 如 http://127.0.0.1:9000；阿里云 OSS 如 https://oss-cn-hangzhou.aliyuncs.com */
        private String endpoint;

        /** 区域，如阿里云 OSS 的 oss-cn-hangzhou */
        private String region = "us-east-1";

        /** 存储桶名 */
        private String bucket = "shiguangji";

        /** 访问密钥 AK */
        private String accessKey;

        /** 访问密钥 SK */
        private String secretKey;

        /** 寻址风格：true=路径风格（MinIO）；阿里云 OSS 仅支持虚拟主机风格，须配置为 false */
        private boolean pathStyleAccess = true;

        public String getEndpoint()
        {
            return endpoint;
        }

        public void setEndpoint(String endpoint)
        {
            this.endpoint = endpoint;
        }

        public String getRegion()
        {
            return region;
        }

        public void setRegion(String region)
        {
            this.region = region;
        }

        public String getBucket()
        {
            return bucket;
        }

        public void setBucket(String bucket)
        {
            this.bucket = bucket;
        }

        public String getAccessKey()
        {
            return accessKey;
        }

        public void setAccessKey(String accessKey)
        {
            this.accessKey = accessKey;
        }

        public String getSecretKey()
        {
            return secretKey;
        }

        public void setSecretKey(String secretKey)
        {
            this.secretKey = secretKey;
        }

        public boolean isPathStyleAccess()
        {
            return pathStyleAccess;
        }

        public void setPathStyleAccess(boolean pathStyleAccess)
        {
            this.pathStyleAccess = pathStyleAccess;
        }
    }
}
