package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.NoteFormDTO;
import com.tianji.learning.domain.po.Note;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.NotePageQuery;
import com.tianji.learning.domain.vo.NoteVO;

/**
 * <p>
 * 笔记表 服务类
 * </p>
 *
 * @author SHEN
 * @since 2026-08-03
 */
public interface INoteService extends IService<Note> {

    void saveNote(NoteFormDTO dto);

    void collectNote(Long id);

    void cancelCollectNote(Long id);

    void updateNote(Long id, NoteFormDTO dto);

    void deleteNote(Long id);

    PageDTO<NoteVO> queryNoteList(NotePageQuery query);
}
