package com.tianji.exam.constants;

import com.tianji.common.enums.BaseEnum;
import lombok.Getter;

@Getter
public enum ExamStatus implements BaseEnum {
    // 1-答题中，2-已交卷待批改，3-已批改
    ANSWERING(1, "答题中"),
    SUBMITTED(2, "已交卷待批改"),
    CORRECTED(3, "已批改")
    ;
    int value;
    String desc;

    ExamStatus(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static ExamStatus of(Integer value) {
        if (value == null) {
            return null;
        }
        for (ExamStatus status : values()) {
            if (status.equalsValue(value)) {
                return status;
            }
        }
        return null;
    }
}
