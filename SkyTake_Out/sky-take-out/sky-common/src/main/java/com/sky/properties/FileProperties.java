package com.sky.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件上传相关配置
 */
@Component
@ConfigurationProperties(prefix = "sky.file")
@Data
public class FileProperties {

    /**
     * 文件上传存储路径
     */
    private String filePath;
}
