package com.shiguangji.business.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.shiguangji.business.domain.SgjItem;
import com.shiguangji.common.exception.ServiceException;

/**
 * 条目照片串解析与校验单元测试（不依赖 Spring 容器与数据库）
 */
class SgjItemPhotoTest
{
    @Test
    void parseSplitsTrimsAndSkipsEmptySegments()
    {
        List<String> urls = SgjItemServiceImpl.parsePhotoUrls("/profile/upload/a.png, /profile/upload/b.png,,");
        assertEquals(List.of("/profile/upload/a.png", "/profile/upload/b.png"), urls);
    }

    @Test
    void parseReturnsEmptyListForNullAndBlank()
    {
        assertTrue(SgjItemServiceImpl.parsePhotoUrls(null).isEmpty());
        assertTrue(SgjItemServiceImpl.parsePhotoUrls("").isEmpty());
        assertTrue(SgjItemServiceImpl.parsePhotoUrls(" , ").isEmpty());
    }

    @Test
    void validateRejectsOverlongPhotoUrl()
    {
        SgjItem item = new SgjItem();
        item.setItemType("PLACE");
        item.setTitle("测试地点");
        item.setPhotos("/profile/upload/" + "a".repeat(500) + ".png");
        ServiceException ex = assertThrows(ServiceException.class, () -> new SgjItemServiceImpl().validateItem(item, true));
        assertTrue(ex.getMessage().contains("照片地址长度"));
    }
}
