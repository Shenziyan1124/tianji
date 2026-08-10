package com.tianji.exam.domain.vo;

import io.swagger.annotations.ApiModel;
import lombok.Data;

import java.util.List;

// 多了 answer、analysis——这是考试结束后才允许返回的
@Data
@ApiModel(description = "题目答题信息")
public class QuestionAnswerVO {
    private Long id;           // 题目id
    private String name;       // 题目名称
    private Integer type;      // 题目类型
    private Integer score;     // 题目分值
    private List<String> options;   // 选项
    private String answer;     // 正确答案
    private String analysis;   // 答案解析
    private Integer difficulty; // 难易程度
}
