package com.youyu.cardequity.promotion.biz.service.impl;

import com.youyu.cardequity.common.base.uidgenerator.UidGenerator;
import com.youyu.cardequity.common.base.util.LocalDateUtils;
import com.youyu.cardequity.common.spring.service.BatchService;
import com.youyu.cardequity.promotion.biz.dal.dao.*;
import com.youyu.cardequity.promotion.biz.dal.entity.*;
import com.youyu.cardequity.promotion.biz.enums.ProductCouponGetTypeEnum;
import com.youyu.cardequity.promotion.biz.enums.ProductCouponStatusEnum;
import com.youyu.cardequity.promotion.biz.service.CouponIssueService;
import com.youyu.cardequity.promotion.biz.utils.CommonUtils;
import com.youyu.cardequity.promotion.constant.CommonConstant;
import com.youyu.cardequity.promotion.dto.req.CouponIssueMsgDetailsReq;
import com.youyu.cardequity.promotion.dto.req.CouponIssueReq;
import com.youyu.cardequity.promotion.dto.req.UserInfo4CouponIssueDto;
import com.youyu.cardequity.promotion.enums.CommonDict;
import com.youyu.cardequity.promotion.enums.CouponIssueVisibleEnum;
import com.youyu.cardequity.promotion.enums.dict.CouponUseStatus;
import com.youyu.cardequity.promotion.enums.dict.TriggerByType;
import com.youyu.cardequity.promotion.enums.dict.UseGeEndDateFlag;
import com.youyu.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.youyu.cardequity.common.base.util.DateUtil.*;
import static com.youyu.cardequity.common.base.util.EnumUtil.getCardequityEnum;
import static com.youyu.cardequity.promotion.enums.CouponIssueStatusEnum.NOT_ISSUE;
import static com.youyu.cardequity.promotion.enums.CouponIssueTriggerTypeEnum.DELAY_JOB_TRIGGER_TYPE;
import static com.youyu.cardequity.promotion.enums.CouponIssueVisibleEnum.INVISIBLE;
import static com.youyu.cardequity.promotion.enums.ResultCode.*;
import static com.youyu.cardequity.promotion.enums.dict.CouponGetType.GRANT;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.join;

@Service
@Slf4j
public class CouponIssueServiceImpl implements CouponIssueService {

    @Autowired
    private CouponIssueMapper couponIssueMapper;
    @Autowired
    private ProductCouponMapper productCouponMapper;
    @Autowired
    private CouponQuotaRuleMapper couponQuotaRuleMapper;

    @Autowired
    private UidGenerator uidGenerator;

    @Autowired
    private BatchService batchService;
    @Autowired
    private CouponStageRuleMapper couponStageRuleMapper;

    @Override
    @Transactional
    public void createIssue(CouponIssueReq couponIssueReq) {
        String couponId = couponIssueReq.getCouponId();
        ProductCouponEntity productCouponEntity = productCouponMapper.selectByPrimaryKey(couponId);

        CouponIssueEntity couponIssue = createCouponIssueEntity(couponIssueReq, productCouponEntity);
        checkCoupon(couponIssue, productCouponEntity);

        couponIssueMapper.insertSelective(couponIssue);
    }

