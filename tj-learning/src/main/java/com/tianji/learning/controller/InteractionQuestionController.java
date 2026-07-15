package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.service.IInteractionQuestionService;
import com.tianji.learning.service.impl.InteractionQuestionServiceImpl;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author SHEN
 * @since 2026-07-08
 */
@RestController
@RequestMapping("/questions")
@Api(tags = "互动问题的回答或评论")
@Slf4j
@RequiredArgsConstructor
public class InteractionQuestionController {

    private final IInteractionQuestionService interactionQuestionService;
    @PostMapping
    @ApiOperation(value = "新增互动问题")
    public void saveQuestion(@RequestBody QuestionFormDTO questionDTO){
        interactionQuestionService.saveQuestion(questionDTO);
    }

    @PutMapping("/{id}")
    @ApiOperation(value = "修改互动问题")
    public void updateQuestion(@PathVariable Long id, @RequestBody QuestionFormDTO questionDTO){
        interactionQuestionService.updateQuestion(id,questionDTO);
    }

    @DeleteMapping("/{id}")
    @ApiOperation(value = "删除互动问题")
    public void deleteQuestion(@PathVariable Long id){
        interactionQuestionService.deleteQuestion(id);
    }

    @GetMapping("/page")
    @ApiOperation(value = "分页查询互动问题")
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query){
        return interactionQuestionService.queryQuestionPage(query);
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "根据id查询互动问题")
    public QuestionVO queryQuestionById(@PathVariable Long id){
        return interactionQuestionService.queryQuestionById(id);
    }

}
