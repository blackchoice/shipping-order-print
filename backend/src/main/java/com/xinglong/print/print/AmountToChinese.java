package com.xinglong.print.print;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Convert RMB amount to Chinese uppercase (e.g. 280.00 -> 贰佰捌拾元整).
 */
public final class AmountToChinese {

    private static final String[] CN_NUM = {
            "零", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖"
    };
    private static final String[] CN_UNIT = {
            "", "拾", "佰", "仟"
    };
    private static final String[] CN_SECTION = {
            "", "万", "亿"
    };

    private AmountToChinese() {
    }

    public static String toChinese(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        BigDecimal value = amount.setScale(2, RoundingMode.HALF_UP);
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            return "零元整";
        }
        boolean negative = value.signum() < 0;
        value = value.abs();

        long yuan = value.longValue();
        int fen = value.remainder(BigDecimal.ONE).movePointRight(2).intValue();

        StringBuilder sb = new StringBuilder();
        if (negative) {
            sb.append("负");
        }
        sb.append(convertInteger(yuan)).append("元");
        if (fen == 0) {
            sb.append("整");
        } else {
            int jiao = fen / 10;
            int f = fen % 10;
            if (jiao > 0) {
                sb.append(CN_NUM[jiao]).append("角");
            } else if (yuan > 0) {
                sb.append("零");
            }
            if (f > 0) {
                sb.append(CN_NUM[f]).append("分");
            }
        }
        return sb.toString();
    }

    private static String convertInteger(long number) {
        if (number == 0) {
            return "零";
        }
        StringBuilder result = new StringBuilder();
        int sectionIndex = 0;
        boolean zero = false;
        while (number > 0) {
            int section = (int) (number % 10000);
            if (section == 0) {
                if (!zero && result.length() > 0) {
                    zero = true;
                }
            } else {
                String sectionStr = convertSection(section);
                if (zero) {
                    result.insert(0, "零");
                    zero = false;
                }
                result.insert(0, sectionStr + CN_SECTION[sectionIndex]);
            }
            number /= 10000;
            sectionIndex++;
        }
        return result.toString();
    }

    private static String convertSection(int section) {
        StringBuilder sb = new StringBuilder();
        int unitPos = 0;
        boolean zero = false;
        int n = section;
        while (n > 0) {
            int digit = n % 10;
            if (digit == 0) {
                if (!zero && sb.length() > 0) {
                    zero = true;
                }
            } else {
                if (zero) {
                    sb.insert(0, "零");
                    zero = false;
                }
                sb.insert(0, CN_NUM[digit] + CN_UNIT[unitPos]);
            }
            n /= 10;
            unitPos++;
        }
        return sb.toString();
    }
}
