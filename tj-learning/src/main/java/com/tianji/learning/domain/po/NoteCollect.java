package com.tianji.learning.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 笔记采集表
 * </p>
 *
 * @author SHEN
 * @since 2026-08-04
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("note_collect")
public class NoteCollect implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 采集记录主键id
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 被采集的笔记id
     */
    private Long noteId;

    /**
     * 采集笔记的用户id
     */
    private Long userId;

    /**
     * 采集时间
     */
    private LocalDateTime createTime;
}
