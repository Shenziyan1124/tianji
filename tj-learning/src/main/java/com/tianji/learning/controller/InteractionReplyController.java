package com.tianji.learning.controller;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.service.IInteractionQuestionService;
import com.tianji.learning.service.IInteractionReplyService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动问题的回答或评论 前端控制器
 * </p>
 *
 * @author SHEN
 * @since 2026-07-08
 */
@RestController
@RequestMapping("/replies")
@Api(tags = "评论相关接口")
@RequiredArgsConstructor
public class InteractionReplyController {

    private final IInteractionReplyService interactionReplyService;

    @PostMapping
    @ApiOperation("新增评论或回答")
    public void addReply(@RequestBody ReplyDTO dto) {
        interactionReplyService.addReply(dto);
    }


    @GetMapping("/page")
    @ApiOperation("分页查询回答或评论列表")
    public PageDTO<ReplyVO> getReplyList(ReplyPageQuery query) {
      return  interactionReplyService.getReplyList(query);
    }


}
