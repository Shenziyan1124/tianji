package com.tianji.learning.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@ApiModel(description = "用户端笔记信息")
public class NoteVO {
    @ApiModelProperty("主键id")
    private Long id;
    @ApiModelProperty("笔记内容")
    private String content;
    @ApiModelProperty("记录笔记时视频播放的时间点，单位秒")
    private Integer noteMoment;
    @ApiModelProperty("是否是隐私笔记")
    private Boolean isPrivate;
    @ApiModelProperty("是否采集")
    private Boolean isGathered;
    @ApiModelProperty("接收 Note userId")
    private Long userId;
    @ApiModelProperty("笔记作者id")
    private Long authorId;
    @ApiModelProperty("笔记作者昵称")
    private String authorName;
    @ApiModelProperty("笔记作者头像")
    private String authorIcon;
    @ApiModelProperty(value = "创建时间", example = "2022-7-18 19:52:36")
    private LocalDateTime createTime;

    @ApiModelProperty("点赞数")
    private Integer likedTimes;
    @ApiModelProperty("是否点赞")
    private Boolean liked;
}
