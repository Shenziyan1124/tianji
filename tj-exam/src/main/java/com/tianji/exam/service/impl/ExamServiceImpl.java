package com.tianji.exam.service.impl;

import com.mongodb.client.result.UpdateResult;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.learning.LearningClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.exam.QuestionDTO;
import com.tianji.api.dto.leanring.LearningRecordFormDTO;
import com.tianji.api.dto.leanring.SectionType;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.exam.constants.ExamErrorInfo;
import com.tianji.exam.constants.ExamStatus;
import com.tianji.exam.domain.dto.ExamStartDTO;
import com.tianji.exam.domain.dto.ExamSubmitDTO;
import com.tianji.exam.domain.po.ExamRecord;
import com.tianji.exam.domain.po.ExamRecordQuestion;
import com.tianji.exam.domain.vo.ExamRecordQuestionVO;
import com.tianji.exam.domain.vo.ExamVO;
import com.tianji.exam.Repository.ExamRecordRepository;
import com.tianji.exam.domain.vo.QuestionAnswerVO;
import com.tianji.exam.domain.vo.QuestionVO;
import com.tianji.exam.service.IExamService;
import com.tianji.exam.service.IQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
    private final RabbitMqHelper rabbitMqHelper;

    // 获取试题并开始考试
    @Override
    public ExamVO startExam(ExamStartDTO dto) {
        Integer type = dto.getType();
        Long sectionId = dto.getSectionId();
        Long courseId = dto.getCourseId();
        Long userId = UserContext.getUser();

        // 1.校验用户是否购买该课程(远程调用学习服务)
        Long lessonValid = learningClient.isLessonValid(courseId);
        if (lessonValid == null) {
            throw new BizIllegalException(ExamErrorInfo.EXAM_NOT_ENROLL);
        }
        // 1.5.校验小节是否属于该课程，防止越权用小节id查其他课程的题目
        List<CataSimpleInfoDTO> cataList = catalogueClient.batchQueryCatalogue(List.of(sectionId));
        if (CollUtils.isEmpty(cataList) || !courseId.equals(cataList.get(0).getCourseId())) {
            throw new BizIllegalException(ExamErrorInfo.SECTION_NOT_BELONG_COURSE);
        }
        // 2.若type=2(考试)，校验考试次数，每人只能参加一次(examRecordRepository.countByUserIdAndCourseIdAndSectionIdAndType)
        if (type.equals(2)) {
            long l = examRecordRepository.countByUserIdAndCourseIdAndSectionIdAndType(userId, courseId, sectionId, type);
            if (l >= 1) {
                throw new BizIllegalException(ExamErrorInfo.EXAM_COUNT_LIMIT);
            }
        }
        // 3.构建ExamRecord，设置userId、courseId、sectionId、type、status、startTime，插入MongoDB(examRecordRepository.insert)
        ExamRecord examRecord = new ExamRecord()
                .setUserId(userId)
                .setCourseId(courseId)
                .setSectionId(sectionId)
                .setLessonId(lessonValid)
                .setType(type)
                .setStartTime(LocalDateTime.now())
                .setStatus(ExamStatus.ANSWERING.getValue());

        examRecord = examRecordRepository.insert(examRecord);
        String examRecordId = examRecord.getId(); // 考试记录id

        // 4.根据sectionId查询MySQL题目列表(questionService)
        List<QuestionDTO> questionDTOS = questionService.queryQuestionByBizId(sectionId);
        if (CollUtils.isEmpty(questionDTOS)){
            throw new BadRequestException(ExamErrorInfo.QUESTION_NOT_EXISTS);
        }
        // 5.组装ExamVO{id: 考试记录id, questions: [...]}返回
        ExamVO vo = new ExamVO();
        vo.setId(examRecordId);
        vo.setQuestions(BeanUtils.copyList(questionDTOS, QuestionVO.class));

        return vo;

    }

    // 提交试卷
    @Override
    public void submitExam(ExamSubmitDTO dto) {
        String examId = dto.getId();
        Long userId = UserContext.getUser();

        // 1. TODO 判断试卷id是否存在 + 属于当前用户
        ExamRecord examRecord = examRecordRepository.findById(examId)
                .orElseThrow(() -> new BizIllegalException(ExamErrorInfo.EXAM_NOT_EXISTS));
        if (!examRecord.getUserId().equals(userId)) {
            throw new BizIllegalException(ExamErrorInfo.EXAM_NOT_EXISTS);
        }

        // 2. 自动批卷
        // 2.1 收集题目id,批量查题目questionDto 有answer 正确答案和score分
        List<@NotNull(message = "题目id不能为空") Long> qIds = dto.getAnswers().stream()
                .map(ExamSubmitDTO.AnswerDTO::getQuestionId).collect(Collectors.toList());
        Map<Long, QuestionDTO> qMap = questionService.queryQuestionByIds(qIds).stream()
                .collect(Collectors.toMap(QuestionDTO::getId, q -> q));
        // 2.2 逐题对比答案,算总分
        int totalScore = 0;
        ArrayList<ExamRecordQuestion> answers = new ArrayList<>(dto.getAnswers().size());

        for (ExamSubmitDTO.AnswerDTO answer : dto.getAnswers()) {
            QuestionDTO question = qMap.get(answer.getQuestionId());
            if (question == null) {
                throw new BadRequestException(ExamErrorInfo.QUESTION_NOT_EXISTS);
            }

            boolean correct = question.getAnswer().equals(answer.getAnswer());
            if (correct){
                totalScore += question.getScore();
            }
            answers.add(
                    new ExamRecordQuestion()
                            .setExamRecordId(examId)
                            .setUserId(userId)
                            .setQuestionId(answer.getQuestionId())
                            .setAnswer(answer.getAnswer())
                            .setCorrect(correct)
                            .setScore(correct ? question.getScore() : 0)
            );
        }


        // 3. 幂等 + 更新考试记录,考试得分/答题信息保存到数据库
        // 用 findAndModify 原子更新:status 从1(答题中)→2(已交卷),条件是 status=1
        // 并发/重复提交时,第二次 matchedCount=0 →拒绝,天然防并发
        Query query = new Query(
                Criteria.where("_id").is(examId)
                        .and("status").is(ExamStatus.ANSWERING.getValue())
        );
        Update update = new Update()
                .set("status", ExamStatus.SUBMITTED.getValue())
                .set("score", totalScore)
                .set("endTime", LocalDateTime.now());
        UpdateResult result = mongoTemplate.updateFirst(query, update, ExamRecord.class);
        if (result.getMatchedCount() == 0){
            throw new BizIllegalException(ExamErrorInfo.EXAM_ALREADY_SUBMITTED);
        }

        //4. 保存答案明细
        mongoTemplate.insertAll(answers);

        // 5. 通知到学习微服务，新增一条学习记录
        LearningRecordFormDTO msgDTO = new LearningRecordFormDTO();
        msgDTO.setSectionType(SectionType.EXAM.getValue());
        msgDTO.setUserId(userId);
        msgDTO.setLessonId(examRecord.getLessonId());
        msgDTO.setSectionId(examRecord.getSectionId());
        msgDTO.setCommitTime(LocalDateTime.now());
        rabbitMqHelper.send(
                MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.EXAM_SUBMIT,
                msgDTO
        );
    }

    // 我的考试列表
    @Override
    public PageDTO<ExamRecord> getMyExamPage(PageQuery query) {
        // 构建查询条件,按当前用户过滤
        Query mongoQuery = new Query(Criteria.where("userId").is(UserContext.getUser()));

        // 先统计总数,count只带条件,忽略分页
        long total = mongoTemplate.count(mongoQuery, ExamRecord.class);

        // 分页 + 开始时间倒叙
        mongoQuery.skip(query.from())
                .limit(query.getPageSize())
                .with(Sort.by(Sort.Direction.DESC, "startTime"));

        List<ExamRecord> records = mongoTemplate.find(mongoQuery, ExamRecord.class);
        long pages = (total + query.getPageSize() - 1) / query.getPageSize();
        if (CollUtils.isEmpty(records))
            return PageDTO.empty(total, pages);

        return new PageDTO<>(total, pages, records);
    }

    // 考试详情
    @Override
    public List<ExamRecordQuestionVO> getExamDetailById(String id) {
        // 1. 根据考试记录id查询所有答题明细
        Query query = new Query(Criteria.where("examRecordId").is(id));
        List<ExamRecordQuestion> records = mongoTemplate.find(query, ExamRecordQuestion.class);

        // 2. 得到所有的题目id,去mysql查询题目 answer analysis options score
        List<Long> qIds = records.stream().map(ExamRecordQuestion::getQuestionId)
                .collect(Collectors.toList());
        List<QuestionDTO> question = questionService.queryQuestionByIds(qIds);
        Map<Long, QuestionDTO> qMap = question.stream()
                .collect(Collectors.toMap(QuestionDTO::getId, q -> q));

        // 3. 组装ExamRecordQuestionVO,
        return records.stream().map(r -> {
            ExamRecordQuestionVO vo = new ExamRecordQuestionVO();
            vo.setAnswer(r.getAnswer());
            vo.setCorrect(r.getCorrect());
            vo.setComment(r.getComment());
            vo.setScore(r.getScore());
            vo.setQuestion(BeanUtils.copyProperties(qMap.get(r.getQuestionId()), QuestionAnswerVO.class));
            return vo;
        }).collect(Collectors.toList());
    }
}
