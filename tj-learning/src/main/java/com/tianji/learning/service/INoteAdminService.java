package com.tianji.learning.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.NoteFormDTO;
import com.tianji.learning.domain.po.Note;
import com.tianji.learning.domain.query.NoteAdminQuery;
import com.tianji.learning.domain.query.NotePageQuery;
import com.tianji.learning.domain.vo.NoteAdminVO;
import com.tianji.learning.domain.vo.NoteDetailVO;
import com.tianji.learning.domain.vo.NoteVO;

/**
 * <p>
 * 笔记表 服务类
 * </p>
 *
 * @author SHEN
 * @since 2026-08-03
 */
public interface INoteAdminService extends IService<Note> {

    PageDTO<NoteAdminVO> queryAdminNoteList(NoteAdminQuery query);

    NoteDetailVO queryAdminNoteDetailByID(Long id);
}
