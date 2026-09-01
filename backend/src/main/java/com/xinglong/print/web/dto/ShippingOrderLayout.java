package com.xinglong.print.web.dto;

import java.util.List;

/**
 * 打印版式，用于前端 1:1 复现打印结果。
 * <p>
 * 内容来自与实际打印同一份行数据，因此预览不会和纸面结果分叉。
 * 前端按 {@code cpi} 定标字宽（一个半角 = 1/cpi 英寸，一个汉字 = 2 个半角），
 * 按 {@code lineHeightMm} 定标行高。预览在纸面上居中显示；{@code printLeftMarginMm}
 * 仅反映打印机 ESC l 补偿，不参与预览定位。
 */
public record ShippingOrderLayout(
        int cols,
        int cpi,
        double widthMm,
        double heightMm,
        int lineSpacingN,
        double lineHeightMm,
        double topMarginMm,
        /** Printer-only left margin (ESC l); preview centers content on the form. */
        double printLeftMarginMm,
        int pageLengthLines,
        List<Line> lines) {

    /**
     * 一行输出。{@code heightMm} 已含 {@code spacingMultiplier}，
     * {@code extraFeedMm} 是该行之后的一次性走纸（标题下的额外间距）。
     */
    public record Line(
            String text,
            double spacingMultiplier,
            double heightMm,
            double extraFeedMm,
            boolean doubleWidth,
            boolean doubleHeight,
            boolean bold) {
    }
}
