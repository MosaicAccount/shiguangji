package com.shiguangji.file.service;

import com.shiguangji.file.domain.SysFile;

/**
 * 文件台账 服务层
 *
 * @author shiguangji
 */
public interface ISysFileService
{
    /**
     * 登记一条上传记录（文件已落存储，登记失败仅记日志不影响上传）
     *
     * @param sysFile 文件台账
     */
    public void recordFile(SysFile sysFile);
}
