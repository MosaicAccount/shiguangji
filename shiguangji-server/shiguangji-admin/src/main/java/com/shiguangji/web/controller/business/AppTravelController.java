package com.shiguangji.web.controller.business;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shiguangji.business.domain.SgjItem;
import com.shiguangji.business.domain.SgjItemPhoto;
import com.shiguangji.business.mapper.SgjItemPhotoMapper;
import com.shiguangji.business.service.ISgjItemService;
import com.shiguangji.common.annotation.Anonymous;
import com.shiguangji.common.core.controller.BaseController;
import com.shiguangji.common.core.domain.AjaxResult;

/**
 * 前台旅行轨迹 操作处理
 *
 * 只读接口，保持匿名访问
 *
 * @author shiguangji
 */
@RestController
@RequestMapping("/app/travel")
public class AppTravelController extends BaseController
{
    @Autowired
    private ISgjItemService sgjItemService;

    @Autowired
    private AppScopeHelper appScopeHelper;

    @Autowired
    private SgjItemPhotoMapper sgjItemPhotoMapper;

    /**
     * 获取旅行轨迹数据（去过地点按时间排序 + 想去地点，按可见范围过滤；
     * 附带每点的照片数与首张照片 URL，供地图照片聚合与旅行时间线使用）
     */
    @Anonymous
    @GetMapping("/trajectory")
    public AjaxResult trajectory()
    {
        SgjItem query = new SgjItem();
        query.setItemType("PLACE");
        // 强制按当前请求的可见范围过滤，防止越权查看他人地点数据
        query.setCreateBy(appScopeHelper.resolveCreateBy());
        List<SgjItem> places = sgjItemService.selectSgjItemList(query);

        // 先收集有坐标的点，再一次性批量取照片，避免逐点查库
        List<Long> itemIds = places.stream()
                .filter(p -> p.getLatitude() != null && p.getLongitude() != null)
                .map(SgjItem::getItemId)
                .collect(Collectors.toList());
        Map<Long, List<SgjItemPhoto>> photosByItem = itemIds.isEmpty() ? Collections.emptyMap()
                : sgjItemPhotoMapper.selectPhotosByItemIds(itemIds).stream()
                        .collect(Collectors.groupingBy(SgjItemPhoto::getItemId));

        List<Map<String, Object>> visited = new ArrayList<>();
        List<Map<String, Object>> wish = new ArrayList<>();

        for (SgjItem place : places)
        {
            if (place.getLatitude() == null || place.getLongitude() == null)
            {
                continue;
            }

            List<SgjItemPhoto> photos = photosByItem.getOrDefault(place.getItemId(), Collections.emptyList());

            Map<String, Object> point = new HashMap<>();
            point.put("itemId", place.getItemId());
            point.put("title", place.getTitle());
            point.put("city", place.getCity());
            point.put("country", place.getCountry());
            point.put("latitude", place.getLatitude());
            point.put("longitude", place.getLongitude());
            point.put("finishDate", place.getFinishDate());
            point.put("updateTime", place.getUpdateTime());
            point.put("photoCount", photos.size());
            point.put("cover", photos.isEmpty() ? null : photos.get(0).getUrl());

            if ("DONE".equals(place.getStatus()))
            {
                visited.add(point);
            }
            else
            {
                wish.add(point);
            }
        }

        // 优先按去过时间排序，没有去过时间时按更新时间兜底，保证轨迹顺序稳定
        visited.sort(Comparator.comparing(
                (Map<String, Object> m) -> {
                    Date finishDate = (Date) m.get("finishDate");
                    return finishDate != null ? finishDate : (Date) m.get("updateTime");
                },
                Comparator.nullsLast(Comparator.naturalOrder())));

        Map<String, Object> result = new HashMap<>();
        result.put("visited", visited);
        result.put("wish", wish);
        return success(result);
    }
}