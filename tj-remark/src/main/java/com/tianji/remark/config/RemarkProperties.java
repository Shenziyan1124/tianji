package com.tianji.remark.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@RefreshScope // ✅ 关键注解：允许在 Nacos 修改配置后，不重启服务即可动态刷新到最新值
@ConfigurationProperties(prefix = "tj.remark") // ✅ 绑定 Nacos 中的 tj.remark 前缀
public class RemarkProperties {

    /**
     * 对应 Nacos 中的 tj.remark.max-liked-times
     * 如果 Nacos 没配，默认值为 10
     */
    private int maxLikedTimes = 10;

    /**
     * 对应 Nacos 中的 tj.remark.biz-types
     * 如果 Nacos 没配，默认为空集合
     */
    private List<String> bizTypes = List.of();
}
