package com.tianji.learning.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 笔记表
 * </p>
 *
 * @author SHEN
 * @since 2026-08-03
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("note")
public class Note implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 笔记主键 id
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 笔记内容
     */
    private String content;

    /**
     * 是否私密笔记，默认false
     */
    private Boolean isPrivate;

    /**
     * 点赞次数
     */
    private Integer likedTimes;

    /**
     * 记录笔记时的视频播放时间点，单位秒
     */
    private Integer noteMoment;

    /**
     * 课程id
     */
    private Long courseId;

    /**
     * 章id
     */
    private Long chapterId;

    /**
     * 节id
     */
    private Long sectionId;

    /**
     * 记笔记的用户id
     */
    private Long userId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 是否隐藏，true表示在用户端隐藏
     */
    private Boolean hidden;




}
