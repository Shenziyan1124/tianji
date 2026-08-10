package com.tianji.exam.domain.po;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;

/**
 * <p>
 * 学员答题记录(MongoDB)
 * </p>
 *
 * @author 虎哥
 * @since 2022-09-02
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Document("exam_record_question")
public class ExamRecordQuestion implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 记录id，MongoDB自动生成
     */
    @Id
    private String id;

    /**
     * 考试记录id，关联exam_record
     */
    private String examRecordId;

    /**
     * 学员id
     */
    private Long userId;

    /**
     * 题目id，关联MySQL的question表
     */
    private Long questionId;

    /**
     * 学员提交的答案，选择题存选项编号，主观题存文本
     */
    private String answer;

    /**
     * 是否答对，批改后回填
     */
    private Boolean correct;

    /**
     * 本题得分，批改后回填
     */
    private Integer score;

    /** 老师评语 */
    private String comment;
}
