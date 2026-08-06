package com.tianji.learning.domain.query;

import com.tianji.common.domain.query.PageQuery;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;


@EqualsAndHashCode(callSuper = true)
@Data
@ApiModel(description = "管理端-笔记分页查询条件")
public class NoteAdminQuery extends PageQuery {
    @ApiModelProperty(value = "是否隐藏", example = "true")
    private Boolean hidden;
    @ApiModelProperty(value = "课程名称")
    private String courseName;
    @ApiModelProperty(value = "创建时间-开始时间")
    private LocalDateTime beginTime;
    @ApiModelProperty(value = "创建时间-结束时间")
    private LocalDateTime endTime;

}
