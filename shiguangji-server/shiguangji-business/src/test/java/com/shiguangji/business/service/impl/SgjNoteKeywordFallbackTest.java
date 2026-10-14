package com.shiguangji.business.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.github.pagehelper.PageHelper;
import com.shiguangji.business.domain.SgjNote;
import com.shiguangji.business.mapper.SgjNoteMapper;

/**
 * 关键词全文检索 0 命中兜底单测（issue #66，不依赖 Spring 容器与数据库）。
 *
 * <p>兜底逻辑：带关键词且全文检索 0 命中时换 like 条件重查（复用同一套可见范围过滤），
 * 并按同一请求参数重建分页。这里用桩 mapper 钉死「何时兜底 / 何时不兜底 / 兜底行是否
 * 走完前台摘要加工」；like 分支 SQL 的真库执行由冒烟测试的 0 命中用例覆盖。</p>
 */
class SgjNoteKeywordFallbackTest
{
    private SgjNoteMapper mapper;

    private SgjNoteServiceImpl service;

    @BeforeEach
    void setUp()
    {
        mapper = mock(SgjNoteMapper.class);
        service = new SgjNoteServiceImpl();
        ReflectionTestUtils.setField(service, "sgjNoteMapper", mapper);
        // 控制器先 startPage() 再进服务，这里还原同样的请求上下文
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown()
    {
        // 桩 mapper 不消费 PageHelper 的线程变量，兜底重建的分页会残留，必须清掉防串测
        PageHelper.clearPage();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void emptyFullTextHitWithKeywordFallsBackToLike()
    {
        SgjNote query = new SgjNote();
        query.setKeyword("琥珀");
        SgjNote hit = new SgjNote();
        hit.setNoteId(1L);
        hit.setTitle("琥珀笔记");
        when(mapper.selectSgjNoteList(any()))
                .thenReturn(List.of())
                .thenReturn(List.of(hit));

        List<SgjNote> result = service.selectSgjNoteList(query);

        assertThat(result).hasSize(1);
        verify(mapper, times(2)).selectSgjNoteList(same(query));
        // 第二次查询带 like 复核标记，且分页按同一请求参数重建过
        assertThat(query.getParams()).containsEntry("keywordLike", Boolean.TRUE);
        assertThat(PageHelper.getLocalPage()).isNotNull();
    }

    @Test
    void emptyResultWithoutKeywordSkipsFallback()
    {
        SgjNote query = new SgjNote();
        when(mapper.selectSgjNoteList(any())).thenReturn(List.of());

        assertThat(service.selectSgjNoteList(query)).isEmpty();
        verify(mapper, times(1)).selectSgjNoteList(same(query));
        assertThat(query.getParams()).doesNotContainKey("keywordLike");
    }

    @Test
    void nonEmptyFullTextHitSkipsFallback()
    {
        SgjNote query = new SgjNote();
        query.setKeyword("琥珀");
        when(mapper.selectSgjNoteList(any()))
                .thenReturn(List.of(new SgjNote()));

        assertThat(service.selectSgjNoteList(query)).hasSize(1);
        verify(mapper, times(1)).selectSgjNoteList(same(query));
        assertThat(query.getParams()).doesNotContainKey("keywordLike");
    }

    @Test
    void frontListFallbackRowsStillGetExcerptAndContentMasked()
    {
        SgjNote query = new SgjNote();
        query.setKeyword("琥珀");
        SgjNote hit = new SgjNote();
        hit.setNoteId(1L);
        hit.setTitle("琥珀笔记");
        hit.setContent("正文里藏着琥珀关键词，应当被截进摘要");
        when(mapper.selectSgjNoteFrontList(any()))
                .thenReturn(List.of())
                .thenReturn(List.of(hit));

        List<SgjNote> result = service.selectSgjNoteFrontList(query);

        assertThat(result).hasSize(1);
        // 兜底命中的行照常走摘要加工：excerpt 以命中处为中心，content 不下发
        assertThat(result.get(0).getExcerpt()).contains("琥珀");
        assertThat(result.get(0).getContent()).isNull();
        verify(mapper, times(2)).selectSgjNoteFrontList(same(query));
    }
}
