package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.remark.RemarkClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionReplyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.tianji.common.constants.Constant.DATA_FIELD_NAME_CREATE_TIME;
import static com.tianji.common.constants.Constant.DATA_FIELD_NAME_LIKED_TIME;

/**
 * <p>
 * 互动问题的回答或评论 服务实现类
 * </p>
 *
 * @author SHEN
 * @since 2026-07-08
 */
@Service
@RequiredArgsConstructor
public class InteractionReplyServiceImpl extends ServiceImpl<InteractionReplyMapper, InteractionReply>
        implements IInteractionReplyService {

    private final InteractionQuestionMapper questionMapper;

    private final UserClient userClient;
    private final RemarkClient remarkClient;


    // 新增评论或回答
    @Override
    public void addReply(ReplyDTO dto) {
        boolean needUpdate = false;  // ← 标记是否需要更新

        // 获取当前登录用户
        Long userId = UserContext.getUser();

        // 将DTO转换为PO
        InteractionReply reply = BeanUtils.copyBean(dto, InteractionReply.class);
        reply.setUserId(userId);
        save(reply);


        InteractionQuestion question = questionMapper.selectById(dto.getQuestionId());
        if (dto.getAnswerId() != null) {
            // 评论, 累加回答的评论次数
            InteractionReply byId = getById(dto.getAnswerId());
            byId.setReplyTimes(byId.getReplyTimes() + 1);
            updateById(byId);
        } else {
            // 是回答 记录最新回答id,更新回答次数
            question.setLatestAnswerId(reply.getId());
            question.setAnswerTimes(question.getAnswerTimes() + 1);
            needUpdate = true;
        }

        Boolean isStudent = dto.getIsStudent();
        if (isStudent != null && isStudent) {
            // 学生提交的回答，状态为未查看
            question.setStatus(QuestionStatus.UN_CHECK);
            needUpdate = true;
        }

        if (needUpdate) {
            questionMapper.updateById(question); // ← 只有真正改了才更新
        }
    }

    // 获取回答或评论列表（用户端）
    @Override
    public PageDTO<ReplyVO> getReplyList(ReplyPageQuery query) {
        // 用户端：过滤隐藏 + 检查匿名
        return queryReplyPage(
                query,
                wrapper ->
                        wrapper.eq(InteractionReply::getHidden, false),  // 额外过滤隐藏
                true);  // 需要检查匿名
    }

    // 获取回答或评论列表（管理端）
    @Override
    public PageDTO<ReplyVO> getReplyAdminList(ReplyPageQuery query) {
        // 管理端：不过滤隐藏 + 无视匿名
        return queryReplyPage(
                query, wrapper -> {
                },
                false);
    }


    // ========================================================================
    // 抽取的私有方法：分页查询回答或评论列表（用户端和管理端共用）
    // 通过 extraCondition 和 checkAnonymity 两个参数区分用户端/管理端行为：
    // - extraCondition: 用户端追加 .eq(hidden, false)；管理端不追加
    // - checkAnonymity: 用户端=true（匿名不暴露）；管理端=false（无视匿名）
    // ========================================================================
    private PageDTO<ReplyVO> queryReplyPage(
            ReplyPageQuery query,
            Consumer<LambdaQueryWrapper<InteractionReply>> extraCondition,
            boolean checkAnonymity
    ) {
        // ==================== 第1步：参数校验 ====================
        // 业务规则：问题id（questionId）和回答id（answerId）至少传一个
        // - 查回答列表 → 只传 questionId
        // - 查评论列表 → 只传 answerId（或两个都传）
        if (query.getAnswerId() == null && query.getQuestionId() == null) {
            throw new BadRequestException("问题id和回答id不能都为空");
        }

        // ==================== 第2步：拼接查询条件并分页查询 ====================
        Page<InteractionReply> page = lambdaQuery()
                // 如果传了 answerId，说明是查评论 → WHERE answer_id = ?
                .eq(query.getAnswerId() != null, InteractionReply::getAnswerId, query.getAnswerId())
                // 如果传了 questionId，说明是查回答 → WHERE question_id = ?
                .eq(query.getQuestionId() != null, InteractionReply::getQuestionId, query.getQuestionId())
                // 默认过滤已隐藏的记录（用户端用）→ WHERE hidden = false
                // 管理端通过 extraCondition 传入空 Consumer 来跳过此条件
                .eq(InteractionReply::getHidden, false)
                // 如果没传 answerId（即查回答列表），只查顶级回答，排除评论
                .isNull(query.getAnswerId() == null || query.getAnswerId() == 0, InteractionReply::getAnswerId)
                // 分页 + 排序：先按点赞数降序（热门在前），再按创建时间升序
                .page(query.toMpPage(
                        new OrderItem(DATA_FIELD_NAME_LIKED_TIME, false),
                        new OrderItem(DATA_FIELD_NAME_CREATE_TIME, true)
                ));
        // 应用调用方传入的额外条件
        // - 用户端：追加 .eq(hidden, false)（已在上方默认加了，这里再执行也无影响）
        // - 管理端：空操作，不追加任何条件
        //extraCondition.accept(null);
        List<InteractionReply> records = page.getRecords();
        // 如果没查到数据，直接返回空分页结果
        if (CollUtils.isEmpty(records)) return PageDTO.empty(0L, 0L);
        
        
        // ==================== 获取当前用户的点赞状态 ==================
        List<Long> bizIds = records.stream().map(InteractionReply::getId)
                .collect(Collectors.toList());
        Set<Long> likesBizIds = CollUtils.emptySet();
        Long userId = UserContext.getUser();
        if (userId != null && !bizIds.isEmpty()) {
            likesBizIds = remarkClient.isBizLiked(bizIds);
            if (likesBizIds == null) {
                likesBizIds = Collections.emptySet();
            }
        }

        // ==================== 第3步：遍历查询结果，收集需要查用户信息的ID ====================
        Set<Long> uids = new HashSet<>();           // 待查询的用户ID集合（最终传给 userClient）
        Set<Long> targetReplyIds = new HashSet<>(); // 被回复的回复ID（用于查出目标回复的作者）

        for (InteractionReply record : records) {
            // 判断是否需要跳过匿名用户：
            // - 用户端（checkAnonymity=true）：是匿名 → skip=true，不收集该用户信息
            // - 管理端（checkAnonymity=false）：永远 skip=false，无视匿名全部收集
            boolean skipAnonymity = checkAnonymity && record.getAnonymity();

            if (!skipAnonymity) {
                uids.add(record.getUserId());        // 本条回复/评论的作者ID
                uids.add(record.getTargetUserId());  // 被回复的目标用户ID（评论才有值）
            }

            // 如果本条回复是对另一条回复的回复（即有 targetReplyId）
            // 先记录下来，后面再查目标回复的作者是否匿名
            if (record.getTargetReplyId() != null && record.getTargetReplyId() > 0) {
                targetReplyIds.add(record.getTargetReplyId());
            }
        }

        // ==================== 第4步：补充目标回复的作者信息 ====================
        // 为什么要单独处理？
        // 场景举例：当前评论是匿名的，第3步跳过了 targetUserId 的收集。
        //          但被回复的那个人（目标回复的作者）可能不是匿名的，
        //          此时仍需显示目标用户名，所以得通过 targetReplyId 查出目标回复的作者。
        if (!targetReplyIds.isEmpty()) {
            // 4.1 根据回复ID批量查询目标回复
            List<InteractionReply> interactionReplies = listByIds(targetReplyIds);
            // 4.2 提取目标回复的作者ID
            Set<Long> collect = interactionReplies.stream()
                    // 根据 checkAnonymity 判断是否过滤匿名作者
                    .filter(r -> !(checkAnonymity && r.getAnonymity()))
                    .map(InteractionReply::getUserId)
                    .collect(Collectors.toSet());
            // 4.3 补充到待查询集合中
            uids.addAll(collect);
        }

        // ==================== 第5步：远程调用用户服务，批量获取用户信息 ====================
        // 为什么不逐条查？因为远程 RPC 调用开销大，批量查询能减少网络往返次数。
        List<UserDTO> userDTOS = userClient.queryUserByIds(uids);
        // 将 List 转为 Map<用户ID, 用户信息DTO>，方便后续快速查找
        Map<Long, UserDTO> userDTOMap = new HashMap<>();
        if (userDTOS != null) {
            userDTOMap = userDTOS.stream()
                    .collect(Collectors.toMap(UserDTO::getId, c -> c));
        }

        // ==================== 第6步：PO 转 VO，组装返回结果 ====================
        ArrayList<ReplyVO> voList = new ArrayList<>();
        Set<Long> finalLikedBizIds = likesBizIds;

        for (InteractionReply record : records) {
            // 6.1 自动复制同名属性
            // （id、content、anonymity、replyTimes、likedTimes、createTime 等）
            ReplyVO replyVO = BeanUtils.copyBean(record, ReplyVO.class);

            // 6.2 填充写作者信息（昵称、头像、身份类型）
            //     用户端：匿名不显示；管理端：无视匿名直接显示
            if (!(checkAnonymity && record.getAnonymity())) {
                UserDTO userDTO = userDTOMap.get(record.getUserId());
                if (userDTO != null) {
                    replyVO.setUserName(userDTO.getName());   // 昵称
                    replyVO.setUserIcon(userDTO.getIcon());   // 头像URL
                    replyVO.setUserType(userDTO.getType());   // 身份类型
                }
            }

            // 6.3 填充被回复的目标用户昵称（仅评论有该字段，回答无）
            UserDTO targetUserDTO = userDTOMap.get(record.getTargetUserId());
            if (targetUserDTO != null) {
                replyVO.setTargetUserName(targetUserDTO.getName());
            }

            if (finalLikedBizIds.contains(record.getId())) {
                replyVO.setLiked(true);
            }else {
                replyVO.setLiked(false);
            }

            voList.add(replyVO);
        }

        // ==================== 第7步：封装分页结果并返回 ====================
        // PageDTO 包含：总条数(total)、总页数(totalPage)、当前页数据(list)
        return PageDTO.of(page, voList);
    }


    // ========================================================================
    // 更新回答或评论的隐藏状态（管理端功能）
    // 如果是隐藏操作，且隐藏的是顶级回答（answerId为null），
    // 需要级联隐藏该回答下的所有评论，确保评论不单独暴露。
    // ========================================================================
    @Override
    public void updateReplyHidden(Long id, Boolean hidden) {

        // ==================== 第1步：根据ID查询记录 ====================
        // 先查出数据库中的记录，确认要操作的对象是否存在
        InteractionReply reply = lambdaQuery().eq(InteractionReply::getId, id).one();
        // 如果记录不存在，或 hidden 参数为空，直接抛异常
        if (reply == null || hidden == null) {
            throw new BadRequestException("回答或评论不存在");
        }
        // ==================== 第2步：更新当前记录 ====================
        // 无论是隐藏（hidden=true）还是取消隐藏（hidden=false），
        // 都先更新本条记录本身的隐藏状态
        reply.setHidden(hidden);
        updateById(reply);

        // ==================== 第3步：级联隐藏子评论 ====================
        // 判断条件：只有满足以下两个条件时才级联：
        // 1. 是隐藏操作（hidden=true）—— 取消隐藏时不级联恢复，防止误恢复
        // 2. 隐藏的是顶级回答（answerId=null）—— 只有顶级回答下有评论
        //    如果是评论（answerId!=null），它没有下级，不需要级联
        if (hidden && reply.getAnswerId() == null) {
            lambdaUpdate()
                    // WHERE answer_id = 当前回答的ID（所有直接评论该回答的记录）
                    .eq(InteractionReply::getAnswerId, id)
                    // SET hidden = true（全部设为隐藏）
                    .set(InteractionReply::getHidden, true)
                    // 执行批量更新
                    .update();
        }
    }

    @Override
    public ReplyVO getReplyAdminDetail(Long id) {

        InteractionReply reply = getById(id);
        if (reply == null)
            throw new BadRequestException("回答或评论不存在");

        ReplyVO vo = BeanUtils.copyBean(reply, ReplyVO.class);
        UserDTO userDTO = userClient.queryUserById(reply.getUserId());
        if (userDTO != null) {
            vo.setUserName(userDTO.getName());
            vo.setUserIcon(userDTO.getIcon());
        }
        return vo;
    }
}
