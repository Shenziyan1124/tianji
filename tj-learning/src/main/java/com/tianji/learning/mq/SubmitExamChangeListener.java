package com.tianji.learning.mq;

import com.tianji.api.dto.leanring.LearningRecordFormDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.service.ILearningRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubmitExamChangeListener {

    private final ILearningRecordService recordService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "learning.exam.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.LEARNING_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.EXAM_SUBMIT)
    )
    public void listen(LearningRecordFormDTO dto) {
        // 1.MQ消费者没有登录上下文,手动放入用户
        UserContext.setUser(dto.getUserId());
        // 2.新增学习记录(考试类型,直接落库并完成课表进度)
        recordService.saveLearningRecord(dto);
    }
}
