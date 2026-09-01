package com.xinglong.print.web;

import com.xinglong.print.print.RawPrintService;
import com.xinglong.print.print.ShippingOrderEscpBuilder;
import com.xinglong.print.web.dto.ShippingOrderLayout;
import com.xinglong.print.web.dto.ShippingOrderPrintRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class PrintController {

    private static final Logger log = LoggerFactory.getLogger(PrintController.class);

    private final ShippingOrderEscpBuilder builder;
    private final RawPrintService rawPrintService;

    public PrintController(ShippingOrderEscpBuilder builder, RawPrintService rawPrintService) {
        this.builder = builder;
        this.rawPrintService = rawPrintService;
    }

    @GetMapping("/printers")
    public Map<String, Object> printers() {
        List<String> names = rawPrintService.listPrinters();
        log.info("查询本机打印机: {}", names);
        Map<String, Object> body = new HashMap<>();
        body.put("printers", names);
        return body;
    }

    @PostMapping("/print/shipping-order")
    public Map<String, Object> print(@Valid @RequestBody ShippingOrderPrintRequest request,
                                     @RequestParam(required = false) String printerName) {
        log.info("收到出货单打印请求: orderNo={}, lines={}, printerName={}",
                request.getOrderNo(),
                request.getLines() == null ? 0 : request.getLines().size(),
                printerName);
        byte[] data = builder.build(request);
        if (printerName != null && !printerName.isBlank()) {
            rawPrintService.printRaw(data, printerName);
        } else {
            rawPrintService.printRaw(data);
        }
        Map<String, Object> body = new HashMap<>();
        body.put("ok", true);
        body.put("bytes", data.length);
        body.put("message", "已提交打印任务");
        return body;
    }

    @PostMapping("/print/shipping-order/preview")
    public Map<String, Object> previewText(@Valid @RequestBody ShippingOrderPrintRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("text", builder.buildPlainPreview(request));
        return body;
    }

    /** 打印版式：前端按此 1:1 复现纸面结果。 */
    @PostMapping("/print/shipping-order/layout")
    public ShippingOrderLayout previewLayout(@Valid @RequestBody ShippingOrderPrintRequest request) {
        return builder.buildLayout(request);
    }

    @PostMapping(value = "/print/shipping-order/preview-bytes",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> previewBytesDownload(@Valid @RequestBody ShippingOrderPrintRequest request) {
        byte[] data = builder.build(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"shipping-order.escp.bin\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }

    @PostMapping("/print/shipping-order/preview-bytes/json")
    public Map<String, Object> previewBytesJson(@Valid @RequestBody ShippingOrderPrintRequest request) {
        byte[] data = builder.build(request);
        Map<String, Object> body = new HashMap<>();
        body.put("bytes", data.length);
        body.put("base64", Base64.getEncoder().encodeToString(data));
        body.put("hex", toHex(data));
        body.put("textPreview", builder.buildPlainPreview(request));
        return body;
    }

    @ExceptionHandler({IllegalStateException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> handlePrintError(RuntimeException ex) {
        log.warn("打印失败: {}", ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("ok", false);
        body.put("message", ex.getMessage());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("参数校验失败");
        Map<String, Object> body = new HashMap<>();
        body.put("ok", false);
        body.put("message", msg);
        return ResponseEntity.badRequest().body(body);
    }

    private static String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
