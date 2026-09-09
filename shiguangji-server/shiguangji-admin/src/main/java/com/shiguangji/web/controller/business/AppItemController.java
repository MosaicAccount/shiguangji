package com.shiguangji.web.controller.business;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.github.pagehelper.Page;
import com.shiguangji.business.domain.SgjItem;
import com.shiguangji.business.service.ISgjItemService;
import com.shiguangji.common.annotation.Anonymous;
import com.shiguangji.common.core.controller.BaseController;
import com.shiguangji.common.core.domain.AjaxResult;
import com.shiguangji.common.utils.SecurityUtils;

/**
 * 前台影视/内容条目 操作处理
 *
 * 浏览接口（GET）保持匿名只读；新增/标记完成等写操作要求登录后本人操作
 *
 * @author shiguangji
 */
@RestController
@RequestMapping("/app/item")
public class AppItemController extends BaseController
{
    @Autowired
    private ISgjItemService sgjItemService;

    @Autowired
    private AppScopeHelper appScopeHelper;

    /**
     * 查询前台内容条目列表（按可见范围过滤 ；分页 ，默认第1页每页10条）
     *
     * ：匿名访客响应脱敏，不返回 remark/comment 私人字段
     */
    @Anonymous
    @GetMapping("/list")
    public AjaxResult list(SgjItem sgjItem)
    {
        // 强制按当前请求的可见范围过滤，防止越权查看他人数据
        sgjItem.setCreateBy(appScopeHelper.resolveCreateBy());
        startPage();
        List<SgjItem> list = sgjItemService.selectSgjItemList(sgjItem);
        // 访客响应脱敏
        appScopeHelper.maskItemsForGuest(list);
        AjaxResult result = success(list);
        result.put("total", ((Page<?>) list).getTotal());
        return result;
    }

    /**
     * 查询前台内容条目详情（按可见范围过滤，）
     *
     * ：匿名访客响应脱敏，不返回 remark/comment 私人字段
     */
    @Anonymous
    @GetMapping("/{itemId}")
    public AjaxResult getInfo(@PathVariable Long itemId)
    {
        SgjItem item = sgjItemService.selectSgjItemById(itemId);
        if (item == null || !isVisible(item.getCreateBy()))
        {
            return error("条目不存在或无权访问");
        }
        // 访客响应脱敏
        appScopeHelper.maskItemForGuest(item);
        return success(item);
    }

    /**
     * 判断当前请求是否可见该创建者对应的数据
     */
    private boolean isVisible(String createBy)
    {
        String scope = appScopeHelper.resolveCreateBy();
        return scope == null || createBy == null || createBy.isEmpty() || scope.equals(createBy);
    }

    /**
     * 新增前台内容条目
     */
    @PostMapping
    public AjaxResult add(@RequestBody SgjItem sgjItem)
    {
        sgjItem.setCreateBy(SecurityUtils.getUsername());
        return toAjax(sgjItemService.insertSgjItem(sgjItem));
    }

    /**
     * 修改前台内容条目（，仅本人或管理员）
     */
    @PutMapping("/{itemId}")
    public AjaxResult edit(@PathVariable Long itemId, @RequestBody SgjItem request)
    {
        SgjItem item = sgjItemService.selectSgjItemById(itemId);
        if (item == null)
        {
            return error("条目不存在");
        }
        if (!appScopeHelper.canOperate(item.getCreateBy()))
        {
            return error("无权操作该条目");
        }
        mergeEditableFields(item, request);
        item.setUpdateBy(SecurityUtils.getUsername());
        return toAjax(sgjItemService.updateSgjItem(item));
    }

    /**
     * 删除前台内容条目（，仅本人或管理员）
     */
    @DeleteMapping("/{itemId}")
    public AjaxResult remove(@PathVariable Long itemId)
    {
        SgjItem item = sgjItemService.selectSgjItemById(itemId);
        if (item == null)
        {
            return error("条目不存在");
        }
        if (!appScopeHelper.canOperate(item.getCreateBy()))
        {
            return error("无权操作该条目");
        }
        return toAjax(sgjItemService.deleteSgjItemByIds(new Long[] { itemId }));
    }

    /**
     * 取消标记完成（看过/读过/去过 改回 想看/想读/想去，，仅本人或管理员）
     */
    @PostMapping("/{itemId}/uncomplete")
    public AjaxResult uncomplete(@PathVariable Long itemId)
    {
        SgjItem item = sgjItemService.selectSgjItemById(itemId);
        if (item == null)
        {
            return error("条目不存在");
        }
        if (!appScopeHelper.canOperate(item.getCreateBy()))
        {
            return error("无权操作该条目");
        }
        item.setStatus("WANT");
        item.setUpdateBy(SecurityUtils.getUsername());
        return toAjax(sgjItemService.updateSgjItem(item));
    }

    /**
     * 查询回收站条目列表（需登录，按可见范围过滤，）
     */
    @GetMapping("/recycle/list")
    public AjaxResult recycleList(SgjItem sgjItem)
    {
        sgjItem.setCreateBy(appScopeHelper.resolveCreateBy());
        List<SgjItem> list = sgjItemService.selectRecycleItemList(sgjItem);
        return success(list);
    }

    /**
     * 恢复回收站条目（，仅本人或管理员）
     */
    @PostMapping("/recycle/restore/{itemId}")
    public AjaxResult recycleRestore(@PathVariable Long itemId)
    {
        if (!canOperateRecycleItem(itemId))
        {
            return error("回收站中不存在该条目或无权操作");
        }
        return toAjax(sgjItemService.restoreSgjItemByIds(new Long[] { itemId }));
    }

