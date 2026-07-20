package com.tianji.remark.task;


import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LikedTimesCheckTask {

    private final static int MAX_LIKED_TIMES = 10;
    // TODO 最好放在配置文件中
    private final static List<String> BIZ_TYPES = List.of("NOTE", "QA");
    private final ILikedRecordService service;

    @Scheduled(fixedDelay = 30000)
    public void checkLikedTimes() {

        for (String bizType : BIZ_TYPES) {
            service.checkLikedTimesAndSendMessage(bizType, MAX_LIKED_TIMES);
        }

    }
}
