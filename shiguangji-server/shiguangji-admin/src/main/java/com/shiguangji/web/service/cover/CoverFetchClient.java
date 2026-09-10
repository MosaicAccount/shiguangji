package com.shiguangji.web.service.cover;

import java.util.List;

/**
 * 封面数据源客户端（issue #5）
 *
 * 抽象「按关键词搜索封面候选」与「按地址抓取图片」两个动作，
 * 便于测试以桩实现替换真实外呼（豆瓣）
 *
 * @author shiguangji
 */
public interface CoverFetchClient
{
    /**
     * 按关键词搜索封面候选
     *
     * @param itemType 条目类型（MOVIE/TV/BOOK）
     * @param keyword 标题关键词
     * @return 候选列表（imageUrl 为来源站原始地址），无结果返回空列表
     */
    List<Candidate> search(String itemType, String keyword);

    /**
     * 抓取图片内容
     *
     * @param imageUrl 图片地址（调用方需先做域名白名单校验）
     */
    Image fetch(String imageUrl);

    /** 封面候选 */
    record Candidate(String sourceId, String title, String year, String imageUrl)
    {
    }

    /** 抓取到的图片内容 */
    record Image(byte[] bytes, String contentType)
    {
    }
}
