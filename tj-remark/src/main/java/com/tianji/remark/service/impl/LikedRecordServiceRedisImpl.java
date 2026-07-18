package com.tianji.remark.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.dto.remark.LikeTimesDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.constants.RedisConstants;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.dto.LikedTimesDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.tianji.common.constants.MqConstants.Exchange.LIKE_RECORD_EXCHANGE;

/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author SHEN
 * @since 2026-07-16
 */
@Service
@RequiredArgsConstructor
public class LikedRecordServiceRedisImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    private final RabbitMqHelper mqHelper;
    private final StringRedisTemplate redisTemplate;

    // 添加点赞记录
    @Override
    public void addLikeRecord(LikeRecordFormDTO dto) {
        // 根据前端看是点赞还是取消
        Boolean success = dto.getLiked() ? like(dto) : cancelLike(dto);

        // 判断是否执行成功,失败直接返回
        if (!success) return;

        // 执行成功,统计点赞数
        Long likedCount = redisTemplate.opsForSet().size(
                RedisConstants.LIKES_BIZ_KEY_PREFIX + dto.getBizId()
        );

        // 缓存点赞数到redis
        redisTemplate.opsForZSet().add(
                RedisConstants.LIKES_TIME_KEY_PREFIX + dto.getBizType(),
                dto.getBizId().toString(),
                likedCount != null ? likedCount : 0
        );
    }


    private Boolean cancelLike(LikeRecordFormDTO dto) {
        // 获取当前用户
        Long userId = UserContext.getUser();

        // 获取key
        String key = RedisConstants.LIKES_BIZ_KEY_PREFIX + dto.getBizId();
        //执行sadd命令
        Long count = redisTemplate.opsForSet().remove(key, userId);

        return count != null && count > 0; // 1成功 0失败
    }

    private Boolean like(LikeRecordFormDTO dto) {
        // 获取当前用户
        Long userId = UserContext.getUser();

        // 获取key
        String key = RedisConstants.LIKES_BIZ_KEY_PREFIX + dto.getBizId();
        //执行sadd命令
        Long count = redisTemplate.opsForSet().add(key, String.valueOf(userId));

        return count != null && count > 0;
    }


    // 查询点赞记录
    @Override
    public Set<Long> isBizLiked(List<Long> bizIds) {
        // 获取当前用户
        Long userId = UserContext.getUser();

        // 查询当前用户是否点赞
        List<Object> objects = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection src = (StringRedisConnection) connection;
            for (Long bizId : bizIds) {
                String key = RedisConstants.LIKES_BIZ_KEY_PREFIX + bizId;
                src.sIsMember(key, String.valueOf(userId));
            }
            return null;
        });

        // 返回结果
        Set<Long> set = new HashSet<>();
        for (int i = 0; i < objects.size(); i++) {
            Object o = objects.get(i);
            if (o != null && (Boolean) o) {
                set.add(bizIds.get(i));
            }
        }
//        Set<Long> collect = IntStream.range(0, objects.size())
//                .filter(i -> objects.get(i) != null && (Boolean) objects.get(i))
//                .mapToObj(bizIds::get)
//                .collect(Collectors.toSet());
        return set;
    }

    @Override
    public void checkLikedTimesAndSendMessage(String bizType) {

    }


    @Override
    public void checkLikedTimesAndSendMessage(String bizType, int maxLikedTimes) {
        // 读取并移除redis中的缓存
        String key = RedisConstants.LIKES_TIME_KEY_PREFIX + bizType;
        Set<ZSetOperations.TypedTuple<String>> typedTuples =
                redisTemplate.opsForZSet().popMin(key, maxLikedTimes);

        if (CollUtils.isEmpty(typedTuples)){
            return;
        }

        // 转换数据
        List<LikedTimesDTO> list = new ArrayList<>(typedTuples.size());

        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            String bizId = typedTuple.getValue();
            Double likedTimes = typedTuple.getScore();
            if (bizId == null || likedTimes == null) continue;

            list.add(LikedTimesDTO.of(Long.valueOf(bizId), likedTimes.intValue()));

        }

        // mq通知

        mqHelper.send(
                LIKE_RECORD_EXCHANGE,
                StringUtils.format("LIKED_TIMES_KEY_TEMPLATE", bizType),
                list
        );

    }

//    @Override
//    public void checkLikedTimesAndSendMessage(String bizType, int maxLikedTimes) {

//
//    }
}
