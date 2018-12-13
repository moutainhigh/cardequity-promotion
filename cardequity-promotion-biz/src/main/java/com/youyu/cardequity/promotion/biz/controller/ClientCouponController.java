package com.youyu.cardequity.promotion.biz.controller;


import com.youyu.cardequity.promotion.api.ClientCouponApi;
import com.youyu.cardequity.promotion.dto.ObtainRspDto;
import com.youyu.cardequity.promotion.vo.req.ClientObtainCouponReq;
import com.youyu.common.api.Result;
import com.youyu.cardequity.promotion.dto.ClientCouponDto;
import com.youyu.cardequity.promotion.biz.service.ClientCouponService;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static com.youyu.common.enums.BaseResultCode.NO_DATA_FOUND;


/**
 * 代码生成器
 *
 * @author 技术平台
 * @date 2018-12-07
 */
@RestController
public class ClientCouponController implements ClientCouponApi {

    @Autowired
    private ClientCouponService clientCouponService;


    @Override
    public Result<List<ClientCouponDto>> findClientCoupon(String clientId) {
        return  Result.ok(clientCouponService.findClientCoupon(clientId));
    }

    @Override
    public Result<ObtainRspDto> obtainCoupon(ClientObtainCouponReq req) {

        return Result.ok(clientCouponService.obtainCoupon(req));
    }

    @Override
    public Result<List<ClientCouponDto>> findEnableUseCoupon(ClientObtainCouponReq req) {
        return Result.ok(clientCouponService.findEnableUseCoupon(req));
    }


}
