package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.NoteFormDTO;
import com.tianji.learning.domain.query.NoteAdminQuery;
import com.tianji.learning.domain.query.NotePageQuery;
import com.tianji.learning.domain.vo.NoteAdminVO;
import com.tianji.learning.domain.vo.NoteDetailVO;
import com.tianji.learning.domain.vo.NoteVO;
import com.tianji.learning.service.INoteAdminService;
import com.tianji.learning.service.INoteService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 笔记表 前端控制器
 * </p>
 *
 * @author SHEN
 * @since 2026-08-03
 */
@RestController
@RequestMapping("/admin/notes")
@Api(tags = "笔记相关接口")
@RequiredArgsConstructor
public class NoteAdminController {

    private final INoteAdminService noteService;

    @GetMapping("/page")
    @ApiOperation(value = "管理端-分页查询笔记")
    public PageDTO<NoteAdminVO> queryAdminNoteList(NoteAdminQuery query){
        return noteService.queryAdminNoteList(query);
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "管理端-查询笔记详情")
    public NoteDetailVO queryAdminNoteDetailByID(@PathVariable Long id){
        return noteService.queryAdminNoteDetailByID(id);
    }


}
