package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.remark.RemarkClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CatalogueDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.NoteFormDTO;
import com.tianji.learning.domain.po.Note;
import com.tianji.learning.domain.po.NoteCollect;
import com.tianji.learning.domain.query.NoteAdminQuery;
import com.tianji.learning.domain.query.NotePageQuery;
import com.tianji.learning.domain.vo.NoteAdminVO;
import com.tianji.learning.domain.vo.NoteVO;
import com.tianji.learning.mapper.NoteCollectMapper;
import com.tianji.learning.mapper.NoteMapper;
import com.tianji.learning.service.INoteAdminService;
import com.tianji.learning.service.INoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <p>
 * 笔记表 服务实现类
 * </p>
 *
 * @author SHEN
 * @since 2026-08-03
 */
@Service
@RequiredArgsConstructor
public class NoteAdminServiceImpl extends ServiceImpl<NoteMapper, Note> implements INoteAdminService {

    private final NoteCollectMapper collectMapper;
    private final UserClient userClient;
    private final RemarkClient remarkClient;
    private final CourseClient courseClient;


    // 管理端笔记列表
    @Override
    public PageDTO<NoteAdminVO> queryAdminNoteList(NoteAdminQuery query) {
        LocalDateTime begin = query.getBeginTime();
        LocalDateTime end = query.getEndTime();

        // 0.按课程名称查课程id列表（先于分页查询)
        List<Long> courseIds = null;
        if (StringUtils.isNotBlank(query.getCourseName())) {
            courseIds = courseClient.queryCourseIdByName(query.getCourseName());
            if (CollUtils.isEmpty(courseIds)) return PageDTO.empty(0L, 0L);
        }

        // 1. 构造查询条件
        Page<Note> page = lambdaQuery()
                .eq(query.getHidden() != null, Note::getHidden, query.getHidden()) // 存在,查询对应的笔记
                .ge(begin != null, Note::getCreateTime, begin)  // ge 大于等于
                .le(end != null, Note::getCreateTime, end)  // le 小于等于
                .in(CollUtils.isNotEmpty(courseIds), Note::getCourseId, courseIds) // in 包含
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<Note> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }

        // 2. po转vo
        // 2.1. copyList：基础字段自动复制（id/content/createTime/hidden 都同名，直接过来）
        List<NoteAdminVO> vos = BeanUtils.copyList(records, NoteAdminVO.class);

        // 2.2. 获取课程信息
        List<Long> courseIdList = records.stream().map(Note::getCourseId).distinct().collect(Collectors.toList()); // distinct去重
        Map<Long, CourseFullInfoDTO> courseMap = new HashMap<>(courseIdList.size());
        for (Long courseId : courseIdList) {
            CourseFullInfoDTO course = courseClient.getCourseInfoById(courseId, true, false);
            if (course != null) courseMap.put(courseId, course);
        }

        // 2.3. 遍历目录,查询章id->章name,节id->节name
        Map<Long, String> chapterNameMap = new HashMap<>(); // 章map
        Map<Long, String> sectionNameMap = new HashMap<>(); // 节map
        // 遍历课程
        for (CourseFullInfoDTO course : courseMap.values()) {
            // 判断章是否存在,不存在跳过
            if (CollUtils.isEmpty(course.getChapters())) continue;
            // 遍历章
            for (CatalogueDTO chapter : course.getChapters()) {
                // 通过章id查询章名称
                chapterNameMap.put(chapter.getId(), chapter.getName());
                // 判断节是否存在,不存在跳过
                if (CollUtils.isEmpty(chapter.getSections())) continue;
                // 遍历节
                for (CatalogueDTO section : chapter.getSections()) {
                    // 通过节id查询节名称
                    sectionNameMap.put(section.getId(), section.getName());
                }
            }
        }

        // 2.4. 获取用户信息
        List<Long> userIdlist = records.stream().map(Note::getUserId).distinct().collect(Collectors.toList());
        List<UserDTO> users = userClient.queryUserByIds(userIdlist);
        Map<Long, UserDTO> userMap = CollUtils.isEmpty(users) ? CollUtils.emptyMap()
                : users.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));

        // 2.5. 引用次数,查采集表group by
        List<Long> noteIds = records.stream().map(Note::getId).collect(Collectors.toList());
        Map<Long, Long> gatheredMap = collectMapper.selectList(
                        new LambdaQueryWrapper<NoteCollect>()
                                .in(NoteCollect::getNoteId, noteIds))
                .stream()
                .collect(Collectors.groupingBy(NoteCollect::getNoteId, Collectors.counting()));

        for (int i = 0; i < records.size(); i++) {
            Note note = records.get(i); // 笔记
            NoteAdminVO vo = vos.get(i); // vo
            // 课程
            CourseFullInfoDTO course = courseMap.get(note.getCourseId());
            if (course != null) vo.setCourseName(course.getName());

            // 章/节
            vo.setChapterName(chapterNameMap.get(note.getChapterId()));
            vo.setSectionName(sectionNameMap.get(note.getSectionId()));

            // 姓名
            UserDTO user = userMap.get(note.getUserId());
            if (user != null) vo.setAuthorName(user.getName());

            // 引用次数
            vo.setGatheredTimes(gatheredMap.getOrDefault(note.getId(), 0L).intValue());
        }

        return PageDTO.of(page, vos);
    }
}
