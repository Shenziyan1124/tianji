package com.tianji.learning.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotNull;

@Data
@ApiModel(description = "笔记表单信息")
public class NoteFormDTO {

    @ApiModelProperty("笔记内容")
    @NotNull(message = "笔记内容不能为空")
    @Length(min = 1, max = 500, message = "笔记内容长度不能超过500")
    private String content;

    @ApiModelProperty("是否私密笔记，默认false")
    private Boolean isPrivate;

    @ApiModelProperty("记录笔记时视频播放的时间点，单位秒")
    @NotNull(message = "视频播放时间点不能为空")
    private Integer noteMoment;

    @ApiModelProperty("课程id")
    @NotNull(message = "课程id不能为空")
    private Long courseId;

    @ApiModelProperty("章id")
    @NotNull(message = "章id不能为空")
    private Long chapterId;

    @ApiModelProperty("节id")
    @NotNull(message = "节id不能为空")
    private Long sectionId;
}
