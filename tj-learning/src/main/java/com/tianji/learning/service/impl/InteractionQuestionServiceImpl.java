package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动提问的问题表 服务实现类
 * </p>
 *
 * @author SHEN
 * @since 2026-07-08
 */
@Service
@RequiredArgsConstructor
public class InteractionQuestionServiceImpl extends ServiceImpl<InteractionQuestionMapper, InteractionQuestion>
        implements IInteractionQuestionService {

    private final InteractionReplyServiceImpl replyService;
    private final UserClient userClient;

    private final CourseClient courseClient;
    private final SearchClient searchClient;
    private final CatalogueClient catalogueClient;
    private final CategoryCache categoryCache;


    // 新增互动问题
    @Override
    public void saveQuestion(QuestionFormDTO questionDTO) {
        Long userId = UserContext.getUser();
        // dto转po
        InteractionQuestion question = BeanUtils.copyBean(questionDTO, InteractionQuestion.class);
        question.setUserId(userId);
        // 写入数据库
        save(question);
    }

    // 修改互动问题
    @Override
    public void updateQuestion(Long id,QuestionFormDTO questionDTO) {
        // 校验参数
        if (StringUtils.isBlank(questionDTO.getTitle()) ||
                StringUtils.isBlank(questionDTO.getDescription()) ||
                questionDTO.getAnonymity() == null){
            throw new BadRequestException("标题、描述和匿名不能为空");
        }

        // 校验问题是否存在
        InteractionQuestion question = getById(id);
        if (question == null) {
            throw new BadRequestException("问题不存在");
        }

        // 校验用户是否是问题的作者
        Long userId = UserContext.getUser();
        if (!question.getUserId().equals(userId)) {
            throw new BadRequestException("无权限修改");
        }

        // 更新问题信
        BeanUtils.copyProperties(questionDTO, question);
        question.setUpdateTime(LocalDateTime.now());
        updateById(question);
    }

    // 删除互动问题
    @Override
    public void deleteQuestion(Long id) {
        // 校验问题是否存在
        InteractionQuestion question = getById(id);
        if (question == null) {
            throw new BadRequestException("问题不存在");
        }
        // 校验用户是否是问题的作者
        Long userId = UserContext.getUser();
        if (!question.getUserId().equals(userId)) {
            throw new BadRequestException("无权限删除");
        }
        // 删除问题
        removeById(id);
    }



    // 分页查询互动问题
    @Override
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query) {
        Long userId = UserContext.getUser();
        // 1.课程id和小节id不能为空
        Long courseId = query.getCourseId();
        Long sectionId = query.getSectionId();
        if (courseId == null && sectionId == null)
            throw new BadRequestException("课程id和小节id不能都为空");
        // 2.分页查询
        Page<InteractionQuestion> page = lambdaQuery()
                .select(InteractionQuestion.class, info -> !info.getProperty().equals("description"))
                .eq(Boolean.TRUE.equals(query.getOnlyMine()), InteractionQuestion::getUserId, userId)
                .eq(courseId != null, InteractionQuestion::getCourseId, courseId)
                .eq(sectionId != null, InteractionQuestion::getSectionId, sectionId)
                .eq(InteractionQuestion::getHidden, false)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());

        List<InteractionQuestion> records = page.getRecords();
        if (records.isEmpty()) return PageDTO.empty(page);

        // 3.根据id查询提问者和最近一次回答的信息
        // 3.1 得到问题中的提问者id和最近一次回答的id
        Set<Long> userIds = new HashSet<>();
        Set<Long> answerIds = new HashSet<>();
        records.forEach(record -> {
            if (!record.getAnonymity()) { // 只查询非匿名的用户
                userIds.add(record.getUserId());
            }
            answerIds.add(record.getLatestAnswerId());
        });

        // 3.2 根据id查询最近一次回答
        answerIds.remove(null);
        Map<Long, InteractionReply> replyMap = new HashMap<>(answerIds.size());
        if (CollUtils.isNotEmpty(answerIds)) {
            List<InteractionReply> replies = replyService.listByIds(answerIds);
            replies.forEach(reply -> {
                replyMap.put(reply.getId(), reply);
                if (!reply.getAnonymity()) {
                    userIds.add(reply.getUserId());
                }
            });
        }

        // 3.3 根据id查询用户信息(提问者)
        userIds.remove(null);
        Map<Long, UserDTO> userMap = new HashMap<>(userIds.size());
        if (CollUtils.isNotEmpty(userIds)) {
            List<UserDTO> users = userClient.queryUserByIds(userIds);
            users.forEach(user -> userMap.put(user.getId(), user));
        }

        // 4.封装vo
        List<QuestionVO> vos = new ArrayList<>(records.size());
        records.forEach(record -> {

            // 4.1 po转vo
            QuestionVO vo = BeanUtils.copyBean(record, QuestionVO.class);
            vos.add(vo);
            // 4.2 封装提问者信息
            if (!record.getAnonymity()) {
                UserDTO userDTO = userMap.get(record.getUserId());
                if (userDTO != null) {
                    vo.setUserName(userDTO.getName());
                    vo.setUserIcon(userDTO.getIcon());
                }
            }
            // 4.3 封装最近一次回答的信息
            InteractionReply latestReply = replyMap.get(record.getLatestAnswerId());
            if (latestReply != null) {
                vo.setLatestReplyContent(latestReply.getContent());
                if (!latestReply.getAnonymity()) {
                    UserDTO user = userMap.get(latestReply.getUserId());
                    if (user != null)
                        vo.setLatestReplyUser(user.getName());
                }
            }
        });


        return PageDTO.of(page, vos);
    }

    // 根据id查询互动问题
    @Override
    public QuestionVO queryQuestionById(Long id) {
        // 1.根据id查询数据
        InteractionQuestion question = getById(id);
        // 2.数据校验
        if (question == null || question.getHidden()) throw new BadRequestException("互动问题不存在");
        // 3.查询提问者信息
        UserDTO userDTO = null;
        if (!question.getAnonymity()) {
            userDTO = userClient.queryUserById(question.getUserId());
        }
        // 4.封装vo
        QuestionVO vo = BeanUtils.copyBean(question, QuestionVO.class);
        if (userDTO != null) {
            vo.setUserName(userDTO.getName());
            vo.setUserIcon(userDTO.getIcon());
        }
        return vo;
    }


    // 管理端分页查询互动问题
    @Override
    public PageDTO<QuestionAdminVO> queryQuestionAdminPage(QuestionAdminPageQuery query) {
        // 1. 处理课程名称,得到id
        List<Long> courseIds = null;
        if (StringUtils.isNotEmpty(query.getCourseName())) {
            courseIds = searchClient.queryCoursesIdByName(query.getCourseName());
            if (CollUtils.isEmpty(courseIds)) {
                return PageDTO.empty(0L, 0L);
            }
        }
        // 2. 分页查询
        Integer status = query.getStatus();
        LocalDateTime beginTime = query.getBeginTime();
        LocalDateTime endTime = query.getEndTime();
        Page<InteractionQuestion> page = lambdaQuery()
                .in(courseIds != null, InteractionQuestion::getCourseId, courseIds)
                .eq(status != null, InteractionQuestion::getStatus, status)
                .gt(beginTime != null, InteractionQuestion::getCreateTime, beginTime)
                .lt(endTime != null, InteractionQuestion::getCreateTime, endTime)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if (records.isEmpty()) return PageDTO.empty(page);

        // 3. 准备vo, 得到用户数据 课程  章/节数据
        Set<Long> userIds = new HashSet<>();
        Set<Long> cIds = new HashSet<>();
        Set<Long> cataIds = new HashSet<>();
        // 3.1 获取各种数据的id集合
        records.forEach(record -> {
            userIds.add(record.getUserId());
            cIds.add(record.getCourseId());
            cataIds.add(record.getChapterId());
            cataIds.add(record.getSectionId());
        });
        // 3.2 根据id查询用户
        List<UserDTO> users = userClient.queryUserByIds(userIds);
        Map<Long, UserDTO> userMap = new HashMap<>(users.size());
        if (CollUtils.isNotEmpty(userIds))
            users.forEach(user -> userMap.put(user.getId(), user));

        // 3.2 根据id查询课程
        List<CourseSimpleInfoDTO> courses = courseClient.getSimpleInfoList(cIds);
        Map<Long, CourseSimpleInfoDTO> courseMap = new HashMap<>(courses.size());
        if (CollUtils.isNotEmpty(courses))
            courses.forEach(course -> courseMap.put(course.getId(), course));

        // 3.3 根据id查询章节
        List<CataSimpleInfoDTO> chapters = catalogueClient.batchQueryCatalogue(cataIds);
        Map<Long, String> chapterMap = new HashMap<>(chapters.size());
        if (CollUtils.isNotEmpty(chapters))
            chapters.forEach(cata -> chapterMap.put(cata.getId(), cata.getName()));

        // 4. 封装vo
        List<QuestionAdminVO> vos = new ArrayList<>(records.size());
        records.forEach(r -> {
            // 4.1 po转vo
            QuestionAdminVO questionAdminVO = BeanUtils.copyBean(r, QuestionAdminVO.class);
            vos.add(questionAdminVO);
            // 4.2 添加其他信息
            // 用户
            UserDTO user = userMap.get(r.getUserId());
            if (user != null) {
                questionAdminVO.setUserName(user.getName());
            }

            // 课程信息以及分类信息
            CourseSimpleInfoDTO course = courseMap.get(r.getCourseId());
            if (course != null) {
                questionAdminVO.setCourseName(course.getName());
                questionAdminVO.setCategoryName(
                        categoryCache.getCategoryNames(course.getCategoryIds())
                );
            }

            // 章节信息
            questionAdminVO.setChapterName(chapterMap.getOrDefault(r.getChapterId(), ""));
            questionAdminVO.setSectionName(chapterMap.getOrDefault(r.getSectionId(), ""));
        });

        return PageDTO.of(page, vos);
    }

    // 管理端根据id查询问题详情
    @Override
    public QuestionAdminVO queryQuestionAdminById(Long id) {

        if (id == null) throw new BadRequestException("问题id不能为空");

        // 1. 根据id查询数据
        InteractionQuestion question = getById(id);
        if (question == null) {
            throw new RuntimeException("问题不存在");
        }

        // 2. 封装vo
        QuestionAdminVO vo = BeanUtils.copyBean(question, QuestionAdminVO.class);

        // 3. 添加用户信息
        UserDTO userDTO = userClient.queryUserById(question.getUserId());
        if (userDTO != null) {
            vo.setUserName(userDTO.getName());
            vo.setUserIcon(userDTO.getIcon());
        }

        // 4. 添加课程信息
        CourseFullInfoDTO courseDTO = courseClient.getCourseInfoById(question.getCourseId(),false,true);
        if (courseDTO != null) {
            vo.setCourseName(courseDTO.getName()); // 课程名称

            List<Long> teacherIds = courseDTO.getTeacherIds();
            if (CollUtils.isEmpty(teacherIds)) throw new RuntimeException("课程没有教师");

            List<UserDTO> teachers = userClient.queryUserByIds(teacherIds);
            if (CollUtils.isNotEmpty(teachers)) {
                vo.setTeacherNames(
                        teachers.stream()
                                .map(UserDTO::getName).collect(Collectors.joining("/")));
            }

            // 课程分类信息
            vo.setCategoryName(categoryCache.getCategoryNames(courseDTO.getCategoryIds()));
        }

        //  5. 添加章节信息
        Set<Long> chapterAndSectionIds = new HashSet<>();
        chapterAndSectionIds.add(question.getChapterId());
        chapterAndSectionIds.add(question.getSectionId());

        List<CataSimpleInfoDTO> cataList = catalogueClient.batchQueryCatalogue(chapterAndSectionIds);
        if (CollUtils.isNotEmpty(cataList)) {
            Map<Long, String> collect = cataList.stream().collect(Collectors.toMap(
                    CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName));
            vo.setChapterName(collect.getOrDefault(question.getChapterId(), ""));
            vo.setSectionName(collect.getOrDefault(question.getSectionId(), ""));
        }

        // 6. 查看完成,将问题修改状态
        question.setStatus(QuestionStatus.CHECKED);
        updateById(question);

        return vo;
    }


    // 管理端更新互动问题隐藏状态
    @Override
    public void updateQuestionAdminHidden(Long id, Boolean hidden) {
        InteractionQuestion question = getById(id);
        if (question == null) {
            throw new RuntimeException("问题不存在");
        }
        if (question.getHidden().equals(hidden)) {
            throw new RuntimeException("问题已经处于该状态");
        }

        question.setHidden(hidden);
        updateById(question);
    }




}