    //todo take care of transaction
    @Override
    @Transactional
    public void processIssue(CouponIssueMsgDetailsReq couponIssueMsgDetailsReq) {

        ProductCouponEntity productCouponEntity = productCouponMapper.selectByPrimaryKey(couponIssueMsgDetailsReq.getCouponId());
        CouponIssueEntity couponIssueEntity = couponIssueMapper.selectByPrimaryKey(couponIssueMsgDetailsReq.getCouponIssueId());

        checkCoupon(couponIssueEntity, productCouponEntity);

        //检查券的上下架
        if (CouponIssueVisibleEnum.INVISIBLE.getCode().equals(couponIssueEntity.getIsVisible())) {
            throw new BizException(INVISIBLE_COUPON_ISSUE_TASK_CANNOT_BE_ISSUED);
        }


        List<UserInfo4CouponIssueDto> eligibleUserList = filterAndGetEligibleUser(
                couponIssueMsgDetailsReq.getUserInfo4CouponIssueDtoList(), productCouponEntity.getClientTypeSet());


        // 根据用户ID正序sort 并选取前部分用户发券
        List<UserInfo4CouponIssueDto> issueClientCouponList = confirmIssueClientAndGetIssueList(eligibleUserList, couponIssueMsgDetailsReq.getCouponId());


        //发券
        List<ClientCouponEntity> clientCouponEntityList = createClientCouponEntityList(issueClientCouponList, productCouponEntity);
        batchService.batchDispose(clientCouponEntityList, ClientCouponMapper.class, "insertSelective");

        //todo 写入发放流水


    }

    private List<UserInfo4CouponIssueDto> filterAndGetEligibleUser(List<UserInfo4CouponIssueDto> userInfo4CouponIssueDtoList, String clientTypeSet) {
        List<UserInfo4CouponIssueDto> eligibleUserList = new ArrayList<>();

        //发放类型检验
        userInfo4CouponIssueDtoList.forEach(userInfo4CouponIssueDto -> {
            if (CommonUtils.isEmptyIgnoreOrWildcardOrContains(clientTypeSet, userInfo4CouponIssueDto.getUserType())) {
                eligibleUserList.add(userInfo4CouponIssueDto);
            }
        });
        return eligibleUserList;
    }

    private List<UserInfo4CouponIssueDto> confirmIssueClientAndGetIssueList(List<UserInfo4CouponIssueDto> eligibleUserList, String couponId) {
        CouponQuotaRuleEntity couponQuotaRuleEntity = couponQuotaRuleMapper.findCouponQuotaRuleById(couponId);
        if (couponQuotaRuleEntity == null || couponQuotaRuleEntity.getMaxCount() <= 0) {
            //todo
            throw new BizException("");
        }

        //发放顺序按照用户ID进行顺序发放，这里先排序
        eligibleUserList.sort((a, b) -> Integer.compare(a.getClientId().compareTo(b.getClientId()), 0));


        Integer couponMaxIssueCount = couponQuotaRuleEntity.getMaxCount();
        if (couponMaxIssueCount <= 0) {
            log.info("券库存剩余容量为0，无法实施发券操作。券ID为：{},准备发放的用户为：{}", couponId, eligibleUserList);
            //todo
            throw new BizException("");
        }

        return eligibleUserList.subList(0, couponMaxIssueCount);
    }


