package com.xinglong.print.print.escp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;

/**
 * Minimal ESC/P (LQ-1600K compatible) command writer for 24-pin Chinese pin printers.
 * Text is encoded with the configured charset (typically GBK).
 */
public class EscpCommandWriter {

    private static final byte ESC = 0x1B;
    private static final byte FS = 0x1C;
    private static final byte CR = 0x0D;
    private static final byte LF = 0x0A;
    private static final byte FF = 0x0C;
    private static final byte NUL = 0x00;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream(2048);
    private final Charset charset;

    public EscpCommandWriter(String encoding) {
        this.charset = Charset.forName(encoding);
    }

    public EscpCommandWriter init() {
        write(ESC, (byte) '@');
        // FS & — select Chinese character mode (LQ-1600K / Chinese ESC/P)
        write(FS, (byte) '&');
        return this;
    }

    /** ESC 3 n — line spacing = n/180 inch (24-pin). */
    public EscpCommandWriter setLineSpacing(int n) {
        write(ESC, (byte) '3', (byte) (n & 0xFF));
        return this;
    }

    /** ESC J n — one-off paper advance of n/180 inch; does not change the set line spacing. */
    public EscpCommandWriter advanceVertical(int units180) {
        int n = Math.max(0, Math.min(255, units180));
        if (n > 0) {
            write(ESC, (byte) 'J', (byte) n);
        }
        return this;
    }

    /** ESC C n — page length in lines (1–127). Short continuous forms need this. */
    public EscpCommandWriter setPageLengthLines(int lines) {
        int n = Math.max(1, Math.min(127, lines));
        write(ESC, (byte) 'C', (byte) n);
        return this;
    }

    /** ESC C NUL n — page length in inches (1–22). */
    public EscpCommandWriter setPageLengthInches(int inches) {
        int n = Math.max(1, Math.min(22, inches));
        write(ESC, (byte) 'C', NUL, (byte) n);
        return this;
    }

    /**
     * ESC ( C 2 0 mL mH — page length in 1/360 inch (ESC/P2).
     * Prefer for short continuous forms where inch granularity is too coarse.
     */
    public EscpCommandWriter setPageLength360(int units360) {
        int n = Math.max(1, Math.min(0xFFFF, units360));
        write(ESC, (byte) '(', (byte) 'C', (byte) 2, (byte) 0,
                (byte) (n & 0xFF), (byte) ((n >> 8) & 0xFF));
        return this;
    }

    /** ESC l n — left margin in columns (current pitch). */
    public EscpCommandWriter setLeftMarginColumns(int columns) {
        int n = Math.max(0, Math.min(255, columns));
        write(ESC, (byte) 'l', (byte) n);
        return this;
    }

    /** ESC Q n — right margin in columns (current pitch). Prevents auto line-wrap. */
    public EscpCommandWriter setRightMarginColumns(int columns) {
        int n = Math.max(1, Math.min(255, columns));
        write(ESC, (byte) 'Q', (byte) n);
        return this;
    }

    /** ESC $ nL nH — absolute horizontal position in 1/60 inch units. */
    public EscpCommandWriter absoluteHorizontal(int units) {
        int n = Math.max(0, units);
        write(ESC, (byte) '$', (byte) (n & 0xFF), (byte) ((n >> 8) & 0xFF));
        return this;
    }

    public EscpCommandWriter bold(boolean on) {
        write(ESC, on ? (byte) 'E' : (byte) 'F');
        return this;
    }

    /**
     * Condensed (~15–17 CPI). One ASCII SI + Chinese FS SI (do not stack ESC SI,
     * or Chinese glyphs become overly narrow ≈ half page width).
     */
    public EscpCommandWriter condensed(boolean on) {
        if (on) {
            write((byte) 0x0F); // SI
            write(FS, (byte) 0x0F); // FS SI — Chinese condensed
        } else {
            write((byte) 0x12); // DC2
            write(FS, (byte) 0x12); // FS DC2
        }
        return this;
    }

    /** ESC M — 12 CPI (useful on 241mm forms when not using SI condensed). */
    public EscpCommandWriter pitch12() {
        write(ESC, (byte) 'M');
        return this;
    }

    /** ESC P — 10 CPI. */
    public EscpCommandWriter pitch10() {
        write(ESC, (byte) 'P');
        return this;
    }

    /** Double-width (ASCII ESC W + Chinese FS SO). */
    public EscpCommandWriter doubleWidth(boolean on) {
        write(ESC, (byte) 'W', on ? (byte) 1 : (byte) 0);
        write(FS, on ? (byte) 0x0E : (byte) 0x14); // FS SO / FS DC4
        return this;
    }

    /** Double-height ESC w n (LQ). */
    public EscpCommandWriter doubleHeight(boolean on) {
        write(ESC, (byte) 'w', on ? (byte) 1 : (byte) 0);
        return this;
    }

    public EscpCommandWriter text(String s) {
        if (s == null || s.isEmpty()) {
            return this;
        }
        try {
            out.write(s.getBytes(charset));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    public EscpCommandWriter crlf() {
        write(CR, LF);
        return this;
    }

    /** CR only — feeds a line only when the printer's AUTO LF switch is on. */
    public EscpCommandWriter cr() {
        write(CR);
        return this;
    }

    /**
     * LF only — feeds exactly one line and returns to the left margin, regardless of the
     * AUTO LF switch. Safest terminator for fixed-height continuous forms.
     */
    public EscpCommandWriter lf() {
        write(LF);
        return this;
    }

    public EscpCommandWriter formFeed() {
        write(FF);
        return this;
    }

    public byte[] toByteArray() {
        return out.toByteArray();
    }

    private void write(byte... bytes) {
        out.write(bytes, 0, bytes.length);
    }
}
