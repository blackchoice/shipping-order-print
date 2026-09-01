package com.xinglong.print.print;

import com.xinglong.print.config.PrintProperties;
import com.xinglong.print.print.escp.EscpCommandWriter;
import com.xinglong.print.web.dto.ShippingOrderLayout;
import com.xinglong.print.web.dto.ShippingOrderPrintRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 出货单 ESC/P 版式。
 * <p>
 * 框线为 GB 制表符；明细/合计/签收的竖线位置必须落在同一套列宽上，否则竖线会断、错位。
 * 填充只用全角空格；单元格内容先补成偶数字宽，避免半角空格把整列挤歪。
 * <p>
 * 竖线要打成连续实线，行距必须略小于汉字字身高度（24 点），见 {@code line-spacing-n}：
 * 行距更大时上下两行的 │ 之间会留白，看起来就是虚线或短一截。
 */
@Component
public class ShippingOrderEscpBuilder {

    private static final int COL_COUNT = 7;
    /** 每根竖线 │ 占 2 列；共 8 根 → 16 */
    private static final int VBAR_DISPLAY = 2;
    private static final int VBAR_COUNT = COL_COUNT + 1;
    private static final int VBAR_OVERHEAD = VBAR_COUNT * VBAR_DISPLAY;
    /** 24 针汉字字身高 24 点（1/180 英寸）；竖线字符恒定画满这么高，与行距无关。 */
    private static final int CELL_UNITS_180 = 24;
    /** 送货人/收货人 居左时相对格子左边框内缩的列数（1 个全角空格）。 */
    private static final int SIGN_INSET = 2;

    private static final String V = "│";
    private static final String H = "─";
    private static final String TL = "┌";
    private static final String TR = "┐";
    private static final String BL = "└";
    private static final String BR = "┘";
    private static final String LJ = "├";
    private static final String RJ = "┤";
    private static final String TJ = "┬";
    private static final String BJ = "┴";
    private static final String CJ = "┼";

    private final PrintProperties props;

    public ShippingOrderEscpBuilder(PrintProperties props) {
        this.props = props;
    }

    /**
     * 一行输出。{@code spacingMultiplier} 是该行走纸相对基准行距的倍数
     * （放大字号的行需要 2 倍，签收加高的空行是 0.5 倍）；
     * {@code extraFeed180} 是该行之后的一次性走纸（标题下的 2mm 间距）。
     */
    private record Row(String text, double spacingMultiplier, int extraFeed180,
                       boolean doubleWidth, boolean doubleHeight, boolean bold) {
        static Row of(String text) {
            return new Row(text, 1, 0, false, false, false);
        }
    }

    public byte[] build(ShippingOrderPrintRequest req) {
        PrintProperties.Page page = props.getPage();
        List<Row> rows = buildRows(req);
        int spacingN = resolveSpacing(page, rows);
        int pageLines = resolvePageLines(page, spacingN);

        EscpCommandWriter w = new EscpCommandWriter(props.getEncoding());
        w.init();
        w.condensed(false);
        applyPitch(w, page);
        int leftCols = page.leftMarginColumns();
        if (leftCols > 0) {
            w.setLeftMarginColumns(leftCols);
            // 右边界 = 左边距 + 内容列，防止超宽时自动换行
            w.setRightMarginColumns(Math.min(255, leftCols + resolveCols(page)));
        }
        w.setLineSpacing(spacingN);
        if (pageLines > 0) {
            w.setPageLengthLines(pageLines);
        }
        // 纸面顶端到标题的留白：用 ESC J 走纸，比空整行精确
        w.advanceVertical(page.mmToUnits180(page.getTopMarginMm()));

        int currentSpacing = spacingN;
        for (Row row : rows) {
            int spacing = rowSpacing(spacingN, row);
            if (spacing != currentSpacing) {
                w.setLineSpacing(spacing);
                currentSpacing = spacing;
            }
            writeRow(w, page, row);
            w.advanceVertical(row.extraFeed180());
        }
        if (page.isFormFeed()) {
            w.formFeed();
        }
        return w.toByteArray();
    }

