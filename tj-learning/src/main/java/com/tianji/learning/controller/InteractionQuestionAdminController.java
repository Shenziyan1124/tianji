package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequestMapping("/admin/questions")
@Api(tags = "互动问题的回答或评论")
@Slf4j
@RequiredArgsConstructor
public class InteractionQuestionAdminController {

    private final IInteractionQuestionService interactionQuestionService;

    @GetMapping("/page")
    @ApiOperation(value = "管理端分页查询互动问题")
    public PageDTO<QuestionAdminVO> queryQuestionAdminPage(QuestionAdminPageQuery query){
        return interactionQuestionService.queryQuestionAdminPage(query);
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "管理端根据id查询问题详情")
    public QuestionAdminVO queryQuestionAdminById(@PathVariable Long id){
        return interactionQuestionService.queryQuestionAdminById(id);
    }

    @PutMapping("/{id}/hidden/{hidden}")
    @ApiOperation(value = "管理端根据id隐藏或显示互动问题")
    public void updateQuestionHidden(@PathVariable Long id, @PathVariable Boolean hidden){
        interactionQuestionService.updateQuestionAdminHidden(id,hidden);
    }
}
