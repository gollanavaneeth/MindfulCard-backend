package com.mindfulcart.assistant.controller;

import com.mindfulcart.assistant.service.OcrService;
import com.mindfulcart.assistant.service.PurchaseTextParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
public class PurchaseToolsController {
  private static final long MAX_UPLOAD_BYTES = 10L * 1024L * 1024L;
  private static final int MAX_TEXT_BYTES = 256 * 1024;

  private final PurchaseTextParser parser;
  private final OcrService ocr;

  @PostMapping("/parse-purchase")
  public PurchaseTextParser.PurchaseDraft parse(@RequestBody ParseRequest request) {
    return parser.parse(request == null ? null : request.text());
  }

  @PostMapping(value = "/extract-metadata", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public UploadAnalysis extract(
      @RequestPart("file") MultipartFile file,
      @RequestParam(value = "extractedText", required = false) String contextualText)
      throws IOException {
    validate(file);
    String name = safeFilename(file.getOriginalFilename());
    String contentType =
        file.getContentType() == null ? "application/octet-stream" : file.getContentType();

    String recognizedText;
    String extractionStatus;
    String engine;
    String language;
    long durationMs;

    if (isPlainText(contentType, name)) {
      recognizedText = readText(file);
      extractionStatus = "TEXT_EXTRACTED";
      engine = "Direct text reader";
      language = "n/a";
      durationMs = 0;
    } else {
      OcrService.OcrResult result = ocr.extract(file);
      recognizedText = result.text();
      extractionStatus = "OCR_COMPLETE";
      engine = result.engine();
      language = result.language();
      durationMs = result.durationMs();
    }

    String usableText =
        Stream.of(contextualText, recognizedText)
            .filter(value -> value != null && !value.isBlank())
            .map(String::trim)
            .distinct()
            .collect(java.util.stream.Collectors.joining("\n"));
    PurchaseTextParser.PurchaseDraft draft = parser.parse(usableText);

    return new UploadAnalysis(
        name,
        contentType,
        file.getSize(),
        sha256(file),
        extractionStatus,
        "Text was extracted locally. Confirm every suggested field before saving.",
        engine,
        language,
        durationMs,
        recognizedText,
        recognizedText.length(),
        draft);
  }

  private void validate(MultipartFile file) {
    if (file == null || file.isEmpty())
      throw new IllegalArgumentException("Choose a receipt or product screenshot");
    if (file.getSize() > MAX_UPLOAD_BYTES)
      throw new IllegalArgumentException("Upload must be 10 MB or smaller");
    String type =
        file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
    String name =
        file.getOriginalFilename() == null
            ? ""
            : file.getOriginalFilename().toLowerCase(Locale.ROOT);
    boolean image = type.startsWith("image/") || name.matches(".*\\.(png|jpe?g|bmp|gif|tiff?)$");
    boolean allowed =
        image
            || type.equals("application/pdf")
            || name.endsWith(".pdf")
            || type.startsWith("text/")
            || name.endsWith(".txt")
            || name.endsWith(".csv");
    if (!allowed)
      throw new IllegalArgumentException(
          "Only PNG, JPG, BMP, GIF, TIFF, PDF, and text files are supported");
  }

  private String readText(MultipartFile file) throws IOException {
    try (InputStream input = file.getInputStream()) {
      byte[] bytes = input.readNBytes(MAX_TEXT_BYTES);
      return new String(bytes, StandardCharsets.UTF_8).replace("\u0000", " ").trim();
    }
  }

  private String sha256(MultipartFile file) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (DigestInputStream input = new DigestInputStream(file.getInputStream(), digest)) {
        input.transferTo(java.io.OutputStream.nullOutputStream());
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }

  private boolean isPlainText(String contentType, String name) {
    String lower = name.toLowerCase(Locale.ROOT);
    return contentType.startsWith("text/") || lower.endsWith(".txt") || lower.endsWith(".csv");
  }

  private String safeFilename(String value) {
    if (value == null || value.isBlank()) return "upload";
    String normalized = value.replace('\\', '/');
    String base = normalized.substring(normalized.lastIndexOf('/') + 1).replaceAll("[\\r\\n]", "");
    return base.length() > 180 ? base.substring(base.length() - 180) : base;
  }

  public record ParseRequest(String text) {}

  public record UploadAnalysis(
      String filename,
      String contentType,
      long sizeBytes,
      String sha256,
      String extractionStatus,
      String message,
      String engine,
      String language,
      long durationMs,
      String ocrText,
      int ocrCharacterCount,
      PurchaseTextParser.PurchaseDraft draft) {}
}
