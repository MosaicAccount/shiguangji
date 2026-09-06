package com.shiguangji.file.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.NoSuchFileException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * S3 协议存储实现（sgj.storage.type=s3）
 *
 * 同一实现通过 endpoint/寻址风格配置兼容 MinIO（路径风格）与
 * 阿里云 OSS（仅支持虚拟主机风格）等 S3 兼容服务。
 *
 * @author shiguangji
 */
public class S3FileStorage extends AbstractFileStorage
{
    private static final Logger log = LoggerFactory.getLogger(S3FileStorage.class);

    /** OSS 等 S3 兼容服务在未指定区域时可用的默认区域 */
    private static final String DEFAULT_REGION = "us-east-1";

    private final S3Client client;

    private final String bucket;

    public S3FileStorage(StorageProperties.S3 props)
    {
        if (props.getEndpoint() == null || props.getEndpoint().isBlank())
        {
            throw new IllegalArgumentException("sgj.storage.s3.endpoint 未配置");
        }
        this.bucket = props.getBucket();
        this.client = S3Client.builder()
                .endpointOverride(URI.create(props.getEndpoint()))
                .region(Region.of(props.getRegion() == null || props.getRegion().isBlank() ? DEFAULT_REGION : props.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(props.isPathStyleAccess())
                        .build())
                .httpClient(UrlConnectionHttpClient.create())
                .build();
        log.info("S3文件存储已启用：endpoint={}, bucket={}, pathStyle={}", props.getEndpoint(), bucket, props.isPathStyleAccess());
    }

    @Override
    public String type()
    {
        return "s3";
    }

    @Override
    protected void doStore(String objectKey, InputStream in, long size, String contentType) throws IOException
    {
        try
        {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .build();
            client.putObject(request, RequestBody.fromInputStream(in, size));
        }
        catch (SdkException e)
        {
            throw new IOException("上传到S3存储失败：" + e.getMessage(), e);
        }
    }

    @Override
    protected InputStream doGetContent(String objectKey) throws IOException
    {
        try
        {
            GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(objectKey).build();
            return client.getObject(request, ResponseTransformer.toInputStream());
        }
        catch (NoSuchKeyException e)
        {
            throw new NoSuchFileException(objectKey);
        }
        catch (SdkException e)
        {
            throw new IOException("从S3存储读取失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String objectKey) throws IOException
    {
        try
        {
            DeleteObjectRequest request = DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build();
            client.deleteObject(request);
        }
        catch (SdkException e)
        {
            throw new IOException("从S3存储删除失败：" + e.getMessage(), e);
        }
    }
}
