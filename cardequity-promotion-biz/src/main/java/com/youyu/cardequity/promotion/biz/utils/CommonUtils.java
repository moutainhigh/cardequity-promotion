package com.youyu.cardequity.promotion.biz.utils;

import com.youyu.cardequity.promotion.biz.constant.CommonConstant;

import java.math.BigDecimal;

/**
 * Created by caiyi on 2018/12/12.
 */
public class CommonUtils {
    /**
     * 判断字符串是否为空或null
     *
     * @param target
     * @return
     */
    public static boolean isEmptyorNull(String target) {
        if (target == null)
            return true;
        if (target.isEmpty() || "".equals(target))
            return true;
        return false;
    }

    /**
     * 为空忽略或匹配成功返回true
     *
     * @param source
     * @param target
     * @return
     */
    public static boolean isEmptyIgnoreOrWildcardOrContains(String source, String target) {
        if (target == null ||
                source == null ||
                source.equals(CommonConstant.WILDCARD) ||
                source.contains(target)) {
            return true;
        }
        return false;
    }

    /**
     * 额度类参数需要验证的情况：0-验证不通过，1-验证通过，2-需要继续验证
     *
     * @param value 额度类参数值
     * @return
     */
    public static int isQuotaValueNeedValidFlag(BigDecimal value) {
        int validflag = 2;//0-验证不通过，1-验证通过，2-需要继续验证

        //额度有效数值为[0,999999999)
        if (value == null ||
            value.compareTo(CommonConstant.IGNOREVALUE) >= 0) {

            validflag = 1;
        } else if (value.compareTo(BigDecimal.ZERO) <= 0) {
            validflag = 0;
        }
        return validflag;
    }

}
