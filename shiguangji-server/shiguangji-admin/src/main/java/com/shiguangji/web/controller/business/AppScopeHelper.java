package com.shiguangji.web.controller.business;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.shiguangji.business.domain.SgjItem;
import com.shiguangji.business.domain.SgjNote;
import com.shiguangji.common.core.domain.model.LoginUser;
import com.shiguangji.common.utils.SecurityUtils;

/**
 * 前台数据可见范围解析（用户数据隔离）
 *
 * 博客模式下前台数据可见规则：
 * - 匿名访客：仅可见公开博主（publicOwner，默认 admin）的数据；
 * - 博主本人 / 管理员：不限制，可见全部数据；
 * - 其他登录用户：仅可见自己的数据（create_by = 当前用户名）。
 *
 * @author shiguangji
 */
@Component
public class AppScopeHelper
{
    /**
     * 前台博客公开数据所属博主账号
     */
    @Value("${shiguangji.public-owner:admin}")
    private String publicOwner;

    /**
     * 解析当前请求可见数据范围对应的 create_by 过滤值
     *
     * @return 返回 null 表示不限制（博主/管理员）；返回具体用户名表示仅可见该用户的数据
     */
    public String resolveCreateBy()
    {
        if (isAnonymous())
        {
            // 匿名访客：仅可见公开博主的数据
            return publicOwner;
        }
        String username = SecurityUtils.getUsername();
        if (username.equals(publicOwner) || SecurityUtils.isAdmin())
        {
            // 博主本人 / 管理员：全量可见
            return null;
        }
        // 其他登录用户：仅可见自己的数据
        return username;
    }

    /**
     * 当前请求是否为匿名访客（未登录）
     *
     * @return true 表示匿名访问
     */
    public boolean isAnonymous()
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof LoginUser);
    }

    /**
     * 访客响应脱敏：清空条目的私人字段（个人短评 comment、私人备注 remark）。
     * 仅对匿名访客生效，登录态返回内容不受影响。
     *
     * @param item 条目
     */
    public void maskItemForGuest(SgjItem item)
    {
        if (item != null && isAnonymous())
        {
            item.setComment(null);
            item.setRemark(null);
        }
    }

    /**
     * 批量版 {@link #maskItemForGuest(SgjItem)}
     *
     * @param items 条目集合
     */
    public void maskItemsForGuest(List<SgjItem> items)
    {
        if (items != null && isAnonymous())
        {
            for (SgjItem item : items)
            {
                item.setComment(null);
                item.setRemark(null);
            }
        }
    }

    /**
     * 访客响应脱敏：清空笔记的私人备注 remark（公开笔记正文本身即对访客开放）。
     * 仅对匿名访客生效，登录态返回内容不受影响。
     *
     * @param note 笔记
     */
    public void maskNoteForGuest(SgjNote note)
    {
        if (note != null && isAnonymous())
        {
            note.setRemark(null);
        }
    }

    /**
     * 批量版 {@link #maskNoteForGuest(SgjNote)}
     *
     * @param notes 笔记集合
     */
    public void maskNotesForGuest(List<SgjNote> notes)
    {
        if (notes != null && isAnonymous())
        {
            for (SgjNote note : notes)
            {
                note.setRemark(null);
            }
        }
    }

    /**
     * 判断当前登录用户是否有权操作某条数据（本人或管理员）
     *
     * @param createBy 数据创建者
     * @return true 表示有权操作
     */
    public boolean canOperate(String createBy)
    {
        if (SecurityUtils.isAdmin())
        {
            return true;
        }
        String username = SecurityUtils.getUsername();
        return createBy != null && !createBy.isEmpty() && createBy.equals(username);
    }
}
