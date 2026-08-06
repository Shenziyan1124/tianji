package com.tianji.learning.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@ApiModel(description = "管理端笔记详情")
public class NoteDetailVO {

    @ApiModelProperty("笔记id")
    private Long id;

    @ApiModelProperty("课程名称")
    private String courseName;

    @ApiModelProperty("章名称")
    private String chapterName;

    @ApiModelProperty("节名称")
    private String sectionName;

    @ApiModelProperty("多级分类，用/拼接")
    private String categoryNames;

    @ApiModelProperty("笔记内容")
    private String content;

    @ApiModelProperty("记录笔记时的视频播放时间点，单位秒")
    private Integer noteMoment;

    @ApiModelProperty("是否在用户端隐藏")
    private Boolean hidden;

    @ApiModelProperty("被采集次数")
    private Integer usedTimes;

    @ApiModelProperty("作者名称")
    private String authorName;

    @ApiModelProperty("作者电话")
    private String authorPhone;

    @ApiModelProperty("发布时间")
    private LocalDateTime createTime;

    @ApiModelProperty("采集人名称集合")
    private List<String> gathers;
}
