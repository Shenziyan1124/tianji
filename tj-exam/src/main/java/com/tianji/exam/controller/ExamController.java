package com.tianji.exam.controller;

import com.tianji.exam.domain.dto.ExamStartDTO;
import com.tianji.exam.domain.vo.ExamVO;
import com.tianji.exam.service.IExamService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * <p>
 * 考试 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2022-09-02
 */
@Api(tags = "考试相关接口")
@RequiredArgsConstructor
@RestController
@RequestMapping("/exams")
public class ExamController {

    private final IExamService examService;

    @ApiOperation("获取试题并开始考试")
    @PostMapping
    public ExamVO startExam(@Valid @RequestBody ExamStartDTO dto) {
        return examService.startExam(dto);
    }
}
