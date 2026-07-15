package com.tianji.learning.controller;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
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
@RequestMapping("/admin/replies")
@Api(tags = "管理端评论相关接口")
@RequiredArgsConstructor
public class InteractionReplyAdminController {

    private final IInteractionReplyService interactionReplyService;

    @GetMapping("/page")
    @ApiOperation("管理端分页查询回答或评论列表")
    public PageDTO<ReplyVO> getReplyAdminList(ReplyPageQuery query) {
      return  interactionReplyService.getReplyAdminList(query);
    }

    @PutMapping("/{id}/hidden/{hidden}")
    @ApiOperation("管理端隐藏或显示回答或评论")
    public void updateReplyHidden(@PathVariable("id") Long id, @PathVariable("hidden") Boolean hidden) {
        interactionReplyService.updateReplyHidden(id, hidden);
    }


    @GetMapping("/{id}")
    @ApiOperation("管理端获取回答或评论详情")
    public ReplyVO getReplyAdminDetail(@PathVariable("id") Long id) {
        return interactionReplyService.getReplyAdminDetail(id);
    }

}