    public String buildPlainPreview(ShippingOrderPrintRequest req) {
        return String.join("\n", buildRows(req).stream().map(Row::text).toList());
    }

    /** 前端按此版式 1:1 复现纸面结果；数据与 {@link #build} 用的完全是同一份行。 */
    public ShippingOrderLayout buildLayout(ShippingOrderPrintRequest req) {
        PrintProperties.Page page = props.getPage();
        List<Row> rows = buildRows(req);
        int spacingN = resolveSpacing(page, rows);

        List<ShippingOrderLayout.Line> lines = new ArrayList<>(rows.size());
        for (Row row : rows) {
            lines.add(new ShippingOrderLayout.Line(
                    row.text(),
                    row.spacingMultiplier(),
                    units180ToMm(rowSpacing(spacingN, row)),
                    units180ToMm(row.extraFeed180()),
                    row.doubleWidth(),
                    row.doubleHeight(),
                    row.bold()));
        }
        return new ShippingOrderLayout(
                resolveCols(page),
                page.getCpi(),
                page.getWidthMm(),
                page.getHeightMm(),
                spacingN,
                units180ToMm(spacingN),
                units180ToMm(page.mmToUnits180(page.getTopMarginMm())),
                page.effectiveLeftMarginMm(),
                resolvePageLines(page, spacingN),
                lines);
    }

    /** 基准行距：装得下就用配置值，装不下才压缩。 */
    private int resolveSpacing(PrintProperties.Page page, List<Row> rows) {
        double totalMultiplier = 0;
        int extraFeed = 0;
        for (Row row : rows) {
            totalMultiplier += row.spacingMultiplier();
            extraFeed += row.extraFeed180();
        }
        extraFeed += page.mmToUnits180(page.getTopMarginMm());

        int spacingN = page.getLineSpacingN();
        int formUnits = page.mmToUnits180(page.getHeightMm());
        if (page.isFitToForm() && formUnits > 0 && totalMultiplier > 0) {
            int needed = (int) Math.ceil(totalMultiplier * spacingN) + extraFeed;
            if (needed > formUnits) {
                spacingN = Math.max(14, (int) Math.floor((formUnits - extraFeed) / totalMultiplier));
            }
        }
        return spacingN;
    }

    private int resolvePageLines(PrintProperties.Page page, int spacingN) {
        return page.getPageLengthLines() > 0
                ? page.getPageLengthLines()
                : page.capacityLines(spacingN);
    }

    private static int rowSpacing(int spacingN, Row row) {
        return Math.max(1, (int) Math.round(spacingN * row.spacingMultiplier()));
    }

    private static double units180ToMm(int units180) {
        return units180 / 180.0 * 25.4;
    }

    private void applyPitch(EscpCommandWriter w, PrintProperties.Page page) {
        // 12 CPI：241mm 纸用 ~100 列可铺满；10 CPI+84 列在默认 12CPI 机上会只占约 75%
        if (page.getCpi() == 10) {
            w.pitch10();
        } else {
            w.pitch12();
        }
    }

