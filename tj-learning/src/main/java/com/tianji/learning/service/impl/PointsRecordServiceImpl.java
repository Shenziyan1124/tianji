package com.tianji.learning.service.impl;

import cn.hutool.core.io.resource.StringResource;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mapper.PointsRecordMapper;
import com.tianji.learning.service.IPointsRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务实现类
 * </p>
 *
 * @author SHEN
 * @since 2026-07-21
 */
@Service
@RequiredArgsConstructor
public class PointsRecordServiceImpl extends ServiceImpl<PointsRecordMapper, PointsRecord>
        implements IPointsRecordService {


    private final StringRedisTemplate redisTemplate;

    // 添加积分记录
    @Override
    public void addPointsRecord(Long userId, int points, PointsRecordType type) {

        int maxPoints = type.getMaxPoints();
        int realPoints = points;
        LocalDateTime now = LocalDateTime.now();

        // 1.判断是否有积分上限
        if (maxPoints > 0) {
            // 2.有, 看是否超过上限
            LocalDateTime startTime = DateUtils.getDayStartTime(now);
            LocalDateTime endTime = DateUtils.getDayEndTime(now);
            // 2.1 查询今日积分
            int currentPoints = queryUserPointsByTypeAndDate(userId, type, startTime, endTime);
            // 2.2 判断是否超过上限
            if (currentPoints  >= maxPoints) return;
            // 2.3 超过上限,不保存
            // 2.4 没有超过上限,保存
            if (currentPoints + points > maxPoints) {
                realPoints = maxPoints - currentPoints;
            }
        }
        // 3.没有,直接保存
        PointsRecord pointsRecord = new PointsRecord();
        pointsRecord.setUserId(userId);
        pointsRecord.setPoints(realPoints);
        pointsRecord.setType(type);
        save(pointsRecord);

        // 4.累计积分数据更新到redis的storedSet中
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX +
               now.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);

        redisTemplate.opsForZSet().incrementScore(key, userId.toString(), realPoints);

    }



    private int queryUserPointsByTypeAndDate(
            Long userId, PointsRecordType type,
            LocalDateTime startTime, LocalDateTime endTime) {
        QueryWrapper<PointsRecord> queryWrapper = new QueryWrapper<>();

        queryWrapper.lambda()
                .eq(PointsRecord::getUserId, userId)
                .eq(type != null, PointsRecord::getType, type)
                .between( startTime != null && endTime != null, PointsRecord::getCreateTime, startTime, endTime);

        Integer points = getBaseMapper().queryUserPointsByTypeAndDate(queryWrapper);

        return points == null ? 0 : points;
    }


    // 获取今日积分统计
    @Override
    public List<PointsStatisticsVO> getTodayPoints() {

        Long userId = UserContext.getUser();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = DateUtils.getDayStartTime(now);
        LocalDateTime endTime = DateUtils.getDayEndTime(now);

        QueryWrapper<PointsRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda()
                .eq(PointsRecord::getUserId, userId)
                .between(PointsRecord::getCreateTime, startTime, endTime);

        List<PointsRecord> p = getBaseMapper().getTodayPoints(queryWrapper);

        if (CollUtils.isEmpty(p)) return List.of();


        return p.stream().map(record -> {
            PointsStatisticsVO vo = new PointsStatisticsVO();
            vo.setPoints(record.getPoints());
            vo.setType(record.getType().getDesc());
            vo.setMaxPoints(record.getType().getMaxPoints());
            return vo;
        }).collect(Collectors.toList());
    }
}
