package com.tianji.learning.domain.query;


import com.tianji.common.domain.query.PageQuery;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@ApiModel(description = "笔记分页查询条件")
public class NotePageQuery extends PageQuery {
    @ApiModelProperty(value = "课程id")
    private Long courseId;

    @ApiModelProperty(value = "小节id", example = "1")
    private Long sectionId;

    @ApiModelProperty(value = "是否只查询我的笔记", example = "true",required = true)
    private Boolean onlyMine;
}
