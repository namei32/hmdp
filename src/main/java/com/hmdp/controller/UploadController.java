package com.hmdp.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.config.HmdpProperties;
import com.hmdp.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/upload")
public class UploadController {
    private final File imageUploadDir;

    public UploadController(HmdpProperties hmdpProperties) {
        this.imageUploadDir = new File(hmdpProperties.getUpload().getImageDir());
    }

    @PostMapping("/blog")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return Result.fail("上传文件不能为空");
        }
        try {
            String originalFilename = image.getOriginalFilename();
            if (StrUtil.isBlank(originalFilename)) {
                return Result.fail("文件名称不能为空");
            }
            String fileName = createNewFileName(originalFilename);
            File targetFile = resolveUploadFile(fileName);
            File parent = targetFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("创建上传目录失败: " + parent.getAbsolutePath());
            }

            image.transferTo(targetFile);
            log.info("文件上传成功: {}", targetFile.getAbsolutePath());
            return Result.ok("/" + fileName);
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @GetMapping("/blog/delete")
    public Result deleteBlogImg(@RequestParam("name") String filename) {
        try {
            File file = resolveUploadFile(filename);
            if (!file.exists() || file.isDirectory()) {
                return Result.fail("错误的文件名称");
            }
            FileUtil.del(file);
            return Result.ok();
        } catch (IOException e) {
            log.warn("删除图片失败，文件名非法: {}", filename, e);
            return Result.fail("错误的文件名称");
        }
    }

    private String createNewFileName(String originalFilename) {
        String suffix = StrUtil.subAfter(originalFilename, ".", true);
        if (StrUtil.isBlank(suffix)) {
            throw new IllegalArgumentException("文件后缀不能为空");
        }

        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        return StrUtil.format("blogs/{}/{}/{}.{}", d1, d2, name, suffix);
    }

    private File resolveUploadFile(String filename) throws IOException {
        String normalizedName = filename.replace('\\', '/');
        while (normalizedName.startsWith("/")) {
            normalizedName = normalizedName.substring(1);
        }

        File root = imageUploadDir.getCanonicalFile();
        File file = new File(root, normalizedName).getCanonicalFile();
        String rootPath = root.getPath();
        if (!file.getPath().equals(rootPath) && !file.getPath().startsWith(rootPath + File.separator)) {
            throw new IOException("文件路径越界: " + filename);
        }
        return file;
    }
}
