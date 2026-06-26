package com.filemanager.service.node.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "curator")
public class ZookeeperProperties {

    private String connectString = "localhost:2181";
    private int sessionTimeoutMs = 30000;
    private int connectionTimeoutMs = 10000;
    private String basePath = "file-manager";
}
