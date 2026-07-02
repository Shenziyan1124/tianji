package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Delete;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 学生课程表 前端控制器
 * </p>
 *
 * @author SHEN
 * @since 2026-06-30
 */
@RestController
@RequestMapping("/lessons")
@Api(tags = "我的课表相关接口")
@RequiredArgsConstructor
public class LearningLessonController {

    private final ILearningLessonService lessonService;

    @GetMapping("/page")
    @ApiOperation("分页查询我的课表")
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery  pageQuery){
        return  lessonService.queryMyLessons(pageQuery);
    }

    @GetMapping("/now")
    @ApiOperation("查询我正在学习的状态")
    public LearningLessonVO getMyCurrentLessons(){
        return lessonService.getMyCurrentLessons();
    }

    @GetMapping("/{courseId}")
    @ApiOperation("查询用户课程表中指定课程状态")
    public LearningLessonVO getLessonByCourseId(@PathVariable Long courseId){
        return lessonService.getLessonByCourseId(courseId);
    }

    @GetMapping("/{courseId}/valid")
    @ApiOperation("检查课程是否有效")
    public Long isLessonValid(@PathVariable Long courseId){
        return lessonService.isLessonValid(courseId);
    }

    @GetMapping("/{courseId}/count")
    @ApiOperation("统计课程的报名人数")
    public Integer getLessonCount(@PathVariable Long courseId){
        return lessonService.getLessonCount(courseId);
    }


    @DeleteMapping("/{courseId}")
    @ApiOperation("删除失效的课程")
    public void deleteLesson(@PathVariable Long courseId){
        UserContext.getUser()
        lessonService.deleteNoValidLesson(userId,courseId);
    }

}
