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

    /**
     * 查询正文里可能含 /profile 引用的文本（笔记 + 草稿）
     *
     * <p>笔记正文不是「一列一个文件」，而是「一段长文本里嵌着若干条路径」，所以只能把可能命中的
     * 文本取回来，交给 {@code ProfileRefExtractor} 以候选集为字典抽键——不能直接 select 出路径。</p>
     *
     * <p>刻意不过滤 del_flag：软删除的笔记是回收站，可被恢复，其图必须保留。
     * 草稿（{@code sgj_note_draft}）**没有过期机制**，一篇编辑了多日还没保存的笔记，
     * 其图只能靠草稿正文保住。</p>
     *
     * <p>调用方（{@code FileCleanupTask}）必须把本查询的失败与 null 结果都当作「本轮中止」，
     * 不能按空集处理——那会把正文引用的图全部误删。</p>
     *
     * @return 含 /profile/ 字面量的正文
     */
    public List<String> selectContentsWithProfileRefs();
}
