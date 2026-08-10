package com.tianji.exam.controller;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.exam.domain.dto.ExamStartDTO;
import com.tianji.exam.domain.dto.ExamSubmitDTO;
import com.tianji.exam.domain.po.ExamRecord;
import com.tianji.exam.domain.vo.ExamRecordQuestionVO;
import com.tianji.exam.domain.vo.ExamVO;
import com.tianji.exam.service.IExamService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

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

    @ApiOperation("提交考试结果")
    @PostMapping("/details")
    public void submitExam(ExamSubmitDTO dto){
        examService.submitExam(dto);
    }

    @GetMapping("/page")
    @ApiOperation("分页查询考试记录")
    public PageDTO<ExamRecord> getMyExamPage(PageQuery query) {
        return examService.getMyExamPage(query);
    }

    @GetMapping("/{id}")
    @ApiOperation("根据id获取考试详情")
    public List<ExamRecordQuestionVO> getExamDetailById(@PathVariable("id") String id) {
        return examService.getExamDetailById(id);
    }
}
