package com.shiguangji.file.domain;

import com.shiguangji.common.core.domain.BaseEntity;

/**
 * 文件台账对象 sys_file
 *
 * 仅登记上传事实供审计/清理/迁移使用，不记录业务关联；
 * 业务表自行保存 storageKey（/profile 前缀相对路径）。
 *
 * @author shiguangji
 */
public class SysFile extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 文件ID */
    private Long fileId;

    /** 原始文件名 */
    private String fileName;

    /** 存储key（/profile 前缀相对路径） */
    private String storageKey;

    /** 存储类型（local/s3） */
    private String storageType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** MIME类型 */
    private String contentType;

    public Long getFileId()
    {
        return fileId;
    }

    public void setFileId(Long fileId)
    {
        this.fileId = fileId;
    }

    public String getFileName()
    {
        return fileName;
    }

    public void setFileName(String fileName)
    {
        this.fileName = fileName;
    }

    public String getStorageKey()
    {
        return storageKey;
    }

    public void setStorageKey(String storageKey)
    {
        this.storageKey = storageKey;
    }

    public String getStorageType()
    {
        return storageType;
    }

    public void setStorageType(String storageType)
    {
        this.storageType = storageType;
    }

    public Long getFileSize()
    {
        return fileSize;
    }

    public void setFileSize(Long fileSize)
    {
        this.fileSize = fileSize;
    }

    public String getContentType()
    {
        return contentType;
    }

    public void setContentType(String contentType)
    {
        this.contentType = contentType;
    }
}