    private List<Row> buildRows(ShippingOrderPrintRequest req) {
        PrintProperties.Page page = props.getPage();
        PrintProperties.Table table = props.getTable();
        int cols = resolveCols(page);
        int inner = cols - VBAR_OVERHEAD;
        int[] tw = fittedColWidths(table, inner);

        List<String> out = new ArrayList<>();

        // 标题双宽时每个字占两格，故按半幅排版，打出来仍是整幅宽
        String title = blankToDefault(req.getCompanyTitle(), "怀化市兴隆农业开发有限公司出货单");
        int titleWidth = page.isTitleDoubleSize() ? even(cols / 2) : cols;
        int titleIndex = out.size();
        out.add(stripTrailingBlanks(center(title, titleWidth)));
        for (int g = 0; g < Math.max(0, page.getTitleGapLines()); g++) {
            out.add("");
        }

        String metaLeft = "客户名称：" + nullToEmpty(req.getCustomerName());
        String metaMid = "单号：" + nullToEmpty(req.getOrderNo());
        String metaRight = "日期：" + nullToEmpty(req.getDate());
        int metaIndex = out.size();
        // 相对表格外框各内缩 2 列，避免贴边看起来偏左/偏右
        out.add(padOrTrim(spreadThree(metaLeft, metaMid, metaRight, cols, 2), cols));

        // —— 表头 + 明细：统一 7 列宽 tw，竖线贯穿 ——
        out.add(hRule(null, tw));
        out.add(dataRow(tw,
                cell("序号", tw[0]),
                cell("货品名称", tw[1]),
                cell("单位", tw[2]),
                cell("数量", tw[3]),
                cell("单价", tw[4]),
                cell("金额", tw[5]),
                cell("备注", tw[6])));
        out.add(hRule(tw, tw));

        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        List<ShippingOrderPrintRequest.LineItem> lines = req.getLines();
        int rowCount = resolveRowCount(table, lines);
        for (int i = 0; i < rowCount; i++) {
            if (i > 0) {
                out.add(hRule(tw, tw));
            }
            if (i < lines.size()) {
                ShippingOrderPrintRequest.LineItem item = lines.get(i);
                BigDecimal amount = item.amount();
                total = total.add(amount);
                out.add(dataRow(tw,
                        cell(String.valueOf(i + 1), tw[0]),
                        cell(nullToEmpty(item.getProductName()), tw[1]),
                        cell(nullToEmpty(item.getUnit()), tw[2]),
                        cell(stripTrailingZeros(item.getQuantity()), tw[3]),
                        cell(formatMoney(item.getUnitPrice()), tw[4]),
                        cell(formatMoney(amount), tw[5]),
                        cell(nullToEmpty(item.getRemark()), tw[6])));
            } else {
                out.add(dataRow(tw,
                        cell(String.valueOf(i + 1), tw[0]),
                        cell("", tw[1]),
                        cell("", tw[2]),
                        cell("", tw[3]),
                        cell("", tw[4]),
                        cell("", tw[5]),
                        cell("", tw[6])));
            }
        }

        // —— 合计：「总金额：」起于单价列（合并 1–3 与 5–6）——
        int[] totalTw = {tw[0], mergeWidth(tw, 1, 3), tw[4], mergeWidth(tw, 5, 6)};
        assertRowWidth(totalTw, cols);
        out.add(hRule(tw, totalTw));
        String totalCn = AmountToChinese.toChinese(total);
        out.add(dataRow(totalTw,
                cell("总金额大写", totalTw[0]),
                cell(totalCn, totalTw[1]),
                cell("总金额：", totalTw[2]),
                cell(formatMoney(total), totalTw[3])));

        // —— 签收：格内竖直居中 = 上半行垫高 + 文字 + 下半行垫高 ——
        int signLeft = mergeWidth(tw, 0, 3);
        int signRight = mergeWidth(tw, 4, 6);
        int[] signTw = {signLeft, signRight};
        assertRowWidth(signTw, cols);
        out.add(hRule(totalTw, signTw));
        // 多出的高度上下分摊，文字行落在格子正中。
        // 下垫行只走半个字身：竖线字符恒定画满一个字身（24 点），
        // 下一行的横线又画在字身正中，两者正好收口，竖线不会从底框戳出去。
        double padTotal = (page.getSignRowHeight() - 1.0) * 2;
        double padBottomMult = Math.min(padTotal,
                CELL_UNITS_180 / 2.0 / Math.max(1, page.getLineSpacingN()));
        double padTopMult = padTotal - padBottomMult;
        int signPadTopIndex = -1;
        int signFillerIndex = -1;
        if (padTopMult > 0.01) {
            signPadTopIndex = out.size();
            out.add(dataRow(signTw, cell("", signTw[0]), cell("", signTw[1])));
        }
        out.add(dataRow(signTw,
                cellLeft("送货人：" + nullToEmpty(req.getDeliverer()), signTw[0], SIGN_INSET),
                cellLeft("收货人：" + nullToEmpty(req.getReceiver()), signTw[1], SIGN_INSET)));
        if (padBottomMult > 0.01) {
            signFillerIndex = out.size();
            out.add(dataRow(signTw, cell("", signTw[0]), cell("", signTw[1])));
        }
        out.add(hRule(signTw, null));

        for (int i = 0; i < out.size(); i++) {
            String row = out.get(i);
            if (row.isEmpty()) {
                continue;
            }
            if (row.startsWith(TL) || row.startsWith(LJ) || row.startsWith(BL) || row.startsWith(V)
                    || row.contains("客户名称")) {
                out.set(i, padOrTrim(row, cols));
            }
        }

        List<Row> rows = new ArrayList<>(out.size());
        for (int i = 0; i < out.size(); i++) {
            String text = out.get(i);
            if (i == titleIndex) {
                // 放大的字高约两倍，走纸也要两倍，否则会被下一行压住
                double mult = page.isTitleDoubleSize() ? 2 : 1;
                rows.add(new Row(text, mult, page.mmToUnits180(page.getTitleGapMm()),
                        page.isTitleDoubleSize(), page.isTitleDoubleSize(), page.isTitleBold()));
            } else if (i == metaIndex) {
                double mult = page.isMetaDoubleHeight() ? 2 : 1;
                rows.add(new Row(text, mult, 0, false, page.isMetaDoubleHeight(), page.isMetaBold()));
            } else if (i == signPadTopIndex) {
                rows.add(new Row(text, padTopMult, 0, false, false, false));
            } else if (i == signFillerIndex) {
                rows.add(new Row(text, padBottomMult, 0, false, false, false));
            } else {
                rows.add(Row.of(text));
            }
        }
        return rows;
    }

