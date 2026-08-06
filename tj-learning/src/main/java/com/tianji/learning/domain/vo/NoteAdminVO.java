package com.tianji.learning.domain.vo;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@ApiModel(description = "管理端笔记信息")
public class NoteAdminVO {
    @ApiModelProperty(value = "主键id")
    private Long id;
    @ApiModelProperty(value = "课程name")
    private String courseName;
    @ApiModelProperty("课程章名称")
    private String chapterName;
    @ApiModelProperty("课程节名称")
    private String sectionName;
    @ApiModelProperty("笔记内容")
    private String content;
    @ApiModelProperty("笔记作者")
    private String authorName;
    @ApiModelProperty("笔记发布时间")
    private LocalDateTime createTime;
    @ApiModelProperty("引用次数（笔记被采集次数）")
    private Integer gatheredTimes;
    @ApiModelProperty("是否隐藏，true表示在用户端隐藏")
    private Boolean hidden;
}
