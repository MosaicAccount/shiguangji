package com.shiguangji.quartz.task;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.shiguangji.business.mapper.FileReferenceMapper;
import com.shiguangji.common.constant.Constants;
import com.shiguangji.file.cleanup.OrphanFileReconciler;
import com.shiguangji.file.domain.SysFile;
import com.shiguangji.file.mapper.SysFileMapper;
import com.shiguangji.file.storage.FileStorageService;

/**
 * 孤儿文件清理任务（每周一次）
 *
 * 对存储中的对象与 sys_file 台账取并集作为候选，凡是没有被任何业务列引用、且已超过
 * 保护期的候选，删除其文件本体与台账记录。候选取并集是为了同时覆盖两类遗留：
 * 登记过但文件已被替换的台账记录，以及落在存储上但从未登记的封面文件。
 *
 * 引用集合来自 FileReferenceMapper，包含回收站（del_flag='2'）中的条目——它们可被恢复，
 * 其文件不算孤儿。
 *
 * 删除对失败是幂等的：文件删除失败只丢台账记录，下轮存储扫描仍会发现它；台账删除失败
 * 只留下记录，下轮也会被再清一次。
 *
 * @author shiguangji
 */
@Component("fileCleanupTask")
public class FileCleanupTask
{
    private static final Logger log = LoggerFactory.getLogger(FileCleanupTask.class);

    /** 保护期：无人引用不足该时长的文件一律保留，用于避开「文件已写入、业务行尚未保存」的竞态 */
    private static final Duration GRACE = Duration.ofDays(7);

    /**
     * 参与对账的归类目录
     *
     * 只覆盖上传链路写入的三个归类；download/import 等目录由其他机制管理，不纳入扫描，
     * 避免误删不受台账约束的文件。
     */
    private static final String[] CATEGORIES = { "upload", "avatar", "cover" };

    /** 触发比例兜底所需的最小候选数，小规模目录不参与比例判断 */
    private static final int SAFETY_MIN_CANDIDATES = 20;

    /** 允许单次回收的最大比例，超出即中止，防止引用查询异常导致清空图库 */
    private static final double SAFETY_MAX_RATIO = 0.5;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private SysFileMapper sysFileMapper;

    @Autowired
    private FileReferenceMapper fileReferenceMapper;

    /**
     * 回收无人引用的上传文件及其台账记录
     */
    public void cleanupOrphanFiles()
    {
        Instant now = Instant.now();

        Set<String> referenced = new HashSet<>(fileReferenceMapper.selectReferencedStorageKeys());
        Map<String, Instant> stored = listStored();
        Map<String, Instant> recorded = listRecorded();

        Set<String> candidates = new HashSet<>(stored.keySet());
        candidates.addAll(recorded.keySet());

        Set<String> orphans = OrphanFileReconciler.findOrphans(stored, recorded, referenced, now, GRACE);

        if (OrphanFileReconciler.exceedsSafetyRatio(orphans.size(), candidates.size(),
                SAFETY_MIN_CANDIDATES, SAFETY_MAX_RATIO))
        {
            log.error("孤儿文件清理已中止：待回收 {} 个 / 候选 {} 个，超过安全比例，疑似引用查询异常",
                    orphans.size(), candidates.size());
            return;
        }

        int deleted = 0;
        int failed = 0;
        for (String path : orphans)
        {
            try
            {
                fileStorageService.delete(path.substring(Constants.RESOURCE_PREFIX.length() + 1));
                deleted++;
            }
            catch (Exception e)
            {
                failed++;
                log.warn("删除孤儿文件失败：{}", path, e);
            }
        }
        int recordCount = orphans.isEmpty() ? 0
                : sysFileMapper.deleteFileRecordsByStorageKeys(new ArrayList<>(orphans));

        log.info("孤儿文件清理完成：候选 {}（存储 {} / 台账 {}），被引用 {}，回收文件 {}（失败 {}），清理台账 {} 条",
                candidates.size(), stored.size(), recorded.size(), referenced.size(), deleted, failed, recordCount);
    }

    /**
     * 列举存储中的对象，key 补齐 /profile 前缀以便与业务表、台账直接比对
     */
    private Map<String, Instant> listStored()
    {
        Map<String, Instant> stored = new LinkedHashMap<>();
        for (String category : CATEGORIES)
        {
            try
            {
                fileStorageService.list(category)
                        .forEach((key, time) -> stored.put(Constants.RESOURCE_PREFIX + "/" + key, time));
            }
            catch (Exception e)
            {
                // 列举失败若按空集处理会把整个存储判成孤儿，必须中止本轮
                throw new IllegalStateException("列举存储对象失败，已中止本次清理：" + category, e);
            }
        }
        return stored;
    }

    /**
     * 读取台账记录，storage_key 本身就是 /profile 前缀路径
     */
    private Map<String, Instant> listRecorded()
    {
        Map<String, Instant> recorded = new HashMap<>();
        List<SysFile> files = sysFileMapper.selectAllFileRecords();
        for (SysFile file : files)
        {
            recorded.put(file.getStorageKey(), toInstant(file.getCreateTime()));
        }
        return recorded;
    }

    private static Instant toInstant(Date date)
    {
        return date == null ? null : date.toInstant();
    }
}
