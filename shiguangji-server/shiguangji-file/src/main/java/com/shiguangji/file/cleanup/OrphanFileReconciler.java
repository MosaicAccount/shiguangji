package com.shiguangji.file.cleanup;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 孤儿文件对账（纯逻辑，不触碰存储与数据库）
 *
 * 候选 = 存储中的对象 ∪ 台账记录，这样既覆盖「登记过但文件已被替换」的记录，
 * 也覆盖「落在磁盘上但从未登记」的文件（如服务端抓取的封面）。
 * 任何一个来源判定为孤儿即可回收，两个来源互相兜底。
 *
 * 「最后可见时间」取存储最后修改时间与台账登记时间中较晚的一个：任一侧刚发生过
 * 写入都说明这个文件还新，宁可不删。
 *
 * @author shiguangji
 */
public final class OrphanFileReconciler
{
    private OrphanFileReconciler()
    {
    }

    /**
     * 对账出可回收的孤儿文件
     *
     * @param stored 存储中的对象：/profile 前缀路径 -> 最后修改时间
     * @param recorded 台账中的记录：/profile 前缀路径 -> 登记时间
     * @param referenced 被业务数据引用的路径集合（含回收站中的记录，它们可被恢复，必须保留）
     * @param now 当前时间
     * @param grace 保护期，未满保护期一律保留，用于避开「文件已写入、业务行尚未保存」的竞态
     * @return 可回收的 /profile 前缀路径集合
     */
    public static Set<String> findOrphans(Map<String, Instant> stored, Map<String, Instant> recorded,
            Set<String> referenced, Instant now, Duration grace)
    {
        Set<String> candidates = new LinkedHashSet<>(stored.keySet());
        candidates.addAll(recorded.keySet());

        Set<String> orphans = new LinkedHashSet<>();
        for (String path : candidates)
        {
            if (referenced.contains(path))
            {
                continue;
            }
            Instant lastSeen = laterOf(stored.get(path), recorded.get(path));
            if (lastSeen == null || lastSeen.plus(grace).isAfter(now))
            {
                // 时间未知（台账登记时间为空等）时保守跳过，宁可漏收不可误删
                continue;
            }
            orphans.add(path);
        }
        return orphans;
    }

    /**
     * 待回收比例是否超过安全阈值
     *
     * 引用集合一旦因为查询口径出错而返回空集，对账会把整个存储判成孤儿；候选量足够大时
     * 按比例兜底中止，避免一次误删清空图库。
     *
     * @param orphanCount 待回收数量
     * @param candidateCount 参与对账的候选总数
     * @param minCandidates 触发兜底所需的最小候选数，低于该数量不做比例判断
     * @param maxRatio 允许的最大待回收比例
     * @return true 表示超过阈值，应中止本次清理
     */
    public static boolean exceedsSafetyRatio(int orphanCount, int candidateCount, int minCandidates, double maxRatio)
    {
        if (candidateCount < minCandidates)
        {
            return false;
        }
        return orphanCount > candidateCount * maxRatio;
    }

    private static Instant laterOf(Instant a, Instant b)
    {
        if (a == null)
        {
            return b;
        }
        if (b == null)
        {
            return a;
        }
        return a.isAfter(b) ? a : b;
    }
}
