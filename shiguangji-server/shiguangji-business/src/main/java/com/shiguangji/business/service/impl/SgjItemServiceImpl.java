package com.shiguangji.business.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shiguangji.business.domain.SgjItem;
import com.shiguangji.business.domain.SgjItemPhoto;
import com.shiguangji.business.mapper.SgjItemMapper;
import com.shiguangji.business.mapper.SgjItemPhotoMapper;
import com.shiguangji.business.service.ISgjItemService;
import com.shiguangji.common.exception.ServiceException;
import com.shiguangji.common.utils.StringUtils;

/**
 * 内容条目 服务层实现
 *
 * @author shiguangji
 */
@Service
public class SgjItemServiceImpl implements ISgjItemService
{
    /** 条目标题最大长度（与表结构 varchar(200) 一致） */
    private static final int TITLE_MAX_LENGTH = 200;

    /** 长文本字段最大长度 */
    private static final int TEXT_MAX_LENGTH = 500;

    /** 单张照片URL最大长度（与 sgj_item_photo.url varchar(500) 一致） */
    private static final int PHOTO_URL_MAX_LENGTH = 500;

    /** 允许的条目类型 */
    private static final java.util.List<String> ITEM_TYPES = Arrays.asList("MOVIE", "TV", "BOOK", "PLACE");

    /** 允许的状态 */
    private static final java.util.List<String> ITEM_STATUS = Arrays.asList("WANT", "DONE");

    @Autowired
    private SgjItemMapper sgjItemMapper;

    @Autowired
    private SgjItemPhotoMapper sgjItemPhotoMapper;

    @Override
    public SgjItem selectSgjItemById(Long itemId)
    {
        SgjItem item = sgjItemMapper.selectSgjItemById(itemId);
        attachPhotos(item);
        return item;
    }

    @Override
    public List<SgjItem> selectSgjItemList(SgjItem sgjItem)
    {
        return sgjItemMapper.selectSgjItemList(sgjItem);
    }

    @Override
    public List<SgjItem> selectRecycleItemList(SgjItem sgjItem)
    {
        return sgjItemMapper.selectRecycleItemList(sgjItem);
    }

    @Override
    @Transactional
    public int insertSgjItem(SgjItem sgjItem)
    {
        validateItem(sgjItem, true);
        if (StringUtils.isEmpty(sgjItem.getStatus()))
        {
            sgjItem.setStatus("WANT");
        }
        int rows = sgjItemMapper.insertSgjItem(sgjItem);
        sgjItemMapper.insertSgjItemExt(sgjItem);
        syncPhotos(sgjItem);
        return rows;
    }

    @Override
    @Transactional
    public int updateSgjItem(SgjItem sgjItem)
    {
        if (sgjItem.getItemId() == null)
        {
            throw new ServiceException("条目ID不能为空");
        }
        validateItem(sgjItem, false);
        SgjItem oldItem = sgjItemMapper.selectSgjItemById(sgjItem.getItemId());
        if (oldItem == null)
        {
            throw new ServiceException("条目不存在");
        }
        if (!oldItem.getItemType().equals(sgjItem.getItemType()))
        {
            throw new ServiceException("不允许修改条目类型");
        }
        attachPhotos(oldItem);
        int rows = sgjItemMapper.updateSgjItem(sgjItem);
        sgjItemMapper.updateSgjItemExt(sgjItem);
        // 仅当照片串变化时才整体替换，避免 complete/uncomplete 等复用更新的链路反复重写照片表
        if (sgjItem.getPhotos() != null && !sgjItem.getPhotos().equals(oldItem.getPhotos()))
        {
            syncPhotos(sgjItem);
        }
        return rows;
    }

    @Override
    public int deleteSgjItemByIds(Long[] itemIds)
    {
        return sgjItemMapper.deleteSgjItemByIds(itemIds);
    }

    @Override
    public int restoreSgjItemByIds(Long[] itemIds)
    {
        return sgjItemMapper.restoreSgjItemByIds(itemIds);
    }

    @Override
    public int purgeSgjItemByIds(Long[] itemIds)
    {
        return sgjItemMapper.purgeSgjItemByIds(itemIds);
    }

