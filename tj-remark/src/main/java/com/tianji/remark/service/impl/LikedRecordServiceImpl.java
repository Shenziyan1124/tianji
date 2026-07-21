package com.tianji.remark.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.tianji.api.dto.remark.LikeTimesDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tianji.common.constants.MqConstants.Exchange.LIKE_RECORD_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE;

/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author SHEN
 * @since 2026-07-16
 */
// @Service
@RequiredArgsConstructor
public class LikedRecordServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    private final RabbitMqHelper mqHelper;

    // 添加点赞记录
    @Override
    public void addLikeRecord(LikeRecordFormDTO dto) {
        // 根据前端看是点赞还是取消
        Boolean success = dto.getLiked() ? like(dto) : cancelLike(dto);

        // 判断是否执行成功,失败直接返回
        if (!success) return;

        // 执行成功,统计点赞数
        Integer count = lambdaQuery().eq(LikedRecord::getBizId, dto.getBizId()).count();

        // mq通知
        mqHelper.send(
                LIKE_RECORD_EXCHANGE,
                StringUtils.format(LIKED_TIMES_KEY_TEMPLATE, dto.getBizType()),
                LikeTimesDTO.of(dto.getBizId(),count)
        );
    }


    private Boolean cancelLike(LikeRecordFormDTO dto) {
        return remove(lambdaQuery()
                .eq(LikedRecord::getUserId, UserContext.getUser())
                .eq(LikedRecord::getBizId, dto.getBizId()));
    }

    private Boolean like(LikeRecordFormDTO dto) {
        Long userId = UserContext.getUser();
        // 查询当前用户的点赞记录
        Integer count = lambdaQuery()
                .eq(LikedRecord::getUserId, userId)
                .eq(LikedRecord::getBizId, dto.getBizId())
                .count();
        // 存在, 直接return
        if (count > 0) return false;
        // 否则, 直接新增
        LikedRecord likedRecord = BeanUtils.copyBean(dto, LikedRecord.class);
        likedRecord.setUserId(userId);
        save(likedRecord);
        return true;
    }


    // 查询点赞记录
    @Override
    public Set<Long> isBizLiked(List<Long> bizIds) {
        // 获取当前用户
        Long userId = UserContext.getUser();
        // 查询当前用户是否点赞
        List<LikedRecord> list = lambdaQuery()
                .in(LikedRecord::getBizId, bizIds)
                .eq(LikedRecord::getUserId, userId)
                .list();

        // 返回结果
        return list.stream().map(LikedRecord::getBizId).collect(Collectors.toSet());
    }


    @Override
    public void checkLikedTimesAndSendMessage(String bizType, int maxLikedTimes) {

    }
}
