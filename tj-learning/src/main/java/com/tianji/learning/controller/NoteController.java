package com.tianji.learning.controller;


import cn.hutool.db.PageResult;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.NoteFormDTO;
import com.tianji.learning.domain.query.NotePageQuery;
import com.tianji.learning.domain.vo.NoteVO;
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
@RequestMapping("/notes")
@Api(tags = "笔记相关接口")
@RequiredArgsConstructor
public class NoteController {

    private final INoteService noteService;

    @PostMapping
    @ApiOperation(value = "新增笔记")
    public void saveNote(@RequestBody @Validated NoteFormDTO dto) {
        noteService.saveNote(dto);
    }

    @PostMapping("/gathers/{id}")
    @ApiOperation(value = "采集笔记")
    public void collectNote(@PathVariable("id") Long id) {
        noteService.collectNote(id);
    }

    @DeleteMapping("/gathers/{id}")
    @ApiOperation(value = "取消采集")
    public void cancelCollectNote(@PathVariable("id") Long id) {
        noteService.cancelCollectNote(id);
    }

    @PutMapping("/{id}")
    @ApiOperation(value = "修改笔记")
    public void updateNote(@PathVariable("id") Long id,@RequestBody NoteFormDTO dto){
        noteService.updateNote(id,dto);
    }

    @DeleteMapping("/{id}")
    @ApiOperation(value = "删除笔记")
    public void deleteNote(@PathVariable("id") Long id){
        noteService.deleteNote(id);
    }

    @GetMapping("/page")
    @ApiOperation(value = "分页查询笔记")
    public PageDTO<NoteVO> queryNoteList(NotePageQuery query){
        return noteService.queryNoteList(query);
    }
}
