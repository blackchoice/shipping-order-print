package com.xinglong.print.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "print")
public class PrintProperties {

    private String printerName = "";
    private String encoding = "GBK";
    private Page page = new Page();
    private Table table = new Table();

    public String getPrinterName() {
        return printerName;
    }

    public void setPrinterName(String printerName) {
        this.printerName = printerName;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public Page getPage() {
        return page;
    }

    public void setPage(Page page) {
        this.page = page;
    }

    public Table getTable() {
        return table;
    }

    public void setTable(Table table) {
        this.table = table;
    }

    public static class Page {
        private int cols = 80;
        /** Characters per inch: 10 (ESC P) or 12 (ESC M). Drives column math and preview scale. */
        private int cpi = 12;
        /** Left margin sent to the printer (ESC l) to compensate physical skew; preview ignores this. */
        private double leftMarginMm = 16;
        /** ESC 3 n, n/180 inch. Smaller = shorter form (short stub ~1:3). */
        private int lineSpacingN = 24;
        private boolean titleBold = true;
        /**
         * ESC C n page length in lines.
         * 0 = derive from heightMm and lineSpacingN (recommended for 93mm forms).
         */
        private int pageLengthLines = 0;
        /** After content, send FF to advance exactly one short page. */
        private boolean formFeed = true;
        /**
         * Title at double width + double height. The title is then laid out over half the
         * columns so it still spans the paper instead of being cut off.
         */
        private boolean titleDoubleSize = true;
        /**
         * Customer/order/date line at double height. Double width is not used here: the line
         * is nearly as wide as the form already and would be truncated.
         */
        private boolean metaDoubleHeight = true;
        private boolean metaBold = true;
        /** Blank lines between title and customer row. */
        private int titleGapLines = 0;
        /** Extra gap under the title, in mm (ESC J). Finer than a whole blank line. */
        private double titleGapMm = 2;
        /** Gap from the top edge of the form to the title, in mm (ESC J before the first line). */
        private double topMarginMm = 6;
        /**
         * Signature row height as a multiple of one line. 1.5 prints the row, then a
         * bars-only half line so the vertical rules stay continuous down to the bottom rule.
         */
        private double signRowHeight = 1.5;
        /** ESC SI condensed — keep off; use standard 10 CPI + cols fitted to 241mm. */
        private boolean condensed = false;
        /** When true, shrink line spacing only if the slip does not fit into heightMm. */
        private boolean fitToForm = true;
        /**
         * Line terminator: {@code crlf} (default), {@code lf} or {@code cr}.
         * CR+LF requires the printer's AUTO LF switch to be off, otherwise every line feeds
         * twice. {@code lf} feeds one line and returns to the left margin either way.
         */
        private String lineEnding = "crlf";
        /** Physical form width in mm (e.g. 241). */
        private double widthMm = 241;
        /** Physical form height in mm (e.g. 93). */
        private double heightMm = 93;

        public int getCols() {
            return cols;
        }

        public void setCols(int cols) {
            this.cols = cols;
        }

        public int getCpi() {
            return cpi;
        }

        public void setCpi(int cpi) {
            this.cpi = cpi;
        }

        public double getLeftMarginMm() {
            return leftMarginMm;
        }

        public void setLeftMarginMm(double leftMarginMm) {
            this.leftMarginMm = leftMarginMm;
        }

        /** ESC l columns for the configured left margin at the current CPI. */
        public int leftMarginColumns() {
            if (leftMarginMm <= 0 || cpi <= 0) {
                return 0;
            }
            return Math.max(0, (int) Math.round(leftMarginMm / 25.4 * cpi));
        }

        /** Effective left margin after rounding to whole columns (what the printer actually uses). */
        public double effectiveLeftMarginMm() {
            return leftMarginColumns() * 25.4 / Math.max(1, cpi);
        }

        public int getLineSpacingN() {
            return lineSpacingN;
        }

        public void setLineSpacingN(int lineSpacingN) {
            this.lineSpacingN = lineSpacingN;
        }

        public boolean isTitleBold() {
            return titleBold;
        }

        public void setTitleBold(boolean titleBold) {
            this.titleBold = titleBold;
        }

        public int getPageLengthLines() {
            return pageLengthLines;
        }

        public void setPageLengthLines(int pageLengthLines) {
            this.pageLengthLines = pageLengthLines;
        }

        /**
         * Effective page length in lines for ESC C.
         * If pageLengthLines &gt; 0 use it; else derive from heightMm and lineSpacingN.
         */
        public int resolvePageLengthLines() {
            if (pageLengthLines > 0) {
                return pageLengthLines;
            }
            if (heightMm <= 0 || lineSpacingN <= 0) {
                return 0;
            }
            // lines = heightMm / 25.4 * 180 / n
            int lines = (int) Math.round(heightMm / 25.4 * 180.0 / lineSpacingN);
            return Math.max(1, Math.min(127, lines));
        }

        public boolean isFormFeed() {
            return formFeed;
        }

        public void setFormFeed(boolean formFeed) {
            this.formFeed = formFeed;
        }

        public boolean isTitleDoubleSize() {
            return titleDoubleSize;
        }

        public void setTitleDoubleSize(boolean titleDoubleSize) {
            this.titleDoubleSize = titleDoubleSize;
        }

        public boolean isMetaDoubleHeight() {
            return metaDoubleHeight;
        }

        public void setMetaDoubleHeight(boolean metaDoubleHeight) {
            this.metaDoubleHeight = metaDoubleHeight;
        }

        public double getTopMarginMm() {
            return topMarginMm;
        }

        public void setTopMarginMm(double topMarginMm) {
            this.topMarginMm = topMarginMm;
        }

        public boolean isMetaBold() {
            return metaBold;
        }

        public void setMetaBold(boolean metaBold) {
            this.metaBold = metaBold;
        }

        public int getTitleGapLines() {
            return titleGapLines;
        }

        public void setTitleGapLines(int titleGapLines) {
            this.titleGapLines = titleGapLines;
        }

        public double getTitleGapMm() {
            return titleGapMm;
        }

        public void setTitleGapMm(double titleGapMm) {
            this.titleGapMm = titleGapMm;
        }

        public double getSignRowHeight() {
            return signRowHeight;
        }

        public void setSignRowHeight(double signRowHeight) {
            this.signRowHeight = signRowHeight;
        }

        /** mm converted to ESC J units (1/180 inch). */
        public int mmToUnits180(double mm) {
            if (mm <= 0) {
                return 0;
            }
            return (int) Math.round(mm / 25.4 * 180.0);
        }

        public boolean isCondensed() {
            return condensed;
        }

        public void setCondensed(boolean condensed) {
            this.condensed = condensed;
        }

        public boolean isFitToForm() {
            return fitToForm;
        }

        public void setFitToForm(boolean fitToForm) {
            this.fitToForm = fitToForm;
        }

        public String getLineEnding() {
            return lineEnding;
        }

        public void setLineEnding(String lineEnding) {
            this.lineEnding = lineEnding;
        }

        /**
         * Lines that make up one form at the given spacing. Rounded (not floored) so
         * pageLines * spacing stays as close as possible to heightMm; otherwise the
         * print position drifts up the form on every following slip.
         */
        public int capacityLines(int spacingN) {
            if (heightMm <= 0 || spacingN <= 0) {
                return 0;
            }
            int lines = (int) Math.round(heightMm / 25.4 * 180.0 / spacingN);
            return Math.max(1, Math.min(127, lines));
        }

        public double getWidthMm() {
            return widthMm;
        }

        public void setWidthMm(double widthMm) {
            this.widthMm = widthMm;
        }

        public double getHeightMm() {
            return heightMm;
        }

        public void setHeightMm(double heightMm) {
            this.heightMm = heightMm;
        }
    }

    public static class Table {
        private int colIndex = 4;
        private int colName = 22;
        private int colUnit = 6;
        private int colQty = 8;
        private int colPrice = 10;
        private int colAmount = 12;
        private int colRemark = 12;
        private int maxRows = 7;
        /** If false, only print real line items (no empty padded rows) — shorter slip. */
        private boolean padEmptyRows = false;

        public int getColIndex() {
            return colIndex;
        }

        public void setColIndex(int colIndex) {
            this.colIndex = colIndex;
        }

        public int getColName() {
            return colName;
        }

        public void setColName(int colName) {
            this.colName = colName;
        }

        public int getColUnit() {
            return colUnit;
        }

        public void setColUnit(int colUnit) {
            this.colUnit = colUnit;
        }

        public int getColQty() {
            return colQty;
        }

        public void setColQty(int colQty) {
            this.colQty = colQty;
        }

        public int getColPrice() {
            return colPrice;
        }

        public void setColPrice(int colPrice) {
            this.colPrice = colPrice;
        }

        public int getColAmount() {
            return colAmount;
        }

        public void setColAmount(int colAmount) {
            this.colAmount = colAmount;
        }

        public int getColRemark() {
            return colRemark;
        }

        public void setColRemark(int colRemark) {
            this.colRemark = colRemark;
        }

        public int getMaxRows() {
            return maxRows;
        }

        public void setMaxRows(int maxRows) {
            this.maxRows = maxRows;
        }

        public boolean isPadEmptyRows() {
            return padEmptyRows;
        }

        public void setPadEmptyRows(boolean padEmptyRows) {
            this.padEmptyRows = padEmptyRows;
        }
    }
}
