package com.tianji.api.dto.leanring;

/**
 * 小节类型：1-视频，2-考试
 */
public enum SectionType {
    VIDEO(1, "视频"),
    EXAM(2, "考试");
    final int value;
    final String desc;

    SectionType(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public int getValue() {
        return value;
    }

    public String getDesc() {
        return desc;
    }
}
