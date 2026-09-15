package com.shiguangji.file.storage;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储策略接口
 *
 * 业务表统一保存 upload() 返回的 /profile 前缀相对路径，与具体存储后端解耦；
 * 后端选择由配置 sgj.storage.type 决定（local=本地磁盘，s3=S3兼容存储）。
 *
 * @author shiguangji
 */
public interface FileStorageService
{
    /** 存储类型标识（local/s3） */
    String type();

    /**
     * 上传文件
     *
     * @param category 归类子目录（upload/avatar 等）
     * @param file 上传的文件
     * @param allowedExtension 允许的扩展名，null 表示不校验
     * @param useCustomNaming 是否使用 UUID 自定义文件名
     * @return /profile 前缀的相对访问路径，如 /profile/upload/2026/09/07/a_1.jpg
     */
    String upload(String category, MultipartFile file, String[] allowedExtension, boolean useCustomNaming)
            throws IOException;

    /**
     * 打开文件读取流，供 /profile/** 代理输出
     *
     * @param objectKey 去掉 /profile 前缀的对象 key，如 upload/2026/09/07/a_1.jpg
     */
    InputStream getContent(String objectKey) throws IOException;

    /**
     * 删除文件，文件不存在时静默返回
     *
     * @param objectKey 去掉 /profile 前缀的对象 key
     */
    void delete(String objectKey) throws IOException;

    /**
     * 列出某归类下已存储的对象，供孤儿文件清理对账
     *
     * @param category 归类子目录（upload/avatar/cover 等）
     * @return 对象 key（去掉 /profile 前缀，与 delete 的参数一致）-> 最后修改时间
     */
    Map<String, Instant> list(String category) throws IOException;
}
