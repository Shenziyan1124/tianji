package com.tianji.learning.constants;

public interface RedisConstants {
    //签到记录前缀: sign:uid:11120真实id:202607月份
    String SIGN_RECORD_KEY_PREFIX = "sign:uid:";

    //积分排行榜前缀: boards:202607月份
    String POINTS_BOARD_KEY_PREFIX = "boards:";
}
