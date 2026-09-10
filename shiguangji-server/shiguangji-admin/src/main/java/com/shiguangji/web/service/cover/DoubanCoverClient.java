package com.shiguangji.web.service.cover;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiguangji.common.exception.ServiceException;
import com.shiguangji.common.utils.StringUtils;

/**
 * 豆瓣封面客户端（issue #5）
 *
 * <p>使用豆瓣页面自用的 suggest 接口（非官方、无鉴权），影视与书籍各一个入口；
 * 接口地址可在 application.yml cover.douban 下覆盖，接口变更时无需改代码。</p>
 *
 * <p>请求需携带浏览器 UA，豆瓣会拒绝默认的 Java 客户端标识。</p>
 *
 * @author shiguangji
 */
@Service
public class DoubanCoverClient implements CoverFetchClient
{
    private static final Logger log = LoggerFactory.getLogger(DoubanCoverClient.class);

    @Value("${cover.douban.movie-suggest-url:https://movie.douban.com/j/subject_suggest}")
    private String movieSuggestUrl;

    @Value("${cover.douban.book-suggest-url:https://book.douban.com/j/subject_suggest}")
    private String bookSuggestUrl;

    /** 抓取豆瓣 CDN 图片必须携带的 Referer（缺省返回 418 反爬响应） */
    @Value("${cover.douban.referer:https://www.douban.com/}")
    private String referer;

    @Value("${cover.douban.user-agent:Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36}")
    private String userAgent;

    /** 外呼超时（毫秒），测试可调小 */
    @Value("${cover.douban.timeout-ms:5000}")
    private long timeoutMs;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public List<Candidate> search(String itemType, String keyword)
    {
        String endpoint = "BOOK".equals(itemType) ? bookSuggestUrl : movieSuggestUrl;
        String url = endpoint + "?q=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        String body = httpGet(url);
        return parseCandidates(body);
    }

    @Override
    public Image fetch(String imageUrl)
    {
        try
        {
            HttpResponse<byte[]> response = httpClient.send(request(imageUrl).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200)
            {
                throw new ServiceException(StringUtils.format("封面图片获取失败（HTTP {}）：{}", response.statusCode(), imageUrl));
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("image/jpeg");
            return new Image(response.body(), contentType);
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            log.warn("封面图片抓取异常：{}", imageUrl, e);
            throw new ServiceException("封面图片获取失败：" + e.getMessage());
        }
    }

    /** 解析 suggest 响应：数组元素含 id/title/year/img，缺 id 或 img 的条目直接跳过 */
    List<Candidate> parseCandidates(String body)
    {
        List<Candidate> candidates = new ArrayList<>();
        if (StringUtils.isEmpty(body))
        {
            return candidates;
        }
        try
        {
            JsonNode array = objectMapper.readTree(body);
            if (!array.isArray())
            {
                return candidates;
            }
            for (JsonNode node : array)
            {
                String id = node.path("id").asText(null);
                // 影视字段为 img，书籍字段为 pic
                String img = node.path("img").asText(node.path("pic").asText(null));
                if (id == null || id.isEmpty() || img == null || img.isEmpty())
                {
                    continue;
                }
                candidates.add(new Candidate(id, node.path("title").asText(""), node.path("year").asText(""), img));
            }
        }
        catch (Exception e)
        {
            log.warn("豆瓣搜索响应解析失败：{}", body, e);
            throw new ServiceException("豆瓣搜索响应解析失败，接口可能已变更");
        }
        return candidates;
    }

    private String httpGet(String url)
    {
        try
        {
            HttpResponse<String> response = httpClient.send(request(url).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200)
            {
                throw new ServiceException(StringUtils.format("豆瓣搜索失败（HTTP {}），接口可能不可用", response.statusCode()));
            }
            return response.body();
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            log.warn("豆瓣搜索请求异常：{}", url, e);
            throw new ServiceException("豆瓣搜索失败：" + e.getMessage());
        }
    }

    private HttpRequest.Builder request(String url)
    {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("User-Agent", userAgent)
                .header("Referer", referer)
                .header("Accept", "*/*");
    }
}
