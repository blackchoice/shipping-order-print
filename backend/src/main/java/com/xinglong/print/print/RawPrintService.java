package com.xinglong.print.print;

import com.xinglong.print.config.PrintProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class RawPrintService {

    private static final Logger log = LoggerFactory.getLogger(RawPrintService.class);

    private final PrintProperties props;
    private final Path rawPrintScript;

    public RawPrintService(PrintProperties props) {
        this.props = props;
        this.rawPrintScript = resolveRawPrintScript();
    }

    public List<String> listPrinters() {
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        List<String> names = new ArrayList<>();
        for (PrintService service : services) {
            names.add(service.getName());
        }
        return names;
    }

    public void printRaw(byte[] data) {
        printRaw(data, props.getPrinterName());
    }

    public void printRaw(byte[] data, String printerName) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("打印数据为空");
        }
        String name = printerName == null ? "" : printerName.trim();
        if (name.isEmpty()) {
            throw new IllegalStateException(
                    "未配置打印机名称。请在 application.yml 设置 print.printer-name，或请求参数传入 printerName。"
                            + " 可用打印机: " + listPrinters());
        }
        if (findByName(name) == null) {
            throw new IllegalStateException(
                    "找不到打印机: \"" + name + "\"。可用打印机: " + listPrinters());
        }
        log.info("提交 Win32 RAW 打印: printer=\"{}\", bytes={}", name, data.length);
        try {
            sendViaWinSpoolRaw(name, data);
            log.info("RAW 打印已写入后台打印队列: printer=\"{}\"", name);
        } catch (Exception e) {
            log.error("RAW 打印失败: printer=\"{}\", error={}", name, e.getMessage(), e);
            throw new IllegalStateException("提交打印任务失败: " + e.getMessage(), e);
        }
    }

    /**
     * Windows pin printers need datatype=RAW. javax.print AUTOSENSE + IPP Class Driver
     * often submits Size=0 jobs and marks the queue Offline.
     */
    private void sendViaWinSpoolRaw(String printerName, byte[] data) throws IOException, InterruptedException {
        if (rawPrintScript == null || !Files.isRegularFile(rawPrintScript)) {
            throw new IllegalStateException(
                    "找不到 scripts/raw-print.ps1，无法进行 Win32 RAW 打印。工程根目录应包含该脚本。");
        }
        Path temp = Files.createTempFile("shipping-order-", ".escp.bin");
        try {
            Files.write(temp, data);
            List<String> cmd = List.of(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-File", rawPrintScript.toAbsolutePath().toString(),
                    "-PrinterName", printerName,
                    "-FilePath", temp.toAbsolutePath().toString()
            );
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("RAW 打印超时");
            }
            int code = process.exitValue();
            if (code != 0) {
                throw new IllegalStateException("RAW 打印脚本退出码 " + code + (output.isBlank() ? "" : ("\n" + output)));
            }
            log.info("raw-print.ps1 输出: {}", output);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private PrintService findByName(String name) {
        return Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
                .filter(s -> s.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    private static Path resolveRawPrintScript() {
        Path cwd = Path.of("").toAbsolutePath();
        Path[] candidates = new Path[] {
                cwd.resolve("scripts").resolve("raw-print.ps1"),
                cwd.resolve("..").resolve("scripts").resolve("raw-print.ps1"),
                cwd.getParent() != null ? cwd.getParent().resolve("scripts").resolve("raw-print.ps1") : null
        };
        for (Path p : candidates) {
            if (p != null && Files.isRegularFile(p)) {
                return p.normalize();
            }
        }
        return null;
    }
}
