package com.sky.controller.admin;

import com.sky.constant.MessageConstant;
import com.sky.properties.FileProperties;
import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 通用接口
 */
@RestController   // 标识为rest控制器，返回json
@RequestMapping("/admin/common") // 设置接口统一前缀
@Slf4j            // lombok日志注解
@Api(tags = "通用接口") // swagger文档注解（旧版swagger）
public class CommonController {

    @Autowired
    private FileProperties fileProperties;

    /**
     * 文件上传
     * @param file
     * @return
     */
    @PostMapping("/upload")
    @ApiOperation("文件上传")
    public Result<String> upload(MultipartFile file){
        log.info("文件上传：{}", file.getOriginalFilename());

        // 1. 获取原始文件名
        String originalFilename = file.getOriginalFilename();
        // 2. 用UUID重命名，避免文件名冲突
        String fileName = UUID.randomUUID() + originalFilename.substring(originalFilename.lastIndexOf("."));

        // 3. 从配置中获取文件存储路径
        String basePath = fileProperties.getFilePath();

        // 4. 目录不存在时自动创建
        Paths.get(basePath).toFile().mkdirs();

        // 5. 保存文件
        try {
            file.transferTo(Paths.get(basePath, fileName).toFile());
        } catch (IOException e) {
            log.error("文件上传失败", e);
            return Result.error(MessageConstant.UPLOAD_FAILED);
        }

        log.info("文件上传成功，文件名：{}", fileName);
        return Result.success("/upload/" + fileName);
    }
}
