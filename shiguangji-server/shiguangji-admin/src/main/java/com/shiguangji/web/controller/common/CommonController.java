package com.shiguangji.web.controller.common;

import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.shiguangji.common.annotation.RateLimiter;
import com.shiguangji.common.config.ShiGuangJiConfig;
import com.shiguangji.common.core.domain.AjaxResult;
import com.shiguangji.common.enums.LimitType;
import com.shiguangji.common.utils.SecurityUtils;
import com.shiguangji.common.utils.StringUtils;
import com.shiguangji.common.utils.file.FileUtils;
import com.shiguangji.common.utils.file.MimeTypeUtils;
import com.shiguangji.file.domain.SysFile;
import com.shiguangji.file.storage.FileStorageService;
import com.shiguangji.file.service.ISysFileService;
import com.shiguangji.framework.config.ServerConfig;
import com.shiguangji.web.service.image.ExternalImageTransferService;
import com.shiguangji.web.service.image.FetchImageRequest;

/**
 * 通用请求处理
 * 
 * @author shiguangji
 */
@RestController
@RequestMapping("/common")
public class CommonController
{
    private static final Logger log = LoggerFactory.getLogger(CommonController.class);

    @Autowired
    private ServerConfig serverConfig;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private ISysFileService sysFileService;

    @Autowired
    private ExternalImageTransferService externalImageTransferService;

    private static final String FILE_DELIMITER = ",";

    /**
     * 通用下载请求
     * 
     * @param fileName 文件名称
     * @param delete 是否删除
     */
    @GetMapping("/download")
    public void fileDownload(String fileName, Boolean delete, HttpServletResponse response, HttpServletRequest request)
    {
        try
        {
            if (!FileUtils.checkAllowDownload(fileName))
            {
                throw new Exception(StringUtils.format("文件名称({})非法，不允许下载。 ", fileName));
            }
            String realFileName = System.currentTimeMillis() + fileName.substring(fileName.indexOf("_") + 1);
            String filePath = ShiGuangJiConfig.getDownloadPath() + fileName;

            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            FileUtils.setAttachmentResponseHeader(response, realFileName);
            FileUtils.writeBytes(filePath, response.getOutputStream());
            if (delete)
            {
                FileUtils.deleteFile(filePath);
            }
        }
        catch (Exception e)
        {
            log.error("下载文件失败", e);
        }
    }

