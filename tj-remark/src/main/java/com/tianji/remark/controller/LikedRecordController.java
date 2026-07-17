package com.tianji.remark.controller;


import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.service.ILikedRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * 点赞记录表 前端控制器
 * </p>
 *
 * @author SHEN
 * @since 2026-07-16
 */
@RestController
@RequestMapping("/likes")
@RequiredArgsConstructor
@Api(tags = "点赞记录相关接口")
public class LikedRecordController {

    private final ILikedRecordService likedRecordService;

    @PostMapping
    @ApiOperation(value = "添加点赞记录", notes = "添加点赞记录")
    public void addLikeRecord(@RequestBody @Valid LikeRecordFormDTO dto) {
        likedRecordService.addLikeRecord(dto);
    }

    @GetMapping("/list")
    @ApiOperation(value = "查询指定业务id的点赞状态")
    public Set<Long> isBizLiked(@RequestParam("bizId") List<Long> bizIds) {
        return likedRecordService.isBizLiked(bizIds);
    }
}
