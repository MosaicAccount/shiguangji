package com.shiguangji.business.service.impl;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.shiguangji.business.domain.SgjTag;
import com.shiguangji.business.mapper.SgjTagMapper;
import com.shiguangji.business.service.ISgjTagService;
import com.shiguangji.common.exception.ServiceException;
import com.shiguangji.common.utils.StringUtils;

/**
 * 标签 服务层实现
 *
 * @author shiguangji
 */
@Service
public class SgjTagServiceImpl implements ISgjTagService
{
    /** 标签名称最大长度（与表结构 varchar(50) 一致） */
    private static final int TAG_NAME_MAX_LENGTH = 50;

    /** 备注最大长度 */
    private static final int REMARK_MAX_LENGTH = 500;

    /** 合法模块取值：复用 sgj_item.item_type 并扩展 NOTE */
    private static final Set<String> VALID_MODULES = Set.of("MOVIE", "TV", "BOOK", "PLACE", "NOTE");

    /** 状态：启用 */
    private static final String STATUS_OK = "0";

    /** 状态：停用 */
    private static final String STATUS_DISABLED = "1";

    @Autowired
    private SgjTagMapper sgjTagMapper;

    @Override
    public SgjTag selectSgjTagById(Long tagId)
    {
        return sgjTagMapper.selectSgjTagById(tagId);
    }

    @Override
    public List<SgjTag> selectSgjTagList(SgjTag sgjTag)
    {
        return sgjTagMapper.selectSgjTagList(sgjTag);
    }

    @Override
    public int insertSgjTag(SgjTag sgjTag)
    {
        validateTag(sgjTag);
        checkModuleNameUnique(sgjTag);
        // 新增未传状态时默认启用
        if (StringUtils.isEmpty(sgjTag.getStatus()))
        {
            sgjTag.setStatus(STATUS_OK);
        }
        return sgjTagMapper.insertSgjTag(sgjTag);
    }

    @Override
    public int updateSgjTag(SgjTag sgjTag)
    {
        if (sgjTag.getTagId() == null)
        {
            throw new ServiceException("标签ID不能为空");
        }
        validateTag(sgjTag);
        checkModuleNameUnique(sgjTag);
        return sgjTagMapper.updateSgjTag(sgjTag);
    }

    @Override
    public int deleteSgjTagByIds(Long[] tagIds)
    {
        return sgjTagMapper.deleteSgjTagByIds(tagIds);
    }

    /**
     * 参数校验：模块必填且取值合法、名称必填/长度/不含分隔符、状态取值
     */
    private void validateTag(SgjTag sgjTag)
    {
        if (StringUtils.isEmpty(sgjTag.getModule()) || !VALID_MODULES.contains(sgjTag.getModule()))
        {
            throw new ServiceException("标签所属模块取值非法（仅允许 MOVIE/TV/BOOK/PLACE/NOTE）");
        }
        String tagName = sgjTag.getTagName();
        if (StringUtils.isBlank(tagName))
        {
            throw new ServiceException("标签名称不能为空");
        }
        // 名称去首尾空白并回写；禁止逗号/空白分隔符，避免破坏记录侧逗号拼接存储
        tagName = tagName.trim();
        if (tagName.length() > TAG_NAME_MAX_LENGTH)
        {
            throw new ServiceException("标签名称长度不能超过" + TAG_NAME_MAX_LENGTH + "个字符");
        }
        if (tagName.contains(",") || tagName.contains("，") || tagName.matches(".*\\s.*"))
        {
            throw new ServiceException("标签名称不能包含逗号或空白字符");
        }
        sgjTag.setTagName(tagName);
        if (sgjTag.getRemark() != null && sgjTag.getRemark().length() > REMARK_MAX_LENGTH)
        {
            throw new ServiceException("备注长度不能超过" + REMARK_MAX_LENGTH + "个字符");
        }
        String status = sgjTag.getStatus();
        if (!StringUtils.isEmpty(status) && !STATUS_OK.equals(status) && !STATUS_DISABLED.equals(status))
        {
            throw new ServiceException("状态取值非法（仅允许 0 启用 / 1 停用）");
        }
    }

    /**
     * 同模块内标签名称唯一（不同模块允许同名）
     */
    private void checkModuleNameUnique(SgjTag sgjTag)
    {
        SgjTag query = new SgjTag();
        query.setModule(sgjTag.getModule());
        query.setTagName(sgjTag.getTagName());
        SgjTag exists = sgjTagMapper.selectSgjTagByModuleAndName(query);
        if (exists != null && !exists.getTagId().equals(sgjTag.getTagId()))
        {
            throw new ServiceException("该模块下已存在同名标签：" + sgjTag.getTagName());
        }
    }
}