    /**
     * 通用上传请求（单个）
     */
    @PostMapping("/upload")
    public AjaxResult uploadFile(MultipartFile file) throws Exception
    {
        try
        {
            // 上传并返回 /profile 前缀的相对路径
            String fileName = fileStorageService.upload("upload", file, MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION, false);
            recordSysFile(file, fileName);
            String url = serverConfig.getUrl() + fileName;
            AjaxResult ajax = AjaxResult.success();
            ajax.put("url", url);
            ajax.put("fileName", fileName);
            ajax.put("newFileName", FileUtils.getName(fileName));
            ajax.put("originalFilename", file.getOriginalFilename());
            return ajax;
        }
        catch (Exception e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 通用上传请求（多个）
     */
    @PostMapping("/uploads")
    public AjaxResult uploadFiles(List<MultipartFile> files) throws Exception
    {
        try
        {
            List<String> urls = new ArrayList<String>();
            List<String> fileNames = new ArrayList<String>();
            List<String> newFileNames = new ArrayList<String>();
            List<String> originalFilenames = new ArrayList<String>();
            for (MultipartFile file : files)
            {
                // 上传并返回 /profile 前缀的相对路径
                String fileName = fileStorageService.upload("upload", file, MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION, false);
                recordSysFile(file, fileName);
                String url = serverConfig.getUrl() + fileName;
                urls.add(url);
                fileNames.add(fileName);
                newFileNames.add(FileUtils.getName(fileName));
                originalFilenames.add(file.getOriginalFilename());
            }
            AjaxResult ajax = AjaxResult.success();
            ajax.put("urls", StringUtils.join(urls, FILE_DELIMITER));
            ajax.put("fileNames", StringUtils.join(fileNames, FILE_DELIMITER));
            ajax.put("newFileNames", StringUtils.join(newFileNames, FILE_DELIMITER));
            ajax.put("originalFilenames", StringUtils.join(originalFilenames, FILE_DELIMITER));
            return ajax;
        }
        catch (Exception e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 笔记正文插图上传（0.0.2 FR-002-9，issue #56）
     *
     * <p>与 {@link #uploadFile} 有三处刻意的差别：只收图片（额外放行 webp）、**uuid 命名**、只回站内相对路径。
     * uuid 命名是关键——笔记正文里保存的是这条路径，正文引用必须落在安全字符集内，
     * 孤儿清理的引用抽取（ProfileRefExtractor）与前端改写才无歧义。</p>
     *
     * <p>需登录（与 /common/upload 一致，本类不加 @Anonymous）。返回 {@code {path: /profile/upload/...}}，
     * 前端用它改写正文，不要用绝对 URL（换端口 / dev↔prod 即失效）。</p>
     */
    @PostMapping("/upload/note-image")
    public AjaxResult uploadNoteImage(MultipartFile file) throws Exception
    {
        try
        {
            String path = fileStorageService.upload("upload", file, MimeTypeUtils.NOTE_IMAGE_EXTENSION, true);
            recordSysFile(file, path);
            AjaxResult ajax = AjaxResult.success();
            ajax.put("path", path);
            return ajax;
        }
        catch (Exception e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 转存外链图片（0.0.6 FR-006-1，issue #68）：服务端抓取 → 落 upload 分类（uuid 命名）→ 回站内路径
     *
     * <p>需登录；IP 维度限流 30 次/分钟（@RateLimiter，Redis 固定窗口）。抓取 / 校验失败不是系统错误，
     * 走业务码 500 + 可读 msg，前端逐条展示（结论 69）。台账的 fileName 记外链原始地址，便于溯源。</p>
     */
    @RateLimiter(time = 60, count = 30, limitType = LimitType.IP)
    @PostMapping("/upload/fetch-image")
    public AjaxResult fetchImage(@RequestBody FetchImageRequest request) throws Exception
    {
        try
        {
            ExternalImageTransferService.TransferResult result = externalImageTransferService.transfer(request.url());
            recordSysFile(result.file(), result.path(), request.url());
            AjaxResult ajax = AjaxResult.success();
            ajax.put("path", result.path());
            return ajax;
        }
        catch (Exception e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 登记文件台账（登记失败不影响上传结果）
     */
    private void recordSysFile(MultipartFile file, String storageKey)
    {
        recordSysFile(file, storageKey, null);
    }

    /**
     * 登记文件台账；fileNameOverride 非空时以它为台账文件名（外链转存记原始 URL 便于溯源）
     */
    private void recordSysFile(MultipartFile file, String storageKey, String fileNameOverride)
    {
        SysFile sysFile = new SysFile();
        sysFile.setFileName(StringUtils.isNotEmpty(fileNameOverride) ? fileNameOverride : file.getOriginalFilename());
        sysFile.setStorageKey(storageKey);
        sysFile.setStorageType(fileStorageService.type());
        sysFile.setFileSize(file.getSize());
        sysFile.setContentType(file.getContentType());
        try
        {
            sysFile.setCreateBy(SecurityUtils.getUsername());
        }
        catch (Exception ignored)
        {
        }
        sysFileService.recordFile(sysFile);
    }

    /**
     * 本地资源通用下载
     */
    @GetMapping("/download/resource")
    public void resourceDownload(String resource, HttpServletRequest request, HttpServletResponse response)
            throws Exception
    {
        try
        {
            if (!FileUtils.checkAllowDownload(resource))
            {
                throw new Exception(StringUtils.format("资源文件({})非法，不允许下载。 ", resource));
            }
            // 本地资源路径
            String localPath = ShiGuangJiConfig.getProfile();
            // 数据库资源地址
            String downloadPath = localPath + FileUtils.stripPrefix(resource);
            // 下载名称
            String downloadName = StringUtils.substringAfterLast(downloadPath, "/");
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            FileUtils.setAttachmentResponseHeader(response, downloadName);
            FileUtils.writeBytes(downloadPath, response.getOutputStream());
        }
        catch (Exception e)
        {
            log.error("下载文件失败", e);
        }
    }
}
