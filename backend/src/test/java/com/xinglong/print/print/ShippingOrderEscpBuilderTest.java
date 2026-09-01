package com.xinglong.print.print;

import com.xinglong.print.config.PrintProperties;
import com.xinglong.print.web.dto.ShippingOrderLayout;
import com.xinglong.print.web.dto.ShippingOrderPrintRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShippingOrderEscpBuilderTest {

    @Test
    void buildsAlignedGbkSlip() {
        PrintProperties props = new PrintProperties();
        props.getPage().setCols(100);
        props.getPage().setCondensed(false);
        props.getPage().setTitleDoubleSize(true);
        props.getPage().setMetaDoubleHeight(true);
        props.getPage().setFitToForm(true);
        props.getPage().setLineEnding("crlf");
        props.getPage().setLineSpacingN(22);
        props.getPage().setTitleGapMm(2);
        props.getPage().setTopMarginMm(6);
        props.getPage().setLeftMarginMm(16);
        props.getPage().setSignRowHeight(1.5);
        props.getPage().setHeightMm(93);
        props.getPage().setWidthMm(241);
        props.getTable().setPadEmptyRows(true);
        props.getTable().setMaxRows(7);
        ShippingOrderEscpBuilder builder = new ShippingOrderEscpBuilder(props);

        ShippingOrderPrintRequest req = new ShippingOrderPrintRequest();
        req.setCompanyTitle("怀化市兴隆农业开发有限公司出货单");
        req.setCustomerName("本部食堂");
        req.setOrderNo("XS-202607010206");
        req.setDate("2026-07-01");
        req.setReceiver("周高玉");
        ShippingOrderPrintRequest.LineItem line = new ShippingOrderPrintRequest.LineItem();
        line.setProductName("牛腩");
        line.setUnit("公斤");
        line.setQuantity(new BigDecimal("4"));
        line.setUnitPrice(new BigDecimal("70.00"));
        req.setLines(List.of(line));

        byte[] data = builder.build(req);
        assertTrue(data[0] == 0x1B && data[1] == (byte) '@');
        boolean hasPitch12 = false;
        for (int i = 0; i + 1 < data.length; i++) {
            if (data[i] == 0x1B && data[i + 1] == (byte) 'M') {
                hasPitch12 = true;
            }
        }
        assertTrue(hasPitch12);
        // 每行一组 CR+LF，不多不少（AUTO LF 需在打印机上关闭）
        int expectedLines = builder.buildPlainPreview(req).split("\n", -1).length;
        int[] ends = countLineEnds(data);
        assertEquals(expectedLines, ends[0]);
        assertEquals(expectedLines, ends[1]);

        String asGbk = new String(data, Charset.forName("GBK"));
        assertTrue(asGbk.contains("怀化市兴隆农业开发有限公司出货单"), asGbk);
        assertTrue(asGbk.contains("牛腩"), asGbk);
        assertTrue(asGbk.contains("总金额大写"), asGbk);

        String preview = builder.buildPlainPreview(req);
        String[] all = preview.split("\n", -1);
        String top = null;
        String header = null;
        String dataRow = null;
        String bottom = null;
        String meta = null;
        for (String ln : all) {
            if (meta == null && ln.contains("客户名称") && ln.contains("日期")) {
                meta = ln;
            }
            if (top == null && ln.startsWith("┌")) {
                top = ln;
            }
            if (header == null && ln.startsWith("│") && ln.contains("序号")) {
                header = ln;
            }
            if (dataRow == null && ln.startsWith("│") && ln.contains("牛腩")) {
                dataRow = ln;
            }
            if (ln.startsWith("└")) {
                bottom = ln;
            }
        }
        assertTrue(top != null && header != null && dataRow != null && bottom != null && meta != null);
        int cols = ShippingOrderEscpBuilder.displayWidth(top);
        assertEquals(cols, ShippingOrderEscpBuilder.displayWidth(header));
        assertEquals(cols, ShippingOrderEscpBuilder.displayWidth(dataRow));
        assertEquals(cols, ShippingOrderEscpBuilder.displayWidth(bottom));
        assertEquals(cols, ShippingOrderEscpBuilder.displayWidth(meta));
        // 签收行 1.5 倍高：送货/收货行后有一行只有竖线的空行，再是底框
        int signIdx = -1;
        int bottomIdx = -1;
        for (int i = 0; i < all.length; i++) {
            if (signIdx < 0 && all[i].contains("送货人")) {
                signIdx = i;
            }
            if (all[i].startsWith("└")) {
                bottomIdx = i;
            }
        }
        assertEquals(signIdx + 2, bottomIdx);
        assertEquals(barOffsets(all[signIdx - 1]), barOffsets(all[signIdx]));
        assertEquals(barOffsets(all[signIdx]), barOffsets(all[signIdx + 1]));
        assertTrue(all[signIdx].contains("收货人"), all[signIdx]);

        int titleLine = -1;
        for (int i = 0; i < all.length; i++) {
            if (all[i].contains("出货单") && !all[i].startsWith("│")) {
                titleLine = i;
                break;
            }
        }
        assertEquals(0, titleLine);

        // 每两行货物之间有分隔线：表头下 1 + 明细间 6 + 合计前 1 + 签收前 1
        int midRules = 0;
        for (String ln : all) {
            if (ln.startsWith("├")) {
                midRules++;
            }
        }
        assertEquals(9, midRules);

        // 竖线收口：相邻两行同一列上，「上行有下笔」必须等价于「下行有上笔」。
        // 不等就意味着竖线要么断一截，要么从横线下面戳出去。
        for (int i = 0; i + 1 < all.length; i++) {
            assertEquals(strokes(all[i], false), strokes(all[i + 1], true),
                    "竖线接缝不齐:\n" + all[i] + "\n" + all[i + 1]);
        }

        // 「总金额：」「收货人：」的左竖线与表头「单价」列的左竖线同一位置
        String totalRow = null;
        String signRow = null;
        for (String ln : all) {
            if (totalRow == null && ln.contains("总金额：")) {
                totalRow = ln;
            }
            if (signRow == null && ln.contains("收货人：")) {
                signRow = ln;
            }
        }
        assertTrue(totalRow != null && signRow != null);
        int priceColStart = barOffsets(header).get(4);
        assertTrue(barOffsets(totalRow).contains(priceColStart), totalRow);
        assertTrue(barOffsets(signRow).contains(priceColStart), signRow);

        // 单据仍在一张 93mm 纸内
        assertTrue(all.length <= props.getPage().capacityLines(22), "lines=" + all.length);

        // 左边距 16mm → 12 CPI 下约 8 列（ESC l 8）
        assertEquals(8, props.getPage().leftMarginColumns());
        assertTrue(containsBytes(data, new byte[] {0x1B, 'l', 8}), "missing ESC l left margin");

        // 顶端留白 6mm 用 ESC J 43，标题下 2mm 用 ESC J 14
        assertTrue(containsBytes(data, new byte[] {0x1B, 'J', 43}), "missing ESC J for top margin");
        assertTrue(containsBytes(data, new byte[] {0x1B, 'J', 14}), "missing ESC J for title gap");
        // 签收下垫行正好半个字身（12/180），竖线末端才落在底框横线上；上垫行补足余量
        assertTrue(containsBytes(data, new byte[] {0x1B, '3', 12}), "missing half-glyph sign bottom pad");
        assertTrue(containsBytes(data, new byte[] {0x1B, '3', 10}), "missing sign top pad");
        assertTrue(containsBytes(data, new byte[] {0x1B, '3', 44}), "missing double line spacing");

        // 标题双宽双高、客户信息行双高；标题按半幅排版，不会被右边界截断
        assertTrue(containsBytes(data, new byte[] {0x1B, 'W', 1}), "missing double width");
        assertTrue(containsBytes(data, new byte[] {0x1B, 'w', 1}), "missing double height");
        assertTrue(ShippingOrderEscpBuilder.displayWidth(all[titleLine]) <= cols / 2, all[titleLine]);

        // 表格内容真正居中：每格左右留白之差不超过半格
        for (String segment : dataRow.split("│")) {
            if (segment.isBlank()) {
                continue;
            }
            assertTrue(Math.abs(blankWidth(segment, true) - blankWidth(segment, false)) <= 1,
                    "not centered: [" + segment + "]");
        }
        // 合计行、签收行标签也居中
        for (String segment : totalRow.split("│")) {
            if (segment.isBlank()) {
                continue;
            }
            assertTrue(Math.abs(blankWidth(segment, true) - blankWidth(segment, false)) <= 1,
                    "total not centered: [" + segment + "]");
        }
        // 送货人/收货人 水平居左，只相对格子左边框内缩 1 个全角空格
        for (String segment : signRow.split("│")) {
            if (segment.isBlank()) {
                continue;
            }
            assertEquals(2, blankWidth(segment, true), "sign not left-aligned: [" + segment + "]");
            assertTrue(blankWidth(segment, false) > blankWidth(segment, true), segment);
        }
        // 客户名称/日期相对表格外框内缩，不贴边
        assertTrue(meta.startsWith("\u3000") || meta.startsWith(" "), meta);
        assertTrue(meta.endsWith("\u3000") || meta.endsWith(" "), meta);
    }

    /** 预览版式必须和实际打印的行完全一致，否则屏幕和纸面会分叉。 */
    @Test
    void layoutMirrorsPrintedRows() {
        PrintProperties props = new PrintProperties();
        props.getPage().setCols(100);
        props.getPage().setCpi(12);
        props.getPage().setTitleDoubleSize(true);
        props.getPage().setMetaDoubleHeight(true);
        props.getPage().setLineSpacingN(22);
        props.getPage().setTitleGapMm(2);
        props.getPage().setTopMarginMm(6);
        props.getPage().setLeftMarginMm(16);
        props.getPage().setSignRowHeight(1.5);
        props.getPage().setHeightMm(93);
        props.getPage().setWidthMm(241);
        ShippingOrderEscpBuilder builder = new ShippingOrderEscpBuilder(props);

        ShippingOrderPrintRequest req = new ShippingOrderPrintRequest();
        req.setCompanyTitle("怀化市兴隆农业开发有限公司出货单");
        req.setCustomerName("本部食堂");
        req.setOrderNo("XS-202607010206");
        req.setDate("2026-07-01");
        req.setDeliverer("张三");
        ShippingOrderPrintRequest.LineItem line = new ShippingOrderPrintRequest.LineItem();
        line.setProductName("牛腩");
        line.setUnit("公斤");
        line.setQuantity(new BigDecimal("4"));
        line.setUnitPrice(new BigDecimal("70.00"));
        req.setLines(List.of(line));

        ShippingOrderLayout layout = builder.buildLayout(req);

        assertEquals(builder.buildPlainPreview(req),
                String.join("\n", layout.lines().stream().map(ShippingOrderLayout.Line::text).toList()));
        assertEquals(100, layout.cols());
        assertEquals(12, layout.cpi());
        assertEquals(241, layout.widthMm());
        assertEquals(93, layout.heightMm());
        assertEquals(22 / 180.0 * 25.4, layout.lineHeightMm(), 1e-9);
        assertEquals(16.93, layout.printLeftMarginMm(), 0.05);

        ShippingOrderLayout.Line title = layout.lines().get(0);
        assertTrue(title.doubleWidth() && title.doubleHeight() && title.bold());

        // 客户名称/单号/日期行：双高 + 加粗，但不双宽（这行已快占满纸宽）
        ShippingOrderLayout.Line metaLine = layout.lines().get(1);
        assertTrue(metaLine.text().contains("客户名称"), metaLine.text());
        assertTrue(metaLine.doubleHeight() && metaLine.bold(), "客户信息行应为双高加粗");
        assertFalse(metaLine.doubleWidth(), "客户信息行双宽会被截断");
        // 放大行走纸两倍，标题下另有 2mm 间距
        assertEquals(2 * layout.lineHeightMm(), title.heightMm(), 1e-9);
        assertTrue(title.extraFeedMm() > 1.9 && title.extraFeedMm() < 2.2);

        // 上边距 6mm
        assertEquals(6, layout.topMarginMm(), 0.15);

        double used = layout.topMarginMm();
        for (ShippingOrderLayout.Line l : layout.lines()) {
            used += l.heightMm() + l.extraFeedMm();
        }
        assertTrue(used <= layout.heightMm(), "版式超出单页高度: " + used + "mm");

        // 格子的可视高度 = 上下两条横线之间的走纸量；签收格必须正好是货物格的 1.5 倍
        List<ShippingOrderLayout.Line> ls = layout.lines();
        int signIdx = -1;
        int bottomIdx = -1;
        int firstRuleIdx = -1;
        for (int i = 0; i < ls.size(); i++) {
            String text = ls.get(i).text();
            if (firstRuleIdx < 0 && text.startsWith("┌")) {
                firstRuleIdx = i;
            }
            if (signIdx < 0 && text.contains("送货人")) {
                signIdx = i;
            }
            if (text.startsWith("└")) {
                bottomIdx = i;
            }
        }
        assertTrue(firstRuleIdx >= 0 && signIdx > 0 && bottomIdx > signIdx);
        assertTrue(ls.get(signIdx).text().contains("收货人"), ls.get(signIdx).text());
        // 表头格：上框线行 + 文字行
        double headerCell = ls.get(firstRuleIdx).heightMm() + ls.get(firstRuleIdx + 1).heightMm();
        // 签收格：顶线 + 上垫 + 文字 + 下垫
        double signCell = 0;
        for (int i = signIdx - 2; i < bottomIdx; i++) {
            signCell += ls.get(i).heightMm();
        }
        assertEquals(1.5 * headerCell, signCell, 1e-9, "签收行不是 1.5 倍高");
    }

    /** 段首（或段尾）连续空白的显示宽度。 */
    private static int blankWidth(String segment, boolean leading) {
        int width = 0;
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(leading ? i : segment.length() - 1 - i);
            if (c == ' ') {
                width += 1;
            } else if (c == '\u3000') {
                width += 2;
            } else {
                break;
            }
        }
        return width;
    }

    private static boolean containsBytes(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * 统计正文里的 CR、LF 个数。ESC/FS 命令的参数字节可能正好是 0x0A/0x0D
     * （例如 ESC 3 10），打印机会把它当参数吃掉，统计时也必须跳过。
     */
    private static int[] countLineEnds(byte[] data) {
        int cr = 0;
        int lf = 0;
        for (int i = 0; i < data.length; i++) {
            byte b = data[i];
            if (b == 0x1B || b == 0x1C) {
                i += commandLength(data, i) - 1;
            } else if (b == 0x0D) {
                cr++;
            } else if (b == 0x0A) {
                lf++;
            }
        }
        return new int[] {cr, lf};
    }

    /** ESC/FS 命令连同参数的字节数。 */
    private static int commandLength(byte[] data, int i) {
        if (i + 1 >= data.length) {
            return 1;
        }
        if (data[i] == 0x1C) {
            return 2;
        }
        return switch ((char) (data[i + 1] & 0xFF)) {
            case '@', 'E', 'F', 'M', 'P' -> 2;
            case '$' -> 4;
            case '(' -> 7;
            default -> 3;
        };
    }

    /** 带上笔（{@code up}）或下笔的竖线在行内的显示偏移。 */
    private static List<Integer> strokes(String row, boolean up) {
        String want = up ? "│└┘├┤┴┼" : "│┌┐├┤┬┼";
        List<Integer> offsets = new java.util.ArrayList<>();
        int offset = 0;
        for (int i = 0; i < row.length(); i++) {
            char c = row.charAt(i);
            if (want.indexOf(c) >= 0) {
                offsets.add(offset);
            }
            offset += (c > 0x7F) ? 2 : 1;
        }
        return offsets;
    }

    /** 行内每根竖线左侧的显示宽度偏移。 */
    private static List<Integer> barOffsets(String row) {        List<Integer> offsets = new java.util.ArrayList<>();
        int offset = 0;
        for (int i = 0; i < row.length(); i++) {
            char c = row.charAt(i);
            if (c == '│' || c == '├' || c == '┼' || c == '┤') {
                offsets.add(offset);
            }
            offset += (c > 0x7F) ? 2 : 1;
        }
        return offsets;
    }
}
