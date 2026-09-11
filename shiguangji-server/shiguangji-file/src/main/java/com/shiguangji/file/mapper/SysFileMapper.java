package com.shiguangji.file.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

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

    /**
     * 查询全部台账记录，供孤儿文件清理对账
     *
     * @return 台账记录集合（仅含 key、存储类型与登记时间）
     */
    public List<SysFile> selectAllFileRecords();

    /**
     * 按存储 key 批量删除台账记录
     *
     * @param storageKeys 存储 key 集合（调用方保证非空）
     * @return 影响行数
     */
    public int deleteFileRecordsByStorageKeys(@Param("storageKeys") List<String> storageKeys);
}