    private List<ClientCouponEntity> createClientCouponEntityList(
            List<UserInfo4CouponIssueDto> issueUserList, ProductCouponEntity couponEntity) {
        List<ClientCouponEntity> clientCouponEntityList = new ArrayList<>();

        issueUserList.forEach(eligibleUser -> {
            //todo
            ClientCouponEntity clientCouponEntity = new ClientCouponEntity();

            //券阶梯信息set
            Optional<CouponStageRuleEntity> couponStageEntityOpt = checkAndGetCouponStageEntityOpt(couponEntity.getId());
            couponStageEntityOpt.ifPresent(couponStageRuleEntity -> {
                clientCouponEntity.setCouponAmout(couponStageRuleEntity.getCouponValue());
                clientCouponEntity.setCouponShortDesc(couponStageRuleEntity.getCouponShortDesc());
                clientCouponEntity.setTriggerByType(couponStageRuleEntity.getTriggerByType());
                clientCouponEntity.setBeginValue(couponStageRuleEntity.getBeginValue());
                clientCouponEntity.setEndValue(couponStageRuleEntity.getEndValue());
            });

            clientCouponEntity.setId(CommonUtils.getUUID());

            clientCouponEntity.setClientId(eligibleUser.getClientId());
            //平台发放
            clientCouponEntity.setGetType(GRANT.getDictValue());


            //有效时间边界set
            LocalDateTime validStartDateTime = computeValidStartTime(couponEntity.getAllowUseBeginDate());
            clientCouponEntity.setValidStartDate(validStartDateTime);

            clientCouponEntity.setValidEndDate(
                    computeValidEndDate(validStartDateTime, couponEntity.getAllowGetEndDate(),
                            couponEntity.getValIdTerm(), couponEntity.getUseGeEndDateFlag()));


            clientCouponEntity.setCouponAmout(couponEntity.getProfitValue());
            clientCouponEntity.setCouponShortDesc(couponEntity.getCouponShortDesc());
            clientCouponEntity.setTriggerByType(TriggerByType.NUMBER.getDictValue());
            clientCouponEntity.setBeginValue(BigDecimal.ZERO);
            clientCouponEntity.setEndValue(CommonConstant.IGNOREVALUE); //数值参数的边界有效上限
            clientCouponEntity.setGetType(couponEntity.getGetType());
            clientCouponEntity.setApplyProductFlag(couponEntity.getApplyProductFlag());
            clientCouponEntity.setCouponStrategyType(couponEntity.getCouponStrategyType());
            clientCouponEntity.setCouponShortDesc(couponEntity.getCouponShortDesc());
            clientCouponEntity.setCouponType(couponEntity.getCouponType());
            clientCouponEntity.setCouponLable(couponEntity.getCouponLable());
            clientCouponEntity.setCouponLevel(couponEntity.getCouponLevel());

            //todo
  /*          clientCouponEntity.setUpdateAuthor(req.getOperator());
            clientCouponEntity.setCreateAuthor(req.getOperator());*/

            clientCouponEntity.setNewFlag(CommonDict.IF_YES.getCode());
            clientCouponEntity.setIsEnable(CommonDict.IF_YES.getCode());
            clientCouponEntity.setStatus(CouponUseStatus.NORMAL.getDictValue());
            //todo
//        entity.setJoinOrderId(req.getActivityId());
            clientCouponEntityList.add(clientCouponEntity);
        });


        return clientCouponEntityList;
    }

    private LocalDateTime computeValidStartTime(LocalDateTime allowUseBeginDateTime) {
        if (allowUseBeginDateTime == null || LocalDate.now().isAfter(allowUseBeginDateTime.toLocalDate())) {
            return LocalDateTime.now();
        }
        return allowUseBeginDateTime;

    }

    private LocalDateTime computeValidEndDate(LocalDateTime validStartDateTime, LocalDateTime allowUseEndDate,
                                              Integer validTimeTerm, String useMinDateFlag) {
        //term无效，则以allowUserEndDate为准：allowUserEndDate为空，则默认时间一个月
        LocalDateTime validEndDate = validStartDateTime.plusMonths(1);
        if (validTimeTerm == null || validTimeTerm <= 0) {
            if (allowUseEndDate == null) {
                return validEndDate;
            }
            return allowUseEndDate;
        }


        validEndDate = validStartDateTime.plusDays(validTimeTerm);

        //若定义了最早结束时间标志，则判断最终结束时间与计算出来的结束时间谁更小
        if (UseGeEndDateFlag.YES.getDictValue().equals(useMinDateFlag)) {
            if (allowUseEndDate != null && validEndDate.isAfter(allowUseEndDate)) {
                return allowUseEndDate;
            } else if (allowUseEndDate == null) {
                return validEndDate;
            }
            return validEndDate;

        }

        if (allowUseEndDate != null) {
            return allowUseEndDate;
        }
        return validEndDate;
    }


