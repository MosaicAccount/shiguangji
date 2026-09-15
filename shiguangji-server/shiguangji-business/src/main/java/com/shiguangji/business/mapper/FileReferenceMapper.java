package com.shiguangji.business.mapper;

import java.util.List;

/**
 * 文件引用 数据层
 *
 * 汇聚全部会引用上传文件的业务列，供孤儿文件清理对账使用。
 * 新增「引用上传文件」的业务列时必须同步扩展这里的查询，否则该列指向的文件会在下一轮
 * 清理中被当成孤儿误删。
 *
 * @author shiguangji
 */
public interface FileReferenceMapper
{
    /**
     * 查询被业务数据引用的全部上传文件路径（/profile 前缀）
     *
     * 刻意不过滤 del_flag：软删除的行是回收站，可被恢复，其文件必须保留，
     * 只有 purge 的物理删除才真正释放引用。
     *
     * 只取 /profile/ 开头的值，外部图片地址（如豆瓣图 URL）不是本站上传的文件，
     * 不参与对账。
     *
     * @return 被引用的文件路径集合
     */
    public List<String> selectReferencedStorageKeys();
}
