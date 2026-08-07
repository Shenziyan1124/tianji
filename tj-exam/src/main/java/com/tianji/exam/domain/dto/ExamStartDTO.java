package com.tianji.exam.domain.dto;

import com.tianji.common.validate.annotations.EnumValid;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * <p>
 * 开始考试表单实体
 * </p>
 *
 * @author 虎哥
 * @since 2022-09-02
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel(description = "开始考试表单实体")
public class ExamStartDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("课程id")
    @NotNull(message = "课程id不能为空")
    private Long courseId;

    @ApiModelProperty("小节id")
    @NotNull(message = "小节id不能为空")
    private Long sectionId;

    @ApiModelProperty("考试类型，1-练习，2-考试")
    @NotNull(message = "考试类型不能为空")
    @EnumValid(enumeration = {1, 2}, message = "考试类型错误")
    private Integer type;
}
