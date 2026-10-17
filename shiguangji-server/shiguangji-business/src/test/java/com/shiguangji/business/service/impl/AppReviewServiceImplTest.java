package com.shiguangji.business.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 标签拆分聚合单测（不依赖数据库，钉住空串 / 空格 / 归并 / 排序口径）。
 *
 * @author shiguangji
 */
class AppReviewServiceImplTest
{
    @Test
    void aggregatesTrimsMergesAndSorts()
    {
        List<Map<String, Object>> tags = AppReviewServiceImpl.aggregateTags(
                Arrays.asList("人文, 美食,,人文", "美食", " 自然 ", "", null));

        assertThat(tags).extracting(t -> (String) t.get("name")).containsExactly("人文", "美食", "自然");
        assertThat(tags.get(0).get("count")).isEqualTo(2L);
        assertThat(tags.get(1).get("count")).isEqualTo(2L);
        assertThat(tags.get(2).get("count")).isEqualTo(1L);
    }

    @Test
    void sameCountSortedByName()
    {
        List<Map<String, Object>> tags = AppReviewServiceImpl.aggregateTags(
                Arrays.asList("b", "a", "c"));

        // 同 count=1，按名字字典序
        assertThat(tags).extracting(t -> (String) t.get("name")).containsExactly("a", "b", "c");
    }

    @Test
    void emptyInputGivesEmptyList()
    {
        assertThat(AppReviewServiceImpl.aggregateTags(Collections.emptyList())).isEmpty();
        assertThat(AppReviewServiceImpl.aggregateTags(Arrays.asList((String) null))).isEmpty();
    }
}
