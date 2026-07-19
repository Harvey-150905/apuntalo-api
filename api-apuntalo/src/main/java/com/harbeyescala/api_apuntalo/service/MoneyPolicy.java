package com.harbeyescala.api_apuntalo.service;
import com.harbeyescala.api_apuntalo.exception.BusinessRuleException;
import java.math.BigDecimal;
import java.math.RoundingMode;
public final class MoneyPolicy {
 public static final int SCALE=2; public static final RoundingMode ROUNDING_MODE=RoundingMode.HALF_UP;
 public static final BigDecimal ZERO=new BigDecimal("0.00"); public static final BigDecimal MAX_VALUE=new BigDecimal("99999999.99");
 private MoneyPolicy(){}
 public static BigDecimal requireValid(BigDecimal value,String code,String message){if(value==null||value.scale()>SCALE||value.abs().compareTo(MAX_VALUE)>0)throw new BusinessRuleException(code,message);return normalize(value);}
 public static BigDecimal requirePositive(BigDecimal value,String code,String message){BigDecimal normalized=requireValid(value,code,message);if(normalized.signum()<=0)throw new BusinessRuleException(code,message);return normalized;}
 public static BigDecimal requirePositiveOrZero(BigDecimal value,String code,String message){BigDecimal normalized=requireValid(value,code,message);if(normalized.signum()<0)throw new BusinessRuleException(code,message);return normalized;}
 public static BigDecimal normalize(BigDecimal value){return value.setScale(SCALE,ROUNDING_MODE);}
 public static BigDecimal canonicalize(BigDecimal value){if(value==null)return null;if(value.signum()==0)return BigDecimal.ZERO;return value.stripTrailingZeros();}
}