    /** 去掉行尾填充，双宽行末尾的空白会把打印头推过右边界。 */
    private static String stripTrailingBlanks(String s) {
        int end = s.length();
        while (end > 0 && (s.charAt(end - 1) == ' ' || s.charAt(end - 1) == '\u3000')) {
            end--;
        }
        return s.substring(0, end);
    }

    private void writeRow(EscpCommandWriter w, PrintProperties.Page page, Row row) {
        if (row.doubleWidth()) {
            w.doubleWidth(true);
        }
        if (row.doubleHeight()) {
            w.doubleHeight(true);
        }
        if (row.bold()) {
            w.bold(true);
        }
        w.text(row.text());
        if (row.bold()) {
            w.bold(false);
        }
        if (row.doubleHeight()) {
            w.doubleHeight(false);
        }
        if (row.doubleWidth()) {
            w.doubleWidth(false);
        }
        endLine(w, page);
    }

    /**
     * 默认只发 LF：LF 本身走一行并回到左边界，不管打印机 AUTO LF 开关是否打开都只走一行。
     * CR+LF 在 AUTO LF 开启时会双倍走纸；单独 CR 在 AUTO LF 关闭时根本不换行。
     */
    private void endLine(EscpCommandWriter w, PrintProperties.Page page) {
        String mode = page.getLineEnding() == null ? "lf" : page.getLineEnding().trim().toLowerCase();
        switch (mode) {
            case "crlf" -> w.crlf();
            case "cr" -> w.cr();
            default -> w.lf();
        }
    }

    /** 按纸宽和字距推算列数（扣齿孔）。 */
    private int resolveCols(PrintProperties.Page page) {
        int configured = page.getCols();
        if (configured > 0) {
            return even(Math.max(VBAR_OVERHEAD + COL_COUNT * 2, configured));
        }
        double printableMm = Math.max(80, page.getWidthMm() - 26);
        int derived = (int) Math.floor(printableMm / 25.4 * page.getCpi());
        return even(Math.max(VBAR_OVERHEAD + COL_COUNT * 2, derived));
    }

    /** 合并 from..to 列（含），并把中间竖线宽度并入单元格，保证整行仍 = cols。 */
    private static int mergeWidth(int[] tw, int from, int to) {
        int w = 0;
        for (int i = from; i <= to; i++) {
            w += tw[i];
        }
        w += (to - from) * VBAR_DISPLAY;
        return even(w);
    }

    private static void assertRowWidth(int[] tw, int cols) {
        int sum = (tw.length + 1) * VBAR_DISPLAY;
        for (int w : tw) {
            sum += w;
        }
        if (sum != cols) {
            throw new IllegalStateException("row width " + sum + " != cols " + cols);
        }
    }

