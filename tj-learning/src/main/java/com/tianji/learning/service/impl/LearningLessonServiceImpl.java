package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.IdAndNumDTO;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.*;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LearningPlanVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * <p>
 * 学生课程表 服务实现类
 * </p>
 *
 * @author SHEN
 * @since 2026-06-30
 */
@Service
@RequiredArgsConstructor
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {

    private final CourseClient  courseClient;
    private final CatalogueClient catalogueClient;
    private final LearningRecordMapper learningRecordMapper;

    // 添加到我的课程中
    @Override
    @Transactional

    public void addUserLesson(Long userId, List<Long> courseIds) {

        //1.查询课程有效期
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(cInfoList)) {
            log.error("课程信息不存在,无法添加");
            return;
        }
        //2.循环遍历
        List<LearningLesson> list = new ArrayList<>(cInfoList.size());
        for (CourseSimpleInfoDTO cInfo : cInfoList) {
            LearningLesson lesson = new LearningLesson();
            //2.1获取过期时间
            Integer validDuration = cInfo.getValidDuration();
            if (validDuration != null && validDuration > 0) {
                LocalDateTime now = LocalDateTime.now();
                lesson.setCreateTime(now);
                lesson.setExpireTime(now.plusMonths(validDuration));
            }
            //2.2填入信息
            lesson.setUserId(userId);
            lesson.setCourseId(cInfo.getId());

            list.add(lesson);

        }
        //3.批量新增
        saveBatch(list);
    }


    // 分页查询我的课表
    @Override
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery pageQuery) {
        //1.查询用户信息
        Long userId = UserContext.getUser();
        //2.分页查询
        Page<LearningLesson> page = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)  // where user_id = #{userId}
                .page(pageQuery.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }

        //3.课程查询
        Map<Long, CourseSimpleInfoDTO> cMap = queryCourseSimpleInfoList(records);
        //4.组装,返回vo
        List<LearningLessonVO> list = new ArrayList<>(records.size());
        //4.1 循环遍历, 把learninglesson转vo
        for (LearningLesson lesson : records) {
            LearningLessonVO learningLessonVO = BeanUtils.copyBean(lesson, LearningLessonVO.class);

            CourseSimpleInfoDTO cInfo = cMap.get(lesson.getCourseId());
            learningLessonVO.setCourseName(cInfo.getName());
            learningLessonVO.setCourseCoverUrl(cInfo.getCoverUrl());
            learningLessonVO.setSections(cInfo.getSectionNum());

            list.add(learningLessonVO);
        }


        // new PageDTO<>(page.getTotal(), page.getPages(), list);
        return PageDTO.of(page, list);
    }

    private Map<Long, CourseSimpleInfoDTO> queryCourseSimpleInfoList(List<LearningLesson> records) {
        Set<Long> cids = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(cids);
        if (CollUtils.isEmpty(cInfoList)) {
           throw new BadRequestException("课程不存在!");
        }
        //3.3 把课程合计处理成map,key是courseid,value是course本身
        Map<Long, CourseSimpleInfoDTO> cMap =
                cInfoList.stream()
                        .collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        return cMap;
    }

    // 查询我正在学习的课程
    @Override
    public LearningLessonVO getMyCurrentLessons() {

        //1.当前用户
        Long userId = UserContext.getUser();

        //2. 查找正在学习中的课程
        // select * form learning_lesson
        // where user_id = {userId} and status = 1 order by latest_learn_time limit 1
        LearningLesson learningLesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getStatus, LessonStatus.LEARNING)
                .orderByDesc(LearningLesson::getLatestLearnTime)
                .last("limit 1")
                .one();
        if (learningLesson == null) return null;

        //3. 远程调用 -- 获取课程中的图片 章节 课程名...
        CourseFullInfoDTO cInfo = courseClient.getCourseInfoById(learningLesson.getCourseId(),false,false);
        if(cInfo == null){
            throw new BizIllegalException("课程不存在");
        }

        //4. 获取正在学习中的总课程数
        Integer count = lambdaQuery().eq(LearningLesson::getUserId, userId).count();

        //5. 获取对应的章节名称和编号
        Long latestSectionId = learningLesson.getLatestSectionId(); // 最近章节的id
        List<CataSimpleInfoDTO> cataSimpleInfoDTOS =
                catalogueClient.batchQueryCatalogue(CollUtils.singletonList(latestSectionId));
        if (CollUtils.isEmpty(cataSimpleInfoDTOS)){
            throw new BizIllegalException("小节不存在");
        }

        //6. 封装vo
        LearningLessonVO vo = BeanUtils.copyProperties(learningLesson, LearningLessonVO.class);
        vo.setCourseName(cInfo.getName());
        vo.setCourseCoverUrl(cInfo.getCoverUrl());
        vo.setSections(cInfo.getSectionNum());
        vo.setCourseAmount(count);
        CataSimpleInfoDTO cataSimpleInfoDTO = cataSimpleInfoDTOS.get(0);
        vo.setLatestSectionName(cataSimpleInfoDTO.getName());
        vo.setLatestSectionIndex(cataSimpleInfoDTO.getCIndex());


        return vo;
    }

    // 查询课程的状态
    @Override
    public LearningLessonVO getLessonByCourseId(Long courseId) {
        //1.当前用户
        Long userId = UserContext.getUser();

        LearningLesson learningLesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();

        if (learningLesson == null) return null;

        return BeanUtils.copyProperties(learningLesson, LearningLessonVO.class);
    }

    // 检查课程是否有效
    @Override
    public Long isLessonValid(Long courseId) {
        Long userId = UserContext.getUser();

        LearningLesson learningLesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if(learningLesson == null) return null;

        LocalDateTime expireTime = learningLesson.getExpireTime();
        LocalDateTime now = LocalDateTime.now();
        if (expireTime != null && now.isAfter(expireTime)){
            return null;
        }

        return learningLesson.getId();
    }

    // 查询某课程的人数
    @Override
    public Integer getLessonCount(Long courseId) {
        return lambdaQuery().eq(LearningLesson::getCourseId,courseId).count();
    }

    // 删除过期的课程
    @Override
    public void deleteNoValidLesson(Long userId, Long courseId) {
        lambdaUpdate()
                .eq(LearningLesson::getUserId,userId)
                .eq(LearningLesson::getCourseId,courseId)
                .eq(LearningLesson::getStatus, LessonStatus.EXPIRED)
                .remove();
    }

    // 通过userId 和 courseId 查询课表
    @Override
    public LearningLesson queryByUserIdAndCourseId(Long userId, Long courseId) {
        return lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
    }

    // 创建学习计划
    @Override
    public void createLessonPlan(LearningPlanDTO dto) {
        Long userId = UserContext.getUser();
        Long courseId = dto.getCourseId();
        Integer freq = dto.getFreq();
        // 查询课表中课程的有关数据
        LearningLesson learningLesson = queryByUserIdAndCourseId(userId, courseId);
//        if (learningLesson == null){
//            throw new BadRequestException("课程不存在");
//        }
        AssertUtils.isNotNull(learningLesson, "课程不存在");

        // 修改数据
        LearningLesson l = new LearningLesson();
        l.setCourseId(learningLesson.getId());
        l.setWeekFreq(freq);
        if (learningLesson.getPlanStatus() == PlanStatus.NO_PLAN){
            l.setPlanStatus(PlanStatus.PLAN_RUNNING);
        }
        updateById(l);


    }

    // 查询学习计划
    @Override
    public LearningPlanPageVO queryLessonPlans(PageQuery pageQuery) {
        LearningPlanPageVO vo = new LearningPlanPageVO();

        // 1.获取当前用户
        Long userId = UserContext.getUser();

        // 2.获取本周的日期
        LocalDate now = LocalDate.now();
        LocalDateTime begin = DateUtils.getWeekBeginTime(now);
        LocalDateTime end = DateUtils.getWeekEndTime(now);

        // 3.获取总的统计数据
        // 3.1本周已学小节数量
        Integer weekFinished = learningRecordMapper.selectCount(
                new LambdaQueryWrapper<LearningRecord>()
                        .eq(LearningRecord::getUserId, userId)
                        .eq(LearningRecord::getFinished, true)
                        .between(LearningRecord::getFinishTime, begin, end)
        );
        vo.setWeekFinished(weekFinished);
        // 3.2本周总小节数量
        Integer weekTotal = getBaseMapper().queryTotalPlan(userId);
        vo.setWeekTotalPlan(weekTotal);
        // TODO 3.3积分统计

        // 4.获取列表数据
        // 4.1 分页查询课表信息以及学习计划信息
        Page<LearningLesson> page = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .in(LearningLesson::getStatus, LessonStatus.NOT_BEGIN, LessonStatus.LEARNING)
                .page(pageQuery.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();
        if (CollUtils.isEmpty(records)) return vo.pageInfo(PageDTO.empty(page));

        // 4.2 查询课表对应的信息 title...
        Map<Long, CourseSimpleInfoDTO> cMap = queryCourseSimpleInfoList(records);
        // 4.3 统计每个课表本周已学的数量
        List<IdAndNumDTO> list = learningRecordMapper.countLearnedSections(userId,begin,end);
        Map<Long, Integer> countMap = IdAndNumDTO.toMap(list);

        // 4.4 组装vo
        List<LearningPlanVO> voList = new ArrayList<>(records.size());
        records.forEach(r -> {
            // 4.4.1 拷贝基础属性信息到vo
            LearningPlanVO learningPlanVO = BeanUtils.copyProperties(r, LearningPlanVO.class);
            // 4.4.2 填充课程基本信息
            CourseSimpleInfoDTO courseId = cMap.get(r.getCourseId());
            if (courseId != null){
                learningPlanVO.setCourseName(courseId.getName());
                learningPlanVO.setSections(courseId.getSectionNum());
            }
            // 4.4.3 填充本周已学数量
            learningPlanVO.setWeekLearnedSections(countMap.getOrDefault(r.getId(), 0));

            voList.add(learningPlanVO);
        });
        return vo.pageInfo(page.getTotal(),page.getPages(),voList);
    }
}