    /**
     * 参数校验：标题必填/长度、条目类型枚举、状态枚举、评分0-10、日期合法性、字段长度
     *
     * @param sgjItem      条目
     * @param titleRequired 新增时标题必填；编辑时允许不传标题（部分更新）
     */
    // 包内可见便于照片校验单测直调（与 parsePhotoUrls 同策略）
    void validateItem(SgjItem sgjItem, boolean titleRequired)
    {
        if (StringUtils.isEmpty(sgjItem.getItemType()))
        {
            throw new ServiceException("条目类型不能为空");
        }
        if (!ITEM_TYPES.contains(sgjItem.getItemType()))
        {
            throw new ServiceException("不支持的条目类型：" + sgjItem.getItemType());
        }
        // 标题去除首尾空白后校验必填/长度，并回写 trim 值（修复：避免全空格标题或首尾空白入库）
        String title = sgjItem.getTitle();
        if (titleRequired && StringUtils.isBlank(title))
        {
            throw new ServiceException("标题不能为空");
        }
        if (title != null && title.trim().length() > TITLE_MAX_LENGTH)
        {
            throw new ServiceException("标题长度不能超过" + TITLE_MAX_LENGTH + "个字符");
        }
        if (title != null && !title.equals(title.trim()))
        {
            sgjItem.setTitle(title.trim());
        }
        if (!StringUtils.isEmpty(sgjItem.getStatus()) && !ITEM_STATUS.contains(sgjItem.getStatus()))
        {
            throw new ServiceException("状态值不合法，仅支持WANT/DONE");
        }
        BigDecimal rating = sgjItem.getRating();
        if (rating != null && (rating.compareTo(BigDecimal.ZERO) < 0 || rating.compareTo(new BigDecimal("10")) > 0))
        {
            throw new ServiceException("评分必须在0-10之间");
        }
        validateTextLength(sgjItem.getTags(), "标签", TEXT_MAX_LENGTH);
        validateTextLength(sgjItem.getCoverUrl(), "封面地址", TEXT_MAX_LENGTH);
        validateTextLength(sgjItem.getRemark(), "备注", TEXT_MAX_LENGTH);
        for (String url : parsePhotoUrls(sgjItem.getPhotos()))
        {
            if (url.length() > PHOTO_URL_MAX_LENGTH)
            {
                throw new ServiceException("单张照片地址长度不能超过" + PHOTO_URL_MAX_LENGTH + "个字符");
            }
        }
        validateDateOrder(sgjItem.getStartDate(), sgjItem.getFinishDate());
    }

    /**
     * 校验文本字段长度
     */
    private void validateTextLength(String text, String fieldName, int maxLength)
    {
        if (text != null && text.length() > maxLength)
        {
            throw new ServiceException(fieldName + "长度不能超过" + maxLength + "个字符");
        }
    }

    /**
     * 校验日期合法性：完成日期不能早于开始日期
     */
    private void validateDateOrder(Date startDate, Date finishDate)
    {
        if (startDate != null && finishDate != null && finishDate.before(startDate))
        {
            throw new ServiceException("完成日期不能早于开始日期");
        }
    }

    /**
     * 回填条目照片串（逗号顺序即展示顺序）
     */
    private void attachPhotos(SgjItem item)
    {
        if (item == null)
        {
            return;
        }
        String photos = sgjItemPhotoMapper.selectPhotosByItemId(item.getItemId())
                .stream().map(SgjItemPhoto::getUrl).collect(Collectors.joining(","));
        item.setPhotos(photos);
    }

    /**
     * 整体替换保存条目照片：ponytail: 删后重插会重建 photo_id/create_time；
     * 照片无独立元数据，可接受。升级路径：照片需要备注/拍摄时间时改为逐条 diff 更新
     */
    private void syncPhotos(SgjItem item)
    {
        List<String> urls = parsePhotoUrls(item.getPhotos());
        sgjItemPhotoMapper.deletePhotosByItemId(item.getItemId());
        if (urls.isEmpty())
        {
            return;
        }
        String operator = StringUtils.isNotEmpty(item.getUpdateBy()) ? item.getUpdateBy() : item.getCreateBy();
        List<SgjItemPhoto> photos = new ArrayList<>();
        for (int i = 0; i < urls.size(); i++)
        {
            SgjItemPhoto photo = new SgjItemPhoto();
            photo.setItemId(item.getItemId());
            photo.setUrl(urls.get(i));
            photo.setSortOrder(i);
            photo.setCreateBy(operator);
            photos.add(photo);
        }
        sgjItemPhotoMapper.insertPhotos(photos);
    }

    /**
     * 解析照片URL串：按英文逗号切分，去首尾空白，跳过空段（包内可见便于单测）
     */
    static List<String> parsePhotoUrls(String photos)
    {
        List<String> urls = new ArrayList<>();
        if (photos == null || photos.isEmpty())
        {
            return urls;
        }
        for (String url : photos.split(","))
        {
            String trimmed = url.trim();
            if (!trimmed.isEmpty())
            {
                urls.add(trimmed);
            }
        }
        return urls;
    }
}