    /**
     * 获取此券的stage
     *
     * @param couponId
     * @return
     */
    private Optional<CouponStageRuleEntity> checkAndGetCouponStageEntityOpt(String couponId) {
        //保护一下如果没有传入StageId，且该券下只有一个id则自动补全
        List<CouponStageRuleEntity> couponStageEntityList = couponStageRuleMapper.findStageByCouponId(couponId);
        if (couponStageEntityList == null || couponStageEntityList.isEmpty()) {
            return Optional.empty();
        }
        if (couponStageEntityList.size() > 1) {
            //todo 确认下这里的逻辑
            log.warn("StageId不为空,但该券却有多个子券无法确定领取的券(后台发放券操作)，券ID为：{}", couponId);
            throw new BizException(PARAM_ERROR.getCode(), PARAM_ERROR.getFormatDesc("StageId不为空,但该券却有多个子券无法确定领取的券"));
        }

        return Optional.of(couponStageEntityList.get(0));
    }


    /**
     * 发放优惠券规则检验
     *
     * @param couponIssueEntity
     * @param productCouponEntity
     */
    private void checkCoupon(CouponIssueEntity couponIssueEntity, ProductCouponEntity productCouponEntity) {
        ProductCouponStatusEnum productCouponStatusEnum = getCardequityEnum(ProductCouponStatusEnum.class, productCouponEntity.getStatus());
        if (!productCouponStatusEnum.isVisible()) {
            throw new BizException(INVISIBLE_COUPON_CANNOT_BE_ISSUED);
        }

        LocalDateTime nowTime = LocalDateTime.now();
        boolean isValid = nowTime.isAfter(productCouponEntity.getAllowUseBeginDate()) && nowTime.isBefore(productCouponEntity.getAllowUseEndDate());
        if (!isValid) {
            throw new BizException(COUPON_HAS_EXPIRED);
        }

        ProductCouponGetTypeEnum productCouponGetTypeEnum = getCardequityEnum(ProductCouponGetTypeEnum.class, productCouponEntity.getGetType());
        if (productCouponGetTypeEnum.isHanld()) {
            throw new BizException(MANUAL_COUPON_CANNOT_BE_ISSUED);
        }

        Date issueTime = string2Date(couponIssueEntity.getIssueTime(), YYYY_MM_DD_HH_MM);
        Date now = now();
        if (now.after(issueTime)) {
            throw new BizException(ISSUE_TIME_MUST_GREATER_CURRENT_TIME);
        }

        LocalDateTime nowLocalDateTime = LocalDateUtils.date2LocalDateTime(issueTime);
        if (nowLocalDateTime.isAfter(productCouponEntity.getAllowUseEndDate())) {
            throw new BizException(COUPON_END_DATE_MUST_GREATER_CURRENT_DATE);
        }

        CouponQuotaRuleEntity couponQuotaRule = couponQuotaRuleMapper.selectByPrimaryKey(couponIssueEntity.getCouponId());
        Integer issueQuantity = couponQuotaRule.getMaxCount();
        if (nonNull(issueQuantity) && issueQuantity <= 0) {
            throw new BizException(COUPON_ISSUE_QUANTITY_CANNOT_LESS_ZERO);
        }
    }

    /**
     * 创建发放优惠券对象
     *
     * @param couponIssueReq
     * @param productCouponEntity
     * @return
     */
    private CouponIssueEntity createCouponIssueEntity(CouponIssueReq couponIssueReq, ProductCouponEntity productCouponEntity) {
        CouponIssueEntity couponIssueEntity = new CouponIssueEntity();
        couponIssueEntity.setCouponIssueId(uidGenerator.getUID2());
        couponIssueEntity.setCouponId(couponIssueReq.getCouponId());
        couponIssueEntity.setCouponName(productCouponEntity.getCouponName());
        couponIssueEntity.setIssueTime(couponIssueReq.getIssueTime());
        couponIssueEntity.setTargetType(couponIssueReq.getTargetType());
        couponIssueEntity.setIsVisible(INVISIBLE.getCode());
        couponIssueEntity.setIssueStatus(NOT_ISSUE.getCode());
        couponIssueEntity.setTriggerType(DELAY_JOB_TRIGGER_TYPE.getCode());
        couponIssueEntity.setIssueIds(join(couponIssueReq.getIssueIds(), ","));
        return couponIssueEntity;
    }


}