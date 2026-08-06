package com.tianji.learning.service.impl;

import cn.hutool.db.PageResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.remark.RemarkClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.NoteFormDTO;
import com.tianji.learning.domain.po.Note;
import com.tianji.learning.domain.po.NoteCollect;
import com.tianji.learning.domain.query.NotePageQuery;
import com.tianji.learning.domain.vo.NoteVO;
import com.tianji.learning.mapper.NoteCollectMapper;
import com.tianji.learning.mapper.NoteMapper;
import com.tianji.learning.service.INoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
public class NoteServiceImpl extends ServiceImpl<NoteMapper, Note> implements INoteService {

    private final NoteCollectMapper collectMapper;
    private final UserClient userClient;
    private final RemarkClient remarkClient;

    // 新增笔记
    @Override
    public void saveNote(NoteFormDTO dto) {
        // 1.获取登录用户id
        Long userId = UserContext.getUser();
        // 2.转dto为po，并填入用户id
        Note note = BeanUtils.copyBean(dto, Note.class);
        note.setUserId(userId);
        // 3.写入数据库
        boolean success = save(note);
        if (!success) {
            throw new DbException("新增笔记失败");
        }
    }

    // 采集笔记
    @Override
    public void collectNote(Long id) {
        // 1.判断笔记是否存在
        Note note = getById(id);
        if (note == null) {
            throw new BizIllegalException("笔记不存在");
        }
        // 2.获取登录用户id
        Long userId = UserContext.getUser();
        // 3.判断是否已经采集过，采集过直接返回
        Integer count = collectMapper.selectCount(new LambdaQueryWrapper<NoteCollect>()
                .eq(NoteCollect::getNoteId, id)
                .eq(NoteCollect::getUserId, userId));
        if (count > 0) {
            return;
        }
        // 4.新增采集记录
        NoteCollect collect = new NoteCollect();
        collect.setNoteId(id);
        collect.setUserId(userId);
        collectMapper.insert(collect);
    }

    // 取消采集
    @Override
    public void cancelCollectNote(Long id) {
        // 1.获取登录用户id
        Long userId = UserContext.getUser();
        // 2.删除采集记录
        collectMapper.delete(new LambdaQueryWrapper<NoteCollect>()
                .eq(NoteCollect::getNoteId, id)
                .eq(NoteCollect::getUserId, userId));
    }

    // 修改笔记
    @Override
    public void updateNote(Long id, NoteFormDTO dto) {
        // 1.判断笔记是否存在
        Note note = getById(id);
        if (note == null) {
            throw new BizIllegalException("笔记不存在");
        }
        // 1.判断用户是否是笔记的作者
        if (!note.getUserId().equals(UserContext.getUser())) {
            throw new BizIllegalException("您没有权限修改该笔记");
        }
        // 2.转dto为po
        Note update = BeanUtils.copyBean(dto, Note.class);
        update.setId(id);
        boolean success = updateById(update);
        if (!success) {
            throw new DbException("修改笔记失败");
        }
    }

    // 删除笔记
    @Override
    @Transactional
    public void deleteNote(Long id) {
        Long userId = UserContext.getUser();
        boolean success = lambdaUpdate()
                .eq(Note::getId, id)
                .eq(Note::getUserId, userId)
                .remove();

        if (!success) {
            throw new DbException("删除笔记失败");
        }

        // 这条笔记被删了，所有采集过它的人（包括别人）的引用都是脏数据，都要清除
        collectMapper.delete(new LambdaUpdateWrapper<NoteCollect>().eq(NoteCollect::getNoteId, id));

    }

    // 查询笔记列表
    @Override
    public PageDTO<NoteVO> queryNoteList(NotePageQuery query) {

        // 在分页查询之前
        if (query.getOnlyMine() == null) {
            throw new BadRequestException("onlyMine不能为空");
        }

        Long userId = UserContext.getUser();
        // 1.分页查询
        Page<Note> page = lambdaQuery()
                .eq(Note::getHidden, false) // 管理端隐藏的笔记用户端一律不可见
                .eq(Boolean.TRUE.equals(query.getOnlyMine()), Note::getUserId, userId)
                .eq(Boolean.FALSE.equals(query.getOnlyMine()), Note::getIsPrivate, false)
                .eq(query.getCourseId() != null, Note::getCourseId, query.getCourseId())
                .eq(query.getSectionId() != null, Note::getSectionId, query.getSectionId())
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<Note> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }

        // 2.转vo
        List<NoteVO> noteVOS = BeanUtils.copyList(records, NoteVO.class);

        // 3.获取当前用户采集了本页哪些笔记
        List<Long> noteIds = records.stream().map(Note::getId).collect(Collectors.toList());
        List<NoteCollect> noteCollects = collectMapper.selectList(
                new LambdaQueryWrapper<NoteCollect>().eq(NoteCollect::getUserId, userId).in(NoteCollect::getNoteId, noteIds));
        Set<Long> gatheredIds = noteCollects.stream().map(NoteCollect::getNoteId).collect(Collectors.toSet());

        // 5.获取用户信息 填入authorid authorName authorIcon
        List<Long> userIdlist = records.stream().map(Note::getUserId).collect(Collectors.toList());
        List<UserDTO> users = userClient.queryUserByIds(userIdlist);
        Map<Long, UserDTO> userMap = CollUtils.isEmpty(users) ?
                Collections.emptyMap()
                : users.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));

        // 6. 获取点赞数和当前用户是否点赞
        Set<Long> bizLiked = remarkClient.isBizLiked(noteIds);
        if (bizLiked == null) {
            bizLiked = Collections.emptySet();
        }

        // 6. 填入用户信息/采集信息
        Set<Long> finalBizLiked = bizLiked;
        noteVOS.forEach(vo -> {
            vo.setIsGathered(gatheredIds.contains(vo.getId())); // 不依赖用户信息,放在前边

            vo.setLiked(finalBizLiked.contains(vo.getId())); // 是否对某个点过赞

            UserDTO user = userMap.get(vo.getUserId());
            if (user == null) return; // 忽略没有用户信息
            vo.setAuthorId(user.getId());
            vo.setAuthorName(user.getName());
            vo.setAuthorIcon(user.getIcon());
        });

        return PageDTO.of(page, noteVOS);
    }


}
