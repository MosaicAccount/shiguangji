package com.shiguangji.file.mapper;

import com.shiguangji.file.domain.SysFile;

/**
 * 文件台账 数据层
 *
 * @author shiguangji
 */
public interface SysFileMapper
{
    /**
     * 新增文件台账
     *
     * @param sysFile 文件台账
     * @return 影响行数
     */
    public int insertSysFile(SysFile sysFile);
}