    /**
     * 彻底删除回收站条目（，仅本人或管理员）
     */
    @PostMapping("/recycle/purge/{itemId}")
    public AjaxResult recyclePurge(@PathVariable Long itemId)
    {
        if (!canOperateRecycleItem(itemId))
        {
            return error("回收站中不存在该条目或无权操作");
        }
        return toAjax(sgjItemService.purgeSgjItemByIds(new Long[] { itemId }));
    }

    /**
     * 校验回收站条目归属（本人或管理员）
     */
    private boolean canOperateRecycleItem(Long itemId)
    {
        SgjItem query = new SgjItem();
        query.setItemId(itemId);
        List<SgjItem> items = sgjItemService.selectRecycleItemList(query);
        if (items.isEmpty())
        {
            return false;
        }
        return appScopeHelper.canOperate(items.get(0).getCreateBy());
    }

    /**
     * 标记条目已完成（看过/读过/去过）
     */
    @PostMapping("/{itemId}/complete")
    public AjaxResult complete(@PathVariable Long itemId, @RequestBody SgjItem request)
    {
        SgjItem item = sgjItemService.selectSgjItemById(itemId);
        if (item == null)
        {
            return error("条目不存在");
        }
        if (!appScopeHelper.canOperate(item.getCreateBy()))
        {
            return error("无权操作该条目");
        }
        item.setStatus("DONE");
        // 未传完成日期时默认取当天
        item.setFinishDate(request.getFinishDate() != null ? request.getFinishDate() : new Date());
        if (request.getRating() != null)
        {
            item.setRating(request.getRating());
        }
        if (request.getComment() != null)
        {
            item.setComment(request.getComment());
        }
        item.setUpdateBy(SecurityUtils.getUsername());
        return toAjax(sgjItemService.updateSgjItem(item));
    }

    /**
     * 将请求中可编辑字段合并到已有条目（仅覆盖非空字段；条目类型不允许修改）
     */
    private void mergeEditableFields(SgjItem item, SgjItem request)
    {
        if (request.getTitle() != null && !request.getTitle().isEmpty())
        {
            item.setTitle(request.getTitle());
        }
        if (request.getStatus() != null && !request.getStatus().isEmpty())
        {
            item.setStatus(request.getStatus());
        }
        if (request.getRating() != null)
        {
            item.setRating(request.getRating());
        }
        if (request.getComment() != null)
        {
            item.setComment(request.getComment());
        }
        if (request.getTags() != null)
        {
            item.setTags(request.getTags());
        }
        if (request.getCoverUrl() != null)
        {
            item.setCoverUrl(request.getCoverUrl());
        }
        if (request.getStartDate() != null)
        {
            item.setStartDate(request.getStartDate());
        }
        if (request.getFinishDate() != null)
        {
            item.setFinishDate(request.getFinishDate());
        }
        if (request.getRemark() != null)
        {
            item.setRemark(request.getRemark());
        }
        // 电影/电视剧公共扩展字段
        if (request.getDirector() != null)
        {
            item.setDirector(request.getDirector());
        }
        if (request.getActors() != null)
        {
            item.setActors(request.getActors());
        }
        if (request.getGenre() != null)
        {
            item.setGenre(request.getGenre());
        }
        if (request.getRegion() != null)
        {
            item.setRegion(request.getRegion());
        }
        if (request.getLanguage() != null)
        {
            item.setLanguage(request.getLanguage());
        }
        if (request.getImdbId() != null)
        {
            item.setImdbId(request.getImdbId());
        }
        if (request.getDoubanId() != null)
        {
            item.setDoubanId(request.getDoubanId());
        }
        // 电影扩展字段
        if (request.getReleaseYear() != null)
        {
            item.setReleaseYear(request.getReleaseYear());
        }
        if (request.getDurationMinutes() != null)
        {
            item.setDurationMinutes(request.getDurationMinutes());
        }
        // 电视剧扩展字段
        if (request.getStartYear() != null)
        {
            item.setStartYear(request.getStartYear());
        }
        if (request.getEndYear() != null)
        {
            item.setEndYear(request.getEndYear());
        }
        if (request.getSeasonCount() != null)
        {
            item.setSeasonCount(request.getSeasonCount());
        }
        if (request.getEpisodeCount() != null)
        {
            item.setEpisodeCount(request.getEpisodeCount());
        }
        // 书籍扩展字段
        if (request.getAuthor() != null)
        {
            item.setAuthor(request.getAuthor());
        }
        if (request.getPublisher() != null)
        {
            item.setPublisher(request.getPublisher());
        }
        if (request.getPublishDate() != null)
        {
            item.setPublishDate(request.getPublishDate());
        }
        if (request.getIsbn() != null)
        {
            item.setIsbn(request.getIsbn());
        }
        if (request.getPages() != null)
        {
            item.setPages(request.getPages());
        }
        // 地点扩展字段
        if (request.getAddress() != null)
        {
            item.setAddress(request.getAddress());
        }
        if (request.getCity() != null)
        {
            item.setCity(request.getCity());
        }
        if (request.getProvince() != null)
        {
            item.setProvince(request.getProvince());
        }
        if (request.getCountry() != null)
        {
            item.setCountry(request.getCountry());
        }
        if (request.getLatitude() != null)
        {
            item.setLatitude(request.getLatitude());
        }
        if (request.getLongitude() != null)
        {
            item.setLongitude(request.getLongitude());
        }
        if (request.getBestSeason() != null)
        {
            item.setBestSeason(request.getBestSeason());
        }
        if (request.getPlaceCategory() != null)
        {
            item.setPlaceCategory(request.getPlaceCategory());
        }
        // 照片串（逗号分隔，空串表示清空），由 service 整体替换到 sgj_item_photo 表
        if (request.getPhotos() != null)
        {
            item.setPhotos(request.getPhotos());
        }
    }
}