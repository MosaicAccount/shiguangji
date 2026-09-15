package com.shiguangji.web.service.cover;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.shiguangji.business.domain.SgjItem;

/**
 * 封面服务纯逻辑单测（issue #5）：域名白名单、豆瓣响应解析、候选年份匹配。
 *
 * <p>不依赖 Spring 上下文与外呼，被测方法均为静态或无状态方法。</p>
 *
 * @author shiguangji
 */
class ItemCoverServiceTest
{
    private static final List<String> SUFFIXES = List.of("doubanio.com");

    // ------------------------------------------------------------------
    // 域名白名单（SSRF 防护）
    // ------------------------------------------------------------------

    @Test
    void whitelistAllowsDoubanCdnAndSubdomains()
    {
        assertThat(ItemCoverService.isAllowedImageHost("https://img1.doubanio.com/view/photo/s_ratio_poster/public/p1.jpg", SUFFIXES)).isTrue();
        assertThat(ItemCoverService.isAllowedImageHost("http://doubanio.com/a.jpg", SUFFIXES)).isTrue();
    }

    @Test
    void whitelistRejectsNonHttpAndForeignHosts()
    {
        // 非 http(s)
        assertThat(ItemCoverService.isAllowedImageHost("ftp://doubanio.com/a.jpg", SUFFIXES)).isFalse();
        // 内网地址
        assertThat(ItemCoverService.isAllowedImageHost("http://127.0.0.1:16379/", SUFFIXES)).isFalse();
        assertThat(ItemCoverService.isAllowedImageHost("http://localhost/a.jpg", SUFFIXES)).isFalse();
        // 伪装后缀在路径里
        assertThat(ItemCoverService.isAllowedImageHost("https://evil.com/doubanio.com/a.jpg", SUFFIXES)).isFalse();
        // 子域名伪造后缀（doubanio.com.evil.com 不以 .doubanio.com 结尾）
        assertThat(ItemCoverService.isAllowedImageHost("https://doubanio.com.evil.com/a.jpg", SUFFIXES)).isFalse();
        // userinfo 混淆：@ 后才是真实主机
        assertThat(ItemCoverService.isAllowedImageHost("http://doubanio.com@evil.com/a.jpg", SUFFIXES)).isFalse();
        // 空 / 无主机
        assertThat(ItemCoverService.isAllowedImageHost(null, SUFFIXES)).isFalse();
        assertThat(ItemCoverService.isAllowedImageHost("", SUFFIXES)).isFalse();
        assertThat(ItemCoverService.isAllowedImageHost("http:///a.jpg", SUFFIXES)).isFalse();
    }

    // ------------------------------------------------------------------
    // 豆瓣 suggest 响应解析
    // ------------------------------------------------------------------

    @Test
    void parseCandidatesExtractsIdTitleYearImg()
    {
        DoubanCoverClient client = new DoubanCoverClient();
        String body = "[{\"id\":\"111\",\"title\":\"肖申克的救赎\",\"year\":\"1994\","
                + "\"img\":\"https://img1.doubanio.com/view/photo/s_ratio_poster/public/p1.jpg\"},"
                // 书籍接口的图片字段为 pic（而非 img）
                + "{\"id\":\"333\",\"title\":\"三体\",\"year\":\"2008\","
                + "\"pic\":\"https://img1.doubanio.com/view/subject/s/public/s1.jpg\"},"
                // 缺 img/pic 的条目应跳过
                + "{\"id\":\"222\",\"title\":\"无图条目\",\"year\":\"2000\"}]";
        List<CoverFetchClient.Candidate> candidates = client.parseCandidates(body);
        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).sourceId()).isEqualTo("111");
        assertThat(candidates.get(0).title()).isEqualTo("肖申克的救赎");
        assertThat(candidates.get(0).year()).isEqualTo("1994");
        assertThat(candidates.get(1).sourceId()).isEqualTo("333");
        assertThat(candidates.get(1).imageUrl()).contains("/view/subject/s/");
    }

    @Test
    void parseCandidatesToleratesEmptyAndInvalidBody()
    {
        DoubanCoverClient client = new DoubanCoverClient();
        assertThat(client.parseCandidates("")).isEmpty();
        assertThat(client.parseCandidates("[]")).isEmpty();
        // 非数组响应（接口变更/风控页）不抛异常返回空，由上层提示无候选
        assertThat(client.parseCandidates("{\"msg\":\"blocked\"}")).isEmpty();
    }

    // ------------------------------------------------------------------
    // 候选年份匹配
    // ------------------------------------------------------------------

    @Test
    void pickCandidatePrefersYearMatchAndSkipsConflict()
    {
        ItemCoverService service = new ItemCoverService();
        SgjItem item = new SgjItem();
        item.setReleaseYear(1994);

        List<CoverFetchClient.Candidate> candidates = List.of(
                new CoverFetchClient.Candidate("1", "同名电影", "2001", "https://img1.doubanio.com/a.jpg", "https://img1.doubanio.com/a.jpg"),
                new CoverFetchClient.Candidate("2", "肖申克的救赎", "1994", "https://img1.doubanio.com/b.jpg", "https://img1.doubanio.com/b.jpg"));

        CoverFetchClient.Candidate pick = service.pickCandidate(candidates, item);
        assertThat(pick.sourceId()).isEqualTo("2");
    }

    @Test
    void pickCandidateAcceptsFirstWhenYearMissing()
    {
        ItemCoverService service = new ItemCoverService();
        // 条目与候选都无年份 → 接受首个
        SgjItem item = new SgjItem();
        List<CoverFetchClient.Candidate> candidates = List.of(
                new CoverFetchClient.Candidate("1", "某书", "", "https://img1.doubanio.com/a.jpg", "https://img1.doubanio.com/a.jpg"));
        assertThat(service.pickCandidate(candidates, item).sourceId()).isEqualTo("1");

        // 条目有年份、候选无年份 → 无冲突，接受
        item.setReleaseYear(1994);
        assertThat(service.pickCandidate(candidates, item).sourceId()).isEqualTo("1");

        // 候选年份与条目年份冲突 → 返回 null 计入 skipped
        List<CoverFetchClient.Candidate> conflicting = List.of(
                new CoverFetchClient.Candidate("1", "同名作品", "1994", "https://img1.doubanio.com/a.jpg", "https://img1.doubanio.com/a.jpg"));
        SgjItem tvItem = new SgjItem();
        tvItem.setStartYear(2020);
        assertThat(service.pickCandidate(conflicting, tvItem)).isNull();
    }
}
