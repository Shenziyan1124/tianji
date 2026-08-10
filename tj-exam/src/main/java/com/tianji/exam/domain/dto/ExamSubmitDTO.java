package com.tianji.exam.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel(description = "提交考试表单实体")
public class ExamSubmitDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("考试记录id")
    @NotNull(message = "考试记录id不能为空")
    private String id;

    @ApiModelProperty("答题信息列表")
    @Size(min = 1, message = "答题信息不能为空")
    @Valid
    private List<AnswerDTO> answers;

    @Data
    @ApiModel(description = "答题信息")
    public static class AnswerDTO {

        @ApiModelProperty("题目id")
        @NotNull(message = "题目id不能为空")
        private Long questionId;

        @ApiModelProperty("题目答案")
        @NotNull(message = "答案不能为空")
        private String answer;

        @ApiModelProperty("题目类型，1：单选题， 2：多选题，3：不定向选择题，4：判断题，5：主观题")
        @NotNull(message = "题目类型不能为空")
        private Integer type;
    }
}
