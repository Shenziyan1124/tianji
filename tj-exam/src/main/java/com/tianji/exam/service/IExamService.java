package com.tianji.exam.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.exam.domain.dto.ExamStartDTO;
import com.tianji.exam.domain.dto.ExamSubmitDTO;
import com.tianji.exam.domain.po.ExamRecord;
import com.tianji.exam.domain.vo.ExamRecordQuestionVO;
import com.tianji.exam.domain.vo.ExamVO;

import javax.validation.Valid;
import java.util.List;

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

    /** 提交试卷 **/
    void submitExam(ExamSubmitDTO dto);


    PageDTO<ExamRecord> getMyExamPage(PageQuery query);

    List<ExamRecordQuestionVO> getExamDetailById(String id);
}
