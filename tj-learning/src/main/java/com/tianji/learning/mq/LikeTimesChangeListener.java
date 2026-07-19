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

import java.util.ArrayList;
import java.util.List;

import static com.tianji.common.constants.MqConstants.Exchange.LIKE_RECORD_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.QA_LIKED_TIMES_KEY;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikeTimesChangeListener {
    private final IInteractionReplyService replyService;

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(name = "qa.like.times.queue", durable = "true"),
                    exchange = @Exchange(name = LIKE_RECORD_EXCHANGE, type = ExchangeTypes.TOPIC),
                    key = QA_LIKED_TIMES_KEY
            ))
    public void listenReplyLikedTimesChange(List<LikeTimesDTO> dto) {
        log.debug("收到点赞数变化消息: {}", dto);

        List<InteractionReply> list = new ArrayList<>(dto.size());
        for (LikeTimesDTO d : dto) {
            InteractionReply r = new InteractionReply();
            r.setId(d.getBizId());
            r.setLikedTimes(d.getLikeTimes());
            list.add(r);
        }

        replyService.updateBatchById(list);
    }

}
