package com.tianji.learning.mapper;

import com.tianji.learning.domain.po.LearningLesson;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import feign.Param;

/**
 * <p>
 * 学生课程表 Mapper 接口
 * </p>
 *
 * @author SHEN
 * @since 2026-06-30
 */
public interface LearningLessonMapper extends BaseMapper<LearningLesson> {

    Integer queryTotalPlan(@Param("userId") Long userId);
}
