package com.tianji.exam.domain.po;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 考试记录(MongoDB)
 * </p>
 *
 * @author 虎哥
 * @since 2022-09-02
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Document("exam_record")
public class ExamRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 考试记录id，MongoDB自动生成
     */
    @Id
    private String id;

    /**
     * 学员id
     */
    private Long userId;

    /**
     * 课程id
     */
    private Long courseId;

    /**
     * 小节id
     */
    private Long sectionId;

    /**
     * 考试类型，1-练习，2-考试
     */
    private Integer type;

    /**
     * 总得分，交卷批改后回填
     */
    private Integer score;

    /**
     * 考试状态，1-答题中，2-已交卷待批改，3-已批改
     */
    private Integer status;

    /**
     * 考试开始时间
     */
    private LocalDateTime startTime;

    /**
     * 考试结束(交卷)时间
     */
    private LocalDateTime endTime;
}
