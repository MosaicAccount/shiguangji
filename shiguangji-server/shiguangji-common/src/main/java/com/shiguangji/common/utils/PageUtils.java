package com.shiguangji.common.utils;

import com.github.pagehelper.PageHelper;
import com.shiguangji.common.core.page.PageDomain;
import com.shiguangji.common.core.page.TableSupport;
import com.shiguangji.common.utils.sql.SqlUtil;

/**
 * 分页工具类
 * 
 * @author shiguangji
 */
public class PageUtils extends PageHelper
{
    /** 每页条数上限（QA修复：防止通过超大 pageSize 一次性拉取全量数据） */
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * 设置请求分页数据
     */
    public static void startPage()
    {
        PageDomain pageDomain = TableSupport.buildPageRequest();
        Integer pageNum = pageDomain.getPageNum();
        Integer pageSize = pageDomain.getPageSize();
        // 每页条数超过上限时截断为上限值
        if (pageSize != null && pageSize > MAX_PAGE_SIZE)
        {
            pageSize = MAX_PAGE_SIZE;
        }
        String orderBy = SqlUtil.escapeOrderBySql(pageDomain.getOrderBy());
        Boolean reasonable = pageDomain.getReasonable();
        PageHelper.startPage(pageNum, pageSize, orderBy).setReasonable(reasonable);
    }

    /**
     * 清理分页的线程变量
     */
    public static void clearPage()
    {
        PageHelper.clearPage();
    }
}
