package com.shiguangji.file.cleanup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * 孤儿文件对账逻辑单元测试（不依赖 Spring 容器、数据库与真实存储）
 */
class OrphanFileReconcilerTest
{
    private static final Instant NOW = Instant.parse("2026-09-10T00:00:00Z");

    private static final Duration GRACE = Duration.ofDays(7);

    /** 30 天前写入，已过保护期 */
    private static final Instant OLD = NOW.minus(Duration.ofDays(30));

    /** 1 天前写入，保护期内 */
    private static final Instant FRESH = NOW.minus(Duration.ofDays(1));

    private static final String ORPHAN = "/profile/upload/2026/08/01/orphan_1.jpg";

    private static final String KEPT = "/profile/upload/2026/08/01/kept_1.jpg";

    @Test
    void reclaimsFileThatNoBusinessRowReferences()
    {
        // 落在存储但从未登记台账的文件（如服务端抓取的封面）也要纳入回收范围
        Set<String> orphans = OrphanFileReconciler.findOrphans(
                Map.of(ORPHAN, OLD), Map.of(), Set.of(), NOW, GRACE);

        assertEquals(Set.of(ORPHAN), orphans);
    }

    @Test
    void keepsFileReferencedByBusinessRow()
    {
        Set<String> orphans = OrphanFileReconciler.findOrphans(
                Map.of(ORPHAN, OLD), Map.of(ORPHAN, OLD), Set.of(ORPHAN), NOW, GRACE);

        assertTrue(orphans.isEmpty());
    }

    @Test
    void keepsFileReferencedOnlyByRecycleBinRow()
    {
        // 回收站条目的 del_flag='2'，恢复后仍需展示封面，因此引用集合包含它
        Set<String> referenced = Set.of("/profile/upload/2026/08/01/recycled_1.jpg");

        Set<String> orphans = OrphanFileReconciler.findOrphans(
                Map.of("/profile/upload/2026/08/01/recycled_1.jpg", OLD), Map.of(), referenced, NOW, GRACE);

        assertTrue(orphans.isEmpty());
    }

    @Test
    void keepsUnreferencedFileWithinGracePeriod()
    {
        // 刚上传、业务行尚未保存的竞态窗口
        Set<String> orphans = OrphanFileReconciler.findOrphans(
                Map.of(ORPHAN, FRESH), Map.of(), Set.of(), NOW, GRACE);

        assertTrue(orphans.isEmpty());
    }

    @Test
    void reclaimsRecordWhoseFileIsGoneFromStorage()
    {
        // 文件已被替换删除，台账记录残留
        Set<String> orphans = OrphanFileReconciler.findOrphans(
                Map.of(), Map.of(ORPHAN, OLD), Set.of(), NOW, GRACE);

        assertEquals(Set.of(ORPHAN), orphans);
    }

    @Test
    void keepsFileWhenLedgerRegistrationIsNewerThanStorageWrite()
    {
        // 存储侧时间旧、台账登记时间新，取较晚者仍在保护期内
        Set<String> orphans = OrphanFileReconciler.findOrphans(
                Map.of(ORPHAN, OLD), Map.of(ORPHAN, FRESH), Set.of(), NOW, GRACE);

        assertTrue(orphans.isEmpty());
    }

    @Test
    void keepsFileWhoseTimestampIsUnknown()
    {
        Map<String, Instant> recordedWithNullTime = new HashMap<>();
        recordedWithNullTime.put(ORPHAN, null);

        Set<String> orphans = OrphanFileReconciler.findOrphans(
                Map.of(), recordedWithNullTime, Set.of(), NOW, GRACE);

        assertTrue(orphans.isEmpty());
    }

    @Test
    void reclaimsFileOnceGracePeriodIsExactlyMet()
    {
        Set<String> orphans = OrphanFileReconciler.findOrphans(
                Map.of(ORPHAN, NOW.minus(GRACE)), Map.of(), Set.of(), NOW, GRACE);

        assertEquals(Set.of(ORPHAN), orphans);
    }

    @Test
    void abortsWhenOrphanRatioExceedsThreshold()
    {
        assertTrue(OrphanFileReconciler.exceedsSafetyRatio(60, 100, 20, 0.5));
    }

    @Test
    void proceedsWhenOrphanRatioIsWithinThreshold()
    {
        assertFalse(OrphanFileReconciler.exceedsSafetyRatio(40, 100, 20, 0.5));
    }

    @Test
    void skipsRatioCheckWhenCandidateCountIsSmall()
    {
        // 小规模目录全部无人引用是正常现象，不应触发兜底
        assertFalse(OrphanFileReconciler.exceedsSafetyRatio(10, 10, 20, 0.5));
    }

    @Test
    void ignoresEmptyInputs()
    {
        Set<String> orphans = OrphanFileReconciler.findOrphans(Map.of(), Map.of(), Set.of(), NOW, GRACE);

        assertTrue(orphans.isEmpty());
    }
}
