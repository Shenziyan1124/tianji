package com.tianji.exam.service;

import com.tianji.exam.domain.dto.ExamStartDTO;
import com.tianji.exam.domain.vo.ExamVO;

/**
 * <p>
 * 考试 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-09-02
 */
public interface IExamService {

    /**
     * 获取试题并开始考试
     */
    ExamVO startExam(ExamStartDTO dto);
}
