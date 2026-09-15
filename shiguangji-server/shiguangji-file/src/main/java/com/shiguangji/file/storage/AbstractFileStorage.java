package com.shiguangji.file.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import org.springframework.web.multipart.MultipartFile;
import com.shiguangji.common.constant.Constants;
import com.shiguangji.common.exception.file.FileException;
import com.shiguangji.common.exception.file.FileUploadException;
import com.shiguangji.common.exception.file.FileNameLengthLimitExceededException;
import com.shiguangji.common.utils.StringUtils;
import com.shiguangji.common.utils.file.FileUploadUtils;

/**
 * 存储策略公共流程：类型校验 -> 生成文件名 -> 写入存储 -> 拼装 /profile 相对路径
 *
 * @author shiguangji
 */
public abstract class AbstractFileStorage implements FileStorageService
{
    @Override
    public final String upload(String category, MultipartFile file, String[] allowedExtension, boolean useCustomNaming)
            throws IOException
    {
        try
        {
            int fileNameLength = Objects.requireNonNull(file.getOriginalFilename()).length();
            if (fileNameLength > FileUploadUtils.DEFAULT_FILE_NAME_LENGTH)
            {
                throw new FileNameLengthLimitExceededException(FileUploadUtils.DEFAULT_FILE_NAME_LENGTH);
            }
            FileUploadUtils.assertAllowed(file, allowedExtension);
        }
        catch (FileException | FileUploadException e)
        {
            // 校验类受检异常统一包装为 IOException，错误信息原样透出
            throw new IOException(e.getMessage(), e);
        }
        String fileName = useCustomNaming ? FileUploadUtils.uuidFilename(file) : FileUploadUtils.extractFilename(file);
        String objectKey = category + "/" + fileName;
        doStore(objectKey, file.getInputStream(), file.getSize(), file.getContentType());
        return Constants.RESOURCE_PREFIX + "/" + objectKey;
    }

    @Override
    public InputStream getContent(String objectKey) throws IOException
    {
        if (StringUtils.isEmpty(objectKey))
        {
            throw new IOException("文件key不能为空");
        }
        for (String part : objectKey.split("/"))
        {
            if ("..".equals(part))
            {
                throw new IOException("非法的文件key：" + objectKey);
            }
        }
        return doGetContent(objectKey);
    }

    /**
     * 存储文件内容
     *
     * @param objectKey 去掉 /profile 前缀的对象 key，如 upload/2026/09/07/a_1.jpg
     * @param in 文件内容流
     * @param size 文件大小（字节）
     * @param contentType 文件 MIME 类型
     */
    protected abstract void doStore(String objectKey, InputStream in, long size, String contentType) throws IOException;

    /** 按 doStore 生成的对象 key 读取内容 */
    protected abstract InputStream doGetContent(String objectKey) throws IOException;
}