    private static String dataRow(int[] tw, String... cells) {
        if (cells.length != tw.length) {
            throw new IllegalArgumentException("cell/width size mismatch");
        }
        StringBuilder sb = new StringBuilder(V);
        for (int i = 0; i < cells.length; i++) {
            sb.append(padOrTrim(cells[i], tw[i])).append(V);
        }
        return sb.toString();
    }

    private static String hRule(int[] above, int[] below) {
        java.util.TreeSet<Integer> up = boundaries(above);
        java.util.TreeSet<Integer> down = boundaries(below);
        java.util.TreeSet<Integer> all = new java.util.TreeSet<>(up);
        all.addAll(down);
        int first = all.first();
        int last = all.last();
        StringBuilder sb = new StringBuilder();
        int offset = 0;
        for (int pos : all) {
            sb.append(hSeg(pos - offset));
            sb.append(junction(pos == first, pos == last, up.contains(pos), down.contains(pos)));
            offset = pos + VBAR_DISPLAY;
        }
        return sb.toString();
    }

    /** 各根竖线在行内的显示偏移；{@code null} 表示这一侧没有行（表格最上/最下）。 */
    private static java.util.TreeSet<Integer> boundaries(int[] tw) {
        java.util.TreeSet<Integer> set = new java.util.TreeSet<>();
        if (tw == null) {
            return set;
        }
        int offset = 0;
        for (int w : tw) {
            set.add(offset);
            offset += VBAR_DISPLAY + w;
        }
        set.add(offset);
        return set;
    }

    /**
     * 按上下两侧是否真有竖线选交叉字符。合并列的边界若用 {@code ─}（没有竖笔），
     * 上一行的竖线就只能停在自己字身底部，比横线短半个字身，看起来差一截。
     */
    private static String junction(boolean first, boolean last, boolean up, boolean down) {
        if (first) {
            return up && down ? LJ : (down ? TL : BL);
        }
        if (last) {
            return up && down ? RJ : (down ? TR : BR);
        }
        return up && down ? CJ : (down ? TJ : BJ);
    }

    private static String hSeg(int displayWidth) {
        // 不能借 even()：它有最小值 2，第一根竖线前的 0 宽会多出一段横线
        return H.repeat(Math.max(0, displayWidth) / VBAR_DISPLAY);
    }

    private int[] fittedColWidths(PrintProperties.Table t, int inner) {
        inner = even(inner);
        int[] raw = {
                Math.max(4, t.getColIndex()),
                Math.max(8, t.getColName()),
                Math.max(4, t.getColUnit()),
                Math.max(4, t.getColQty()),
                Math.max(4, t.getColPrice()),
                Math.max(4, t.getColAmount()),
                Math.max(4, t.getColRemark())
        };
        int sum = 0;
        for (int v : raw) {
            sum += v;
        }
        int[] out = new int[raw.length];
        int used = 0;
        for (int i = 0; i < raw.length; i++) {
            if (i == raw.length - 1) {
                out[i] = even(Math.max(4, inner - used));
            } else {
                int w = (int) Math.round(raw[i] * (inner * 1.0 / sum));
                out[i] = even(Math.max(4, w));
                used += out[i];
            }
        }
        // 若超宽，从备注列收回
        int total = 0;
        for (int v : out) {
            total += v;
        }
        while (total > inner && out[6] > 4) {
            out[6] = even(out[6] - 2);
            total -= 2;
        }
        while (total > inner && out[1] > 8) {
            out[1] = even(out[1] - 2);
            total -= 2;
        }
        // 「总金额大写」=10，首列至少 12，避免居中补空后截断末字
        if (out[0] < 12) {
            int need = even(12 - out[0]);
            out[0] = 12;
            out[1] = even(Math.max(8, out[1] - need));
        }
        // 余量进货品名称列
        total = 0;
        for (int v : out) {
            total += v;
        }
        if (total < inner) {
            out[1] = even(out[1] + (inner - total));
        } else if (total > inner) {
            out[1] = even(Math.max(8, out[1] - (total - inner)));
        }
        return out;
    }

