package com.shiguangji.web.service.cover;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.shiguangji.business.domain.SgjItem;
import com.shiguangji.business.service.ISgjItemService;
import com.shiguangji.common.exception.ServiceException;
import com.shiguangji.common.utils.StringUtils;
import com.shiguangji.common.utils.file.MimeTypeUtils;
import com.shiguangji.file.storage.ByteArrayMultipartFile;
import com.shiguangji.file.storage.FileStorageService;

/**
 * 条目封面服务（issue #5）：豆瓣候选搜索、封面转存、缺失封面批量补全
 *
 * <p>封面统一转存到自有存储（FileStorageService），cover_url 保存自有 /profile 路径，
 * 规避豆瓣 CDN 防盗链导致的站内 403；同时在影视/电视剧扩展表落 douban_id 溯源。</p>
 *
 * @author shiguangji
 */
@Service
public class ItemCoverService
{
    private static final Logger log = LoggerFactory.getLogger(ItemCoverService.class);

    /** 支持自动匹配的条目类型（地点无豆瓣数据源） */
    private static final List<String> MATCHABLE_TYPES = List.of("MOVIE", "TV", "BOOK");

    @Autowired
    private CoverFetchClient coverClient;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private ISgjItemService itemService;

    /** 封面来源图片域名白名单（SSRF/防盗链防护，后缀匹配，逗号分隔） */
    @Value("${cover.image-host-suffixes:doubanio.com}")
    private String imageHostSuffixes;

    /** 批量补全逐条间隔毫秒数，降低对豆瓣的请求频率 */
    @Value("${cover.backfill.interval-ms:500}")
    private long backfillIntervalMs;

    /**
     * 搜索封面候选；imageUrl 改写为后端代理相对路径，浏览器经我方服务展示缩略图
     */
    public List<CoverFetchClient.Candidate> search(String itemType, String keyword)
    {
        return coverClient.search(itemType, keyword).stream()
                .map(c -> new CoverFetchClient.Candidate(c.sourceId(), c.title(), c.year(), proxyPath(c.imageUrl())))
                .toList();
    }

    /**
     * 转存封面：抓取来源图 → 写入自有存储 → 返回 /profile 相对路径
     *
     * <p>搜索结果为小图（s_ratio_poster），优先抓同源大图（l_ratio_poster），失败回退小图。</p>
     *
     * @param itemType 条目类型
     * @param sourceUrl 来源图片地址（仅放行白名单域名）
     */
    public String importCover(String itemType, String sourceUrl)
    {
        CoverFetchClient.Image image = fetchWithFallback(preferLargeUrl(sourceUrl), sourceUrl);
        String filename = "cover." + imageExtension(sourceUrl, image.contentType());
        ByteArrayMultipartFile file = new ByteArrayMultipartFile("file", filename, image.contentType(), image.bytes());
        try
        {
            return fileStorageService.upload("cover", file, MimeTypeUtils.IMAGE_EXTENSION, true);
        }
        catch (java.io.IOException e)
        {
            throw new ServiceException("封面保存失败：" + e.getMessage());
        }
    }

    /**
     * 批量补全缺失封面：按标题搜索豆瓣取首个匹配候选（有年份信息时校验年份一致，不一致跳过）
     *
     * @param itemType 条目类型（空则扫描全部可匹配类型）
     * @param operator 操作人（记录 update_by）
     * @return updated/skipped/failed 汇总
     */
    public Map<String, Integer> backfillMissingCovers(String itemType, String operator)
    {
        List<String> types = StringUtils.isEmpty(itemType) ? MATCHABLE_TYPES : List.of(itemType);
        int updated = 0;
        int skipped = 0;
        int failed = 0;
        for (String type : types)
        {
            SgjItem query = new SgjItem();
            query.setItemType(type);
            query.getParams().put("missingCover", Boolean.TRUE);
            List<SgjItem> items = itemService.selectSgjItemList(query);
            for (SgjItem item : items)
            {
                try
                {
                    // 直接走原始来源图地址（不经代理改写）
                    CoverFetchClient.Candidate pick = pickCandidate(coverClient.search(type, item.getTitle()), item);
                    if (pick == null)
                    {
                        skipped++;
                        continue;
                    }
                    item.setCoverUrl(importCover(type, pick.imageUrl()));
                    if (pick.sourceId() != null && !"BOOK".equals(type))
                    {
                        item.setDoubanId(pick.sourceId());
                    }
                    item.setUpdateBy(operator);
                    itemService.updateSgjItem(item);
                    updated++;
                    Thread.sleep(backfillIntervalMs);
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                    break;
                }
                catch (Exception e)
                {
                    failed++;
                    log.warn("补全封面失败（itemId={}，title={}）：{}", item.getItemId(), item.getTitle(), e.getMessage());
                }
            }
        }
        Map<String, Integer> summary = new LinkedHashMap<>();
        summary.put("updated", updated);
        summary.put("skipped", skipped);
        summary.put("failed", failed);
        return summary;
    }

