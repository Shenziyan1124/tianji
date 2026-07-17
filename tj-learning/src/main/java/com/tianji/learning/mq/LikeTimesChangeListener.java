package com.tianji.learning.mq;

import com.tianji.api.dto.remark.LikeTimesDTO;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.tianji.common.constants.MqConstants.Exchange.LIKE_RECORD_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.QA_LIKED_TIMES_KEY;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikeTimesChangeListener {

    private final IInteractionReplyService interactionReplyService;
/**
 * 使用RabbitMQ监听器来处理点赞次数变更的消息
 * 通过绑定特定的队列、交换机和路由键来接收消息
 *
 * @param dto 包含点赞变更信息的DTO对象
 */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "qa.liked.times.queue", durable = "true"),  // 创建一个持久化的队列，名称为qa.liked.times.queue
            exchange = @Exchange(name = LIKE_RECORD_EXCHANGE, type = ExchangeTypes.TOPIC),  // 绑定一个主题类型的交换机，名称为LIKE_RECORD_EXCHANGE
            key = QA_LIKED_TIMES_KEY  // 设置路由键为QA_LIKED_TIMES_KEY
    ))
    public void listenReplyLikedTimesChange(LikeTimesDTO dto){
        // 记录调试日志，输出收到的点赞次数变更消息
        log.debug("收到点赞次数变更消息: {}", dto);

        // 创建InteractionReply对象并设置属性
        InteractionReply r = new InteractionReply();
        r.setId(dto.getBizId());  // 设置业务ID
        r.setLikedTimes(dto.getLikeTimes());  // 设置点赞次数
        // 调用服务层方法更新回复的点赞次数
        interactionReplyService.updateById(r);

    }
}
