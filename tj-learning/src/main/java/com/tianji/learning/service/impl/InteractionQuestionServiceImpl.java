package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
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
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

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

    // TODO 管理端根据id查询互动问题
    @Override
    public QuestionAdminVO queryQuestionAdminById(Long id) {
        InteractionQuestion question = getById(id);
        if (question != null) {
            return BeanUtils.copyBean(question, QuestionAdminVO.class);
        }
        return null;
    }


}
