

package com.tianji.promotion.domain.query;

import com.tianji.common.domain.query.PageQuery;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@EqualsAndHashCode(callSuper = true)
@Data
@ApiModel(description = "兑换码查询参数")
@Accessors(chain = true)
public class CouponCodeQuery extends PageQuery {

    @ApiModelProperty("优惠券id")
    private Long couponId;

    @ApiModelProperty("优惠券状态，1：待发放，2：发放中，3：已结束, 4：取消/终止")
    private Integer status;

}