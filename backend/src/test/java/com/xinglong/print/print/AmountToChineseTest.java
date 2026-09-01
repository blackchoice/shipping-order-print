package com.xinglong.print.print;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmountToChineseTest {

    @Test
    void twoHundredEighty() {
        assertEquals("贰佰捌拾元整", AmountToChinese.toChinese(new BigDecimal("280.00")));
    }

    @Test
    void zero() {
        assertEquals("零元整", AmountToChinese.toChinese(BigDecimal.ZERO));
    }

    @Test
    void withJiaoFen() {
        String cn = AmountToChinese.toChinese(new BigDecimal("1.23"));
        assertTrue(cn.contains("壹元"));
        assertTrue(cn.contains("贰角"));
        assertTrue(cn.contains("叁分"));
    }
}