    /**
     * 抓取图片内容（含白名单校验）
     *
     * <p>ponytail: 单层域名后缀校验，未做 DNS 解析后二次校验（rebinding 需要内网出口才有意义）；
     * 如部署环境出现内网访问需求，升级为「解析 IP 后再校验」。</p>
     */
    public CoverFetchClient.Image fetchImage(String imageUrl)
    {
        if (!isAllowedImageHost(imageUrl, allowedSuffixes()))
        {
            throw new ServiceException("封面来源域名不在白名单内：" + imageUrl);
        }
        return coverClient.fetch(imageUrl);
    }

    /**
     * 域名白名单校验：仅放行 http(s)，主机名为配置后缀的精确或子域名匹配
     */
    static boolean isAllowedImageHost(String url, List<String> suffixes)
    {
        if (StringUtils.isEmpty(url))
        {
            return false;
        }
        URI uri;
        try
        {
            uri = URI.create(url);
        }
        catch (IllegalArgumentException e)
        {
            return false;
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (host == null || (!"http".equals(scheme) && !"https".equals(scheme)))
        {
            return false;
        }
        return suffixes.stream().anyMatch(suffix ->
                host.equals(suffix) || host.endsWith("." + suffix));
    }

    /**
     * 候选挑选：与条目自身年份信息一致的取首个；年份冲突的候选跳过，无年份信息则接受首个
     */
    CoverFetchClient.Candidate pickCandidate(List<CoverFetchClient.Candidate> candidates, SgjItem item)
    {
        Integer refYear = referenceYear(item);
        for (CoverFetchClient.Candidate candidate : candidates)
        {
            Integer candidateYear = parseYear(candidate.year());
            if (refYear != null && candidateYear != null && !refYear.equals(candidateYear))
            {
                continue;
            }
            return candidate;
        }
        return null;
    }

    /** 条目可作比对依据的年份：电影上映年份 / 电视剧开播年份 / 书籍出版年份 */
    private Integer referenceYear(SgjItem item)
    {
        if (item.getReleaseYear() != null)
        {
            return item.getReleaseYear();
        }
        if (item.getStartYear() != null)
        {
            return item.getStartYear();
        }
        Date publishDate = item.getPublishDate();
        if (publishDate != null)
        {
            return LocalDate.ofInstant(publishDate.toInstant(), ZoneId.systemDefault()).getYear();
        }
        return null;
    }

    private Integer parseYear(String year)
    {
        if (StringUtils.isEmpty(year))
        {
            return null;
        }
        try
        {
            return Integer.valueOf(year.trim().substring(0, 4));
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    /** 原始来源图地址 → 本服务代理相对路径（前端拼 baseURL 展示） */
    private String proxyPath(String imageUrl)
    {
        return "/app/item/cover/proxy?u=" + URLEncoder.encode(imageUrl, StandardCharsets.UTF_8);
    }

    /**
     * 小图地址换大图，失败由抓取方回退原地址：
     * 影视海报 s_ratio_poster → l_ratio_poster；书籍封面 /view/subject/s/ → /view/subject/l/
     */
    private String preferLargeUrl(String imageUrl)
    {
        return imageUrl
                .replace("/s_ratio_poster/", "/l_ratio_poster/")
                .replace("/view/subject/s/", "/view/subject/l/");
    }

    /** 抓取主地址失败且与回退地址不同时回退重抓（大图 404 等场景） */
    private CoverFetchClient.Image fetchWithFallback(String primaryUrl, String fallbackUrl)
    {
        try
        {
            return fetchImage(primaryUrl);
        }
        catch (Exception e)
        {
            if (primaryUrl.equals(fallbackUrl))
            {
                throw e;
            }
            log.info("大图获取失败，回退小图：{}", fallbackUrl);
            return fetchImage(fallbackUrl);
        }
    }

    /** 从地址路径或响应类型推断图片扩展名（存储校验用；未知类型默认 jpg，豆瓣海报均为 jpg） */
    private String imageExtension(String imageUrl, String contentType)
    {
        if (StringUtils.isNotEmpty(contentType) && contentType.contains("/"))
        {
            String sub = contentType.split(";")[0].trim().split("/")[1];
            if (Arrays.asList(MimeTypeUtils.IMAGE_EXTENSION).contains(sub))
            {
                return sub;
            }
        }
        String path = URI.create(imageUrl).getPath();
        int dot = path.lastIndexOf('.');
        if (dot >= 0)
        {
            String ext = path.substring(dot + 1).toLowerCase();
            if (Arrays.asList(MimeTypeUtils.IMAGE_EXTENSION).contains(ext))
            {
                return ext;
            }
        }
        return "jpg";
    }

    private List<String> allowedSuffixes()
    {
        return Arrays.stream(imageHostSuffixes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
