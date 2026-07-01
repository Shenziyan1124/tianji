package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        Set<Long> cids = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(cids);
        if (CollUtils.isEmpty(cInfoList)) {
           throw new BadRequestException("课程不存在!");
        }
        //3.3 把课程合计处理成map,key是courseid,value是course本身
        Map<Long, CourseSimpleInfoDTO> cMap =
                cInfoList.stream()
                        .collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
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
}
