package com.shiguangji.file.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.shiguangji.file.domain.SysFile;
import com.shiguangji.file.mapper.SysFileMapper;
import com.shiguangji.file.service.ISysFileService;

/**
 * 文件台账 服务实现
 *
 * ponytail: 登记失败不回滚上传——文件本体已可访问，台账是辅助数据，
 * 中断上传对用户伤害更大；后续如需强一致再改为先登记后清理的任务补偿。
 *
 * @author shiguangji
 */
@Service
public class SysFileServiceImpl implements ISysFileService
{
    private static final Logger log = LoggerFactory.getLogger(SysFileServiceImpl.class);

    @Autowired
    private SysFileMapper sysFileMapper;

    @Override
    public void recordFile(SysFile sysFile)
    {
        try
        {
            sysFileMapper.insertSysFile(sysFile);
        }
        catch (Exception e)
        {
            log.error("文件台账登记失败：{}", sysFile.getStorageKey(), e);
        }
    }
}
