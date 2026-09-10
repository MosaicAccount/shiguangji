package com.shiguangji.web.controller.business;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shiguangji.common.annotation.Anonymous;
import com.shiguangji.common.core.controller.BaseController;
import com.shiguangji.common.core.domain.AjaxResult;
import com.shiguangji.common.utils.StringUtils;
import com.shiguangji.web.service.cover.CoverFetchClient;
import com.shiguangji.web.service.cover.ItemCoverService;

/**
 * 条目封面（issue #5）：豆瓣候选搜索、图片代理、转存、缺失封面批量补全
 *
 * <p>search/import 需登录（写库入口）；proxy 匿名 —— 候选缩略图经 &lt;img&gt; 展示无法携带
 * 登录态，仅放行豆瓣 CDN 白名单域名；backfill 为后台管理操作。</p>
 *
 * @author shiguangji
 */
@RestController
public class ItemCoverController extends BaseController
{
    @Autowired
    private ItemCoverService coverService;

    /**
     * 候选封面图代理：经白名单校验后回源豆瓣 CDN 输出（候选缩略图展示用）
     */
    @Anonymous
    @GetMapping("/app/item/cover/proxy")
    public ResponseEntity<byte[]> proxy(@RequestParam String u)
    {
        CoverFetchClient.Image image = coverService.fetchImage(u);
        MediaType mediaType = StringUtils.isEmpty(image.contentType()) ? MediaType.IMAGE_JPEG
                : MediaType.parseMediaType(image.contentType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)))
                .body(image.bytes());
    }

    /**
     * 搜索豆瓣封面候选（影视/书籍），返回的 imageUrl 为本服务代理相对路径
     */
    @GetMapping("/app/item/cover/search")
    public AjaxResult search(@RequestParam String itemType, @RequestParam String title)
    {
        if (!List.of("MOVIE", "TV", "BOOK").contains(itemType))
        {
            return error("该条目类型不支持自动匹配封面");
        }
        if (StringUtils.isEmpty(title))
        {
            return error("标题不能为空");
        }
        return success(coverService.search(itemType, title));
    }

    /**
     * 转存豆瓣封面到自有存储，返回 {url: /profile 相对路径}（提交时随条目一起保存）
     */
    @PostMapping("/app/item/cover/import")
    public AjaxResult importCover(@RequestBody CoverImportRequest request)
    {
        if (request.sourceUrl() == null || request.sourceUrl().isEmpty())
        {
            return error("来源地址不能为空");
        }
        String url = coverService.importCover(request.itemType(), request.sourceUrl());
        // 注意走 success(Object)：success(String) 会把内容当提示语放进 msg
        return success(Map.of("url", url));
    }

    /**
     * 批量补全缺失封面（后台管理）：扫描无封面条目，按标题自动匹配豆瓣首个候选
     */
    @PreAuthorize("@ss.hasPermi('sgj:item:edit')")
    @PostMapping("/business/item/cover/backfill")
    public AjaxResult backfill(@RequestParam(required = false) String itemType)
    {
        Map<String, Integer> summary = coverService.backfillMissingCovers(itemType, getUsername());
        return success(summary);
    }

    /** 转存请求体 */
    record CoverImportRequest(String itemType, String sourceUrl, String sourceId)
    {
    }
}