    private int resolveRowCount(PrintProperties.Table table, List<ShippingOrderPrintRequest.LineItem> lines) {
        int maxRows = Math.max(1, table.getMaxRows());
        int actual = lines == null ? 0 : lines.size();
        if (!table.isPadEmptyRows()) {
            return Math.min(maxRows, Math.max(1, actual));
        }
        return maxRows;
    }

    /** 居中：左右填充按半格精确均分，内容宽度为奇数时也只偏 1/2 格。 */
    private static String cell(String text, int width) {
        width = even(width);
        String t = nullToEmpty(text);
        if (displayWidth(t) > width) {
            t = trimToWidth(t, width);
        }
        int pad = width - displayWidth(t);
        int left = pad / 2;
        return spaces(left) + t + spaces(pad - left);
    }

    private static String cellLeft(String text, int width) {
        return cellLeft(text, width, 0);
    }

    /** 居左；{@code inset} 是相对格子左边框的内缩列数，免得文字贴着竖线。 */
    private static String cellLeft(String text, int width, int inset) {
        width = even(width);
        inset = Math.min(even(Math.max(0, inset)), Math.max(0, width - 2));
        return spaces(inset) + padOrTrim(text, width - inset);
    }

    private static String spreadThree(String left, String mid, String right, int width) {
        return spreadThree(left, mid, right, width, 0);
    }

    /**
     * 左中右三段铺满 {@code width}；{@code inset} 是两侧相对表格外框的内缩列数，
     * 避免客户名称贴左、日期贴右看起来偏出去。
     */
    private static String spreadThree(String left, String mid, String right, int width, int inset) {
        width = even(width);
        inset = Math.max(0, even(inset));
        int inner = Math.max(2, width - 2 * inset);
        String l = nullToEmpty(left);
        String m = nullToEmpty(mid);
        String r = nullToEmpty(right);
        int lw = displayWidth(l);
        int rw = displayWidth(r);
        if (lw + displayWidth(m) + rw > inner) {
            m = trimToWidth(m, Math.max(0, inner - lw - rw));
        }
        int gap = inner - lw - displayWidth(m) - rw;
        int gap1 = gap / 2;
        return spaces(inset) + l + spaces(gap1) + m + spaces(gap - gap1) + r + spaces(inset);
    }

    /**
     * 半格用 ASCII 空格、整格用全角空格。两者宽度都按同一字距计（半角 = 1，汉字 = 2），
     * 所以混用不会破坏列对齐，反而能把内容精确居中。
     */
    private static String spaces(int displayWidth) {
        if (displayWidth <= 0) {
            return "";
        }
        return "\u3000".repeat(displayWidth / 2) + " ".repeat(displayWidth % 2);
    }

    private static String padOrTrim(String s, int width) {
        width = even(width);
        String t = nullToEmpty(s);
        int dw = displayWidth(t);
        if (dw == width) {
            return t;
        }
        if (dw < width) {
            return t + spaces(width - dw);
        }
        return trimToWidth(t, width);
    }

    private static String center(String s, int width) {
        return cell(s, width);
    }

    private static int even(int n) {
        return n < 2 ? 2 : (n % 2 == 0 ? n : n - 1);
    }

    static int displayWidth(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        int w = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            w += (cp > 0x7F) ? 2 : 1;
            i += Character.charCount(cp);
        }
        return w;
    }

    /** 截断到不超过 width，不补齐。 */
    private static String trimToWidth(String s, int width) {
        StringBuilder sb = new StringBuilder();
        int w = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            int cw = (cp > 0x7F) ? 2 : 1;
            if (w + cw > width) {
                break;
            }
            sb.appendCodePoint(cp);
            w += cw;
            i += Character.charCount(cp);
        }
        return sb.toString();
    }

    private static String formatMoney(BigDecimal v) {
        if (v == null) {
            return "0.00";
        }
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String stripTrailingZeros(BigDecimal v) {
        if (v == null) {
            return "";
        }
        return v.stripTrailingZeros().toPlainString();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String blankToDefault(String s, String def) {
        return (s == null || s.isBlank()) ? def : s;
    }
}
