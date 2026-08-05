package com.tianji.learning.mq;


import com.tianji.api.dto.remark.LikeTimesDTO;
import com.tianji.learning.domain.po.Note;
import com.tianji.learning.service.INoteService;
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
import static com.tianji.common.constants.MqConstants.Key.NOTE_LIKED_TIMES_KEY;

@Slf4j
@Component
@RequiredArgsConstructor
public class NoteLikeTimesChangeListener {
    private final INoteService noteService;
    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(name = "note.like.times.queue", durable = "true"),
                    exchange = @Exchange(name = LIKE_RECORD_EXCHANGE, type = ExchangeTypes.TOPIC),
                    key = NOTE_LIKED_TIMES_KEY
            ))
    public void listenNoteLikedTimesChange(List<LikeTimesDTO> dto) {
        log.debug("收到笔记点赞数变化消息: {}", dto);

        List<Note> list = new ArrayList<>(dto.size());
        for (LikeTimesDTO d : dto) {
            Note note = new Note();
            note.setId(d.getBizId());          // 业务id = 笔记id
            note.setLikedTimes(d.getLikeTimes()); // 点赞数
            list.add(note);
        }

        noteService.updateBatchById(list);
    }
}
