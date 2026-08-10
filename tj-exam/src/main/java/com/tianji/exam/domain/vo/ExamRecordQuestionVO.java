package com.tianji.exam.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(description = "考试答题详情")
public class ExamRecordQuestionVO {
    @ApiModelProperty("学员答案")
    private String answer;

    @ApiModelProperty("老师评语")
    private String comment;

    @ApiModelProperty("是否正确")
    private Boolean correct;

    @ApiModelProperty("学员得分")
    private Integer score;

    @ApiModelProperty("题目信息")
    private QuestionAnswerVO question;
}
