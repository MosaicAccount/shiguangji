package com.shiguangji.quartz.task;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.shiguangji.business.mapper.FileReferenceMapper;
import com.shiguangji.file.domain.SysFile;
import com.shiguangji.file.mapper.SysFileMapper;
import com.shiguangji.file.storage.FileStorageService;

/**
 * 孤儿文件清理任务编排单元测试（协作者全部打桩，不依赖 Spring 容器、数据库与真实存储）
 *
 * 重点覆盖两条防误删护栏：安全比例兜底、列举存储失败中止。
 */
class FileCleanupTaskTest
{
    private static final Instant OLD = Instant.now().minus(Duration.ofDays(30));

    private static final String ORPHAN_KEY = "upload/2026/08/01/orphan_1.jpg";

    private static final String ORPHAN_PATH = "/profile/" + ORPHAN_KEY;

    private FileStorageService fileStorageService;

    private SysFileMapper sysFileMapper;

    private FileReferenceMapper fileReferenceMapper;

    private FileCleanupTask task;

    @BeforeEach
    void setUp() throws Exception
    {
        fileStorageService = mock(FileStorageService.class);
        sysFileMapper = mock(SysFileMapper.class);
        fileReferenceMapper = mock(FileReferenceMapper.class);

        task = new FileCleanupTask();
        ReflectionTestUtils.setField(task, "fileStorageService", fileStorageService);
        ReflectionTestUtils.setField(task, "sysFileMapper", sysFileMapper);
        ReflectionTestUtils.setField(task, "fileReferenceMapper", fileReferenceMapper);

        when(fileStorageService.list("upload")).thenReturn(Map.of());
        when(fileStorageService.list("avatar")).thenReturn(Map.of());
        when(fileStorageService.list("cover")).thenReturn(Map.of());
        when(fileReferenceMapper.selectReferencedStorageKeys()).thenReturn(List.of());
        when(sysFileMapper.selectAllFileRecords()).thenReturn(List.of());
    }

    @Test
    void deletesUnreferencedFileAndItsLedgerRecord() throws Exception
    {
        when(fileStorageService.list("upload")).thenReturn(Map.of(ORPHAN_KEY, OLD));
        when(sysFileMapper.selectAllFileRecords()).thenReturn(List.of(record(ORPHAN_PATH)));

        task.cleanupOrphanFiles();

        verify(fileStorageService).delete(ORPHAN_KEY);
        verify(sysFileMapper).deleteFileRecordsByStorageKeys(List.of(ORPHAN_PATH));
    }

    @Test
    void keepsFileReferencedByBusinessRow() throws Exception
    {
        when(fileStorageService.list("upload")).thenReturn(Map.of(ORPHAN_KEY, OLD));
        when(fileReferenceMapper.selectReferencedStorageKeys()).thenReturn(List.of(ORPHAN_PATH));

        task.cleanupOrphanFiles();

        verify(fileStorageService, never()).delete(anyString());
        verify(sysFileMapper, never()).deleteFileRecordsByStorageKeys(anyList());
    }

    @Test
    void keepsFileWithinGracePeriod() throws Exception
    {
        // 刚上传、业务行尚未保存的竞态窗口
        when(fileStorageService.list("upload")).thenReturn(Map.of(ORPHAN_KEY, Instant.now()));

        task.cleanupOrphanFiles();

        verify(fileStorageService, never()).delete(anyString());
    }

    @Test
    void abortsWithoutDeletingWhenOrphanRatioExceedsSafetyThreshold() throws Exception
    {
        Map<String, Instant> stored = new HashMap<>();
        for (int i = 0; i < 100; i++)
        {
            stored.put("upload/2026/08/01/f_" + i + ".jpg", OLD);
        }
        when(fileStorageService.list("upload")).thenReturn(stored);

        task.cleanupOrphanFiles();

        // 全部候选都是孤儿说明引用查询很可能失效，必须一个都不删
        verify(fileStorageService, never()).delete(anyString());
        verify(sysFileMapper, never()).deleteFileRecordsByStorageKeys(anyList());
    }

    @Test
    void abortsWithoutDeletingWhenListingStorageFails() throws Exception
    {
        when(fileStorageService.list("upload")).thenThrow(new IOException("存储不可读"));

        assertThrows(IllegalStateException.class, () -> task.cleanupOrphanFiles());

        // 列举失败按空集处理会把整个存储判成孤儿，必须中止
        verify(fileStorageService, never()).delete(anyString());
        verify(sysFileMapper, never()).deleteFileRecordsByStorageKeys(anyList());
    }

    @Test
    void keepsGoingWhenSingleFileDeletionFails() throws Exception
    {
        String otherKey = "upload/2026/08/01/orphan_2.jpg";
        when(fileStorageService.list("upload")).thenReturn(Map.of(ORPHAN_KEY, OLD, otherKey, OLD));
        doThrow(new IOException("文件被占用")).when(fileStorageService).delete(ORPHAN_KEY);

        task.cleanupOrphanFiles();

        // 单个文件删除失败不应中断整轮
        verify(fileStorageService).delete(otherKey);
    }

    private static SysFile record(String storageKey)
    {
        SysFile file = new SysFile();
        file.setStorageKey(storageKey);
        file.setCreateTime(Date.from(OLD));
        return file;
    }
}
