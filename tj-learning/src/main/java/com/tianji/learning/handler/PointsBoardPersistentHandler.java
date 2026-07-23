package com.tianji.learning.handler;

import cn.hutool.db.meta.Table;
import com.github.xiaoymin.knife4j.core.util.CommonUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.utils.TableInfoContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PointsBoardPersistentHandler {

    private final IPointsBoardSeasonService pointsBoardSeasonService;
    private final IPointsBoardService pointsBoardService;
    private final StringRedisTemplate stringRedisTemplate;

    @XxlJob("createTableJob")
    public void createPointsBoardTableOfLastSeason() {
        // 1. 获取上月时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        // 2. 查询赛季id
        Integer seasonId = pointsBoardSeasonService.querySeasonByTime(time);
        if (seasonId == null) return;
        // 3. 创建表
        pointsBoardService.createPointsBoardTable(seasonId);
    }


    @XxlJob("savePointsBoard2DB")
    public void savePointsBoard2DB() {
        // 1. 获取上月时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        // 2. 动态计算表
        Integer seasonId = pointsBoardSeasonService.querySeasonByTime(time);
        if (seasonId == null) return;
        TableInfoContext.setInfo("points_board_" + seasonId);

        // 3. 查询榜单数据
        // 3.1 拼接key
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + time.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        // 3.2 查询数据
        int index = XxlJobHelper.getShardIndex();
        int total = XxlJobHelper.getShardTotal();
        int pageNo = index + 1;
        int pageSize = 1;
        while (true) {
            List<PointsBoard> list = pointsBoardService.queryCurrentBoardList(key, pageNo, pageSize);
            if (CollUtils.isEmpty(list)) break;

            // 4. 持久化数据库
            // pointsboard里有多个字段, 查询的只有三个, 把rank设置到id上, rank设置null, mybatisplus会处理null的不更新
            list.forEach(c -> {
                c.setId(c.getRank().longValue());
                c.setRank(null);
            });
            pointsBoardService.saveBatch(list);

            // 5. 分页++
            pageNo+=total;
        }
        TableInfoContext.remove();
    }

    @XxlJob("clearPointsBoardFormRedis")
    public void clearPointsBoardFormRedis() {
        // 1. 获取上月时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        // 2. 拼接key
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + time.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        // 3. 删除redis数据
        stringRedisTemplate.unlink(key);
    }

}
