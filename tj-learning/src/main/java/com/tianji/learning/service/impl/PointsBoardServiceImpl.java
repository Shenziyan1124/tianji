package com.tianji.learning.service.impl;

import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardItemVO;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.mapper.PointsBoardMapper;
import com.tianji.learning.service.IPointsBoardService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.BoundZSetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.validation.constraints.Min;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 学霸天梯榜 服务实现类
 * </p>
 *
 * @author SHEN
 * @since 2026-07-21
 */
@Service
@RequiredArgsConstructor
public class PointsBoardServiceImpl extends ServiceImpl<PointsBoardMapper, PointsBoard> implements IPointsBoardService {

    private final StringRedisTemplate redisTemplate;
    private final UserClient userClient;

    // 查询赛季的积分榜
    @Override
    public PointsBoardVO queryPointsBoardsBySeason(PointsBoardQuery query) {
        // 0. 判断是否查询当前赛季
        boolean isCurrent = query.getSeason() == null || query.getSeason() == 0;
        // 0. 获取redis的key
        LocalDateTime now = LocalDateTime.now();
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + now.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);

        // 1. 查询我的积分和排名
        PointsBoard myPointsBoard = isCurrent ?
                queryMyCurrentBoard(key) :  // 查询当前赛季 redis查
                queryMyHistoryBoard(query.getSeason()); // 查询指定赛季 mysql查

        // 2. 查询榜单排行榜,分页
        List<PointsBoard> list = isCurrent ?
                queryCurrentBoardList(key, query.getPageNo(), query.getPageSize()) : // 查询当前赛季 redis查
                queryHistoryBoardList(query); // 查询指定赛季 mysql查

        // 3. 封装vo返回

        // 3.0 返回我的积分和排名
        PointsBoardVO vo = new PointsBoardVO();
        if (myPointsBoard != null) {
            vo.setRank(myPointsBoard.getRank());
            vo.setPoints(myPointsBoard.getPoints());
        }
        if (CollUtils.isEmpty(list)) return vo;

        // 3.1 查询用户信息
        Set<Long> uIds = list.stream().map(PointsBoard::getUserId).collect(Collectors.toSet());
        List<UserDTO> userDTOS = userClient.queryUserByIds(uIds);
        Map<Long,String> userMap = new HashMap<>(uIds.size());
        if (CollUtils.isNotEmpty(userDTOS)) {
            userMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, UserDTO::getName));
        }

        // 3.2 处理排行榜列表
        List<PointsBoardItemVO> items = new ArrayList<>(list.size());
        for (PointsBoard p : list) {
            PointsBoardItemVO item = new PointsBoardItemVO();
            item.setRank(p.getRank());
            item.setPoints(p.getPoints());
            item.setName(userMap.get(p.getUserId()));
            items.add(item);
        }
        vo.setBoardList(items);

        return vo;
    }



    private List<PointsBoard> queryHistoryBoardList(PointsBoardQuery query) {
        return null;
    }

    @Override
    public List<PointsBoard> queryCurrentBoardList(String key,
                                                   @Min(value = 1, message = "页码不能小于1") Integer pageNo,
                                                   @Min(value = 1, message = "每页查询数量不能小于1") Integer pageSize) {

        int from = (pageNo - 1) * pageSize; // 起始索引
        Set<ZSetOperations.TypedTuple<String>> typedTuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, from, from + pageSize - 1);

        if (CollUtils.isEmpty(typedTuples)) return CollUtils.emptyList();

        int rank = from + 1;
        List<PointsBoard> list = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            if (typedTuple.getValue() == null || typedTuple.getScore() == null) {
                continue;
            }
            PointsBoard pointsBoard = new PointsBoard();
            pointsBoard.setUserId(Long.valueOf(typedTuple.getValue()));
            pointsBoard.setPoints(typedTuple.getScore().intValue());
            pointsBoard.setRank(rank++);
            list.add(pointsBoard);
        }

        return list;
    }

    private PointsBoard queryMyHistoryBoard(Long season) {
        return null;
    }

    private PointsBoard queryMyCurrentBoard(String key) {
        String userId = UserContext.getUser().toString();
        // 0. 绑定redis的key
        BoundZSetOperations<String, String> ops = redisTemplate.boundZSetOps(key);
        // 1. 查询积分
        Double points = ops.score(userId);
        // 2. 查询排名
        Long rank = ops.reverseRank(userId);
        // 3. 封装返回
        PointsBoard pointsBoard = new PointsBoard();
        pointsBoard.setPoints(points == null ? 0 : points.intValue());
        pointsBoard.setRank(rank == null ? 0 : rank.intValue() + 1);
        return pointsBoard;
    }


    // 创建积分榜表
    @Override
    public void createPointsBoardTable(Integer seasonId) {
        getBaseMapper().createPointsBoardTable("points_board_" + seasonId);
    }

}
