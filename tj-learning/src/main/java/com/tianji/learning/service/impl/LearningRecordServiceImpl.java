package com.tianji.learning.service.impl;

import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.api.dto.leanring.LearningRecordFormDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * <p>
 * 学习记录表 服务实现类
 * </p>
 *
 * @author SHEN
 * @since 2026-07-02
 */
@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord>
        implements ILearningRecordService {

    private final ILearningLessonService lessonService;

    private final CourseClient courseClient;

    // 查询指定课程的学习记录
    @Override
    public LearningLessonDTO queryLearningRecordByCourse(Long courseId) {
        //1.获取登录用户
        Long userId = UserContext.getUser();
        //2.查询课表
        LearningLesson learningLesson = lessonService.queryByUserIdAndCourseId(userId, courseId);
        if (learningLesson == null) {
            return null;
        }
        //3.查询学习记录
        List<LearningRecord> records = lambdaQuery().eq(LearningRecord::getLessonId, learningLesson.getId()).list();

        //4.封装返回vo
        LearningLessonDTO learningLessonDTO = new LearningLessonDTO();
        learningLessonDTO.setId(learningLesson.getId());
        learningLessonDTO.setLatestSectionId(learningLesson.getLatestSectionId());
        learningLessonDTO.setRecords(BeanUtils.copyList(records, LearningRecordDTO.class));

        return learningLessonDTO;
    }

    // 保存学习记录
    @Override
    @Transactional
    public void saveLearningRecord(LearningRecordFormDTO dto) {
        // 1.获取登录用户
        Long userId = UserContext.getUser();
        // 2. 处理学习记录
        Boolean finished = false;
        if (dto.getSectionType() == SectionType.VIDEO.getValue()) {
            // 2.1 处理视频
            finished = handleVideoRecord(userId, dto);
        } else {
            // 2.2 处理考试
            finished = handleExamRecord(userId, dto);
        }
        // 3. 处理课表记录
        handleLearningLessonChanges(dto, finished);
    }

    // 处理考试记录
    private Boolean handleExamRecord(Long userId, LearningRecordFormDTO dto) {
        // 转dto为po
        LearningRecord learningRecord = BeanUtils.copyBean(dto, LearningRecord.class);
        // 填入缺少的数据
        learningRecord.setUserId(userId);
        learningRecord.setFinished(true);
        learningRecord.setFinishTime(dto.getCommitTime());
        // 写入数据库
        boolean success = save(learningRecord);
        if (!success) {
            throw new DbException("新增考试记录失败");
        }
        return true;
    }

    private Boolean handleVideoRecord(Long userId, LearningRecordFormDTO dto) {
        // 1. 查询旧的学习记录
        LearningRecord old = lambdaQuery()
                .eq(LearningRecord::getLessonId, dto.getLessonId())
                .eq(LearningRecord::getSectionId, dto.getSectionId())
                .one();
        // 2. 判断是否存在
        if (old == null) {
            // 3. 不存在 新增
            // 3.1 转dto为po
            LearningRecord learningRecord = BeanUtils.copyBean(dto, LearningRecord.class);
            // 3.2 填入缺少的数据
            learningRecord.setUserId(userId);
            // 3.3 写入数据库
            boolean success = save(learningRecord);
            if (!success) {
                throw new DbException("新增学习记录失败");
            }
            return false;
        }
        // 4. 存在 更新
        // 4.1 判断是否第一次完成
        boolean firstFinished = !old.getFinished() && dto.getMoment() * 2 >= dto.getDuration();
        boolean success = lambdaUpdate()
                .set(LearningRecord::getMoment, dto.getMoment())
                .set(firstFinished, LearningRecord::getFinished, true)
                .set(firstFinished, LearningRecord::getFinishTime, dto.getCommitTime())
                .eq(LearningRecord::getId, old.getId())
                .update();
        if (!success) {
            throw new DbException("更新学习记录失败");
        }
        return firstFinished;
    }

    private void handleLearningLessonChanges(LearningRecordFormDTO dto, Boolean finished) {
        // 1. 查询课表
        LearningLesson lesson = lessonService.getById(dto.getLessonId());
        if (lesson == null) {
            throw new BizIllegalException("课程不存在,无法更新数据");
        }
        // 2. 判断是否有新的小节学完
        Boolean allLearned = false;
        if (finished) {
            // 3. 有->查询数据课程
            CourseFullInfoDTO courseInfoById = courseClient.getCourseInfoById(lesson.getCourseId(),false,false);
            if (courseInfoById == null) {
                throw new BizIllegalException("课程不存在,无法更新数据");
            }
            // 4. 比较课程是否全部学完, 已学习小节>=课程总小节
            allLearned = lesson.getLearnedSections() + 1 >= courseInfoById.getSectionNum();
        }
        // 5. 更新课表
        boolean success = lessonService.lambdaUpdate()
                .set(lesson.getLearnedSections() == 0,LearningLesson::getStatus,LessonStatus.LEARNING.getValue())
                .set(allLearned, LearningLesson::getStatus, LessonStatus.FINISHED.getValue())
                .set(!finished,LearningLesson::getLatestLearnTime,dto.getCommitTime())
                .set(!finished,LearningLesson::getLatestSectionId,dto.getSectionId())
                .set(finished,LearningLesson::getLearnedSections, lesson.getLearnedSections() + 1)
                .eq(LearningLesson::getId,lesson.getId())
                .update();
        if (!success){
            throw new DbException("更新课表失败");
        }
    }
}
