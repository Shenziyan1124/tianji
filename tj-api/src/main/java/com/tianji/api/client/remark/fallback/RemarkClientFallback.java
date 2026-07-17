package com.tianji.api.client.remark.fallback;

import com.tianji.api.client.remark.RemarkClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

import java.util.Collections;
import java.util.Set;

@Slf4j
public class RemarkClientFallback implements FallbackFactory<RemarkClient> {

    @Override
    public RemarkClient create(Throwable cause) {
        log.error("查询tj-remark服务异常", cause);

        return new RemarkClient() {
            @Override
            public Set<Long> isBizLiked(Iterable<Long> bizIds) {
                log.warn("查询点赞状态降级，bizIds: {}", bizIds, cause);
                return Collections.emptySet();
            }
        };
    }
}
