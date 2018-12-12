package com.youyu.cardequity.promotion.biz.dal.dao;

import com.youyu.cardequity.promotion.biz.dal.entity.ProductCouponEntity;
import com.youyu.cardequity.promotion.dto.ShortCouponDetailDto;
import com.youyu.common.mapper.YyMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 *  代码生成器
 *
 * @author 技术平台
 * @date 2018-12-07
 */
public interface ProductCouponMapper extends YyMapper<ProductCouponEntity> {

    /**
     * 通过常用参数查询可领取优惠券列表
     * @param productId 产品ID
     * @param groupId 产品分组ID
     * @param clientType 客户类型
     * @param entrustWay 委托方式
     * @return
     */
    List<ProductCouponEntity> findEnableGetCouponListByCommon(@Param("productId") String productId,
                                                              @Param("groupId") String groupId,
                                                              @Param("entrustWay") String entrustWay,
                                                              @Param("clientType") String clientType);

    /**
     * 根据指定券，获取到不满足领取频率规则的券及其阶梯
     * @param couponId 优惠券id
     * @param clientId 客户编号
     * @return
     */
    List<ShortCouponDetailDto> findClinetFreqForbidCouponDetailListByIds(@Param("clientId") String clientId,
                                                                         @Param("couponId") String couponId);

    /**
     * 根据指定券，获取到券的基本信息
     * @param couponId 优惠券id
     * @return
     */
    ProductCouponEntity findProductCouponById(@Param("couponId") String couponId);


}




