package com.tianji.remark.task;


import com.tianji.remark.config.RemarkProperties;
import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class LikedTimesCheckTask {

    // TODO 最好放在配置文件中
    // private final static int MAX_LIKED_TIMES = 10;
    // private final static List<String> BIZ_TYPES = List.of("NOTE", "QA");
    private final ILikedRecordService service;
    private final RemarkProperties remarkProperties;

    @Scheduled(fixedDelay = 30000)
    public void checkLikedTimes() {
        log.info("开始检查点赞次数同步");
        for (String bizType : remarkProperties.getBizTypes()) {
            service.checkLikedTimesAndSendMessage(bizType, remarkProperties.getMaxLikedTimes());
        }

    }
}
