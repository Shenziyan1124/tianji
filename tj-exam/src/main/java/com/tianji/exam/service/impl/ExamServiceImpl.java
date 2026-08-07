package com.tianji.exam.service.impl;

import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.learning.LearningClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.exam.QuestionDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.exam.constants.ExamErrorInfo;
import com.tianji.exam.constants.ExamStatus;
import com.tianji.exam.domain.dto.ExamStartDTO;
import com.tianji.exam.domain.po.ExamRecord;
import com.tianji.exam.domain.vo.ExamVO;
import com.tianji.exam.Repository.ExamRecordRepository;
import com.tianji.exam.domain.vo.QuestionVO;
import com.tianji.exam.service.IExamService;
import com.tianji.exam.service.IQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * <p>
 * 考试 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-09-02
 */
@Service
@RequiredArgsConstructor
public class ExamServiceImpl implements IExamService {

    private final ExamRecordRepository examRecordRepository;
    private final MongoTemplate mongoTemplate;
    private final LearningClient learningClient;
    private final CatalogueClient catalogueClient;
    private final IQuestionService questionService;

    @Override
    public ExamVO startExam(ExamStartDTO dto) {
        Integer type = dto.getType();
        Long sectionId = dto.getSectionId();
        Long courseId = dto.getCourseId();
        Long userId = UserContext.getUser();

        // TODO 1.校验用户是否购买该课程(远程调用学习服务)
        Long lessonValid = learningClient.isLessonValid(courseId);
        if (lessonValid == null) {
            throw new BizIllegalException(ExamErrorInfo.EXAM_NOT_ENROLL);
        }
        // TODO 1.5.校验小节是否属于该课程，防止越权用小节id查其他课程的题目
        List<CataSimpleInfoDTO> cataList = catalogueClient.batchQueryCatalogue(List.of(sectionId));
        if (CollUtils.isEmpty(cataList) || !courseId.equals(cataList.get(0).getCourseId())) {
            throw new BizIllegalException(ExamErrorInfo.SECTION_NOT_BELONG_COURSE);
        }
        // TODO 2.若type=2(考试)，校验考试次数，每人只能参加一次(examRecordRepository.countByUserIdAndCourseIdAndSectionIdAndType)
        if (type.equals(2)) {
            long l = examRecordRepository.countByUserIdAndCourseIdAndSectionIdAndType(userId, courseId, sectionId, type);
            if (l >= 1) {
                throw new BizIllegalException(ExamErrorInfo.EXAM_COUNT_LIMIT);
            }
        }
        // TODO 3.构建ExamRecord，设置userId、courseId、sectionId、type、status、startTime，插入MongoDB(examRecordRepository.insert)
        ExamRecord examRecord = new ExamRecord()
                .setUserId(userId)
                .setCourseId(courseId)
                .setSectionId(sectionId)
                .setType(type)
                .setStartTime(LocalDateTime.now())
                .setStatus(ExamStatus.ANSWERING.getValue());

        examRecord = examRecordRepository.insert(examRecord);
        String examRecordId = examRecord.getId(); // 考试记录id

        // TODO 4.根据sectionId查询MySQL题目列表(questionService)
        List<QuestionDTO> questionDTOS = questionService.queryQuestionByBizId(sectionId);
        if (CollUtils.isEmpty(questionDTOS)){
            throw new BadRequestException(ExamErrorInfo.QUESTION_NOT_EXISTS);
        }
        // TODO 5.组装ExamVO{id: 考试记录id, questions: [...]}返回
        ExamVO vo = new ExamVO();
        vo.setId(examRecordId);
        vo.setQuestions(BeanUtils.copyList(questionDTOS, QuestionVO.class));

        return vo;

    }
}
