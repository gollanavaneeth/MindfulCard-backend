package com.mindfulcart.assistant.service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.util.LoadLibs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class OcrService {
  private static final long MAX_PIXELS = 28_000_000L;
  private static final int MAX_OUTPUT_CHARS = 60_000;
  private final Semaphore permits = new Semaphore(2);

  @Value("${app.ocr.language:eng}")
  private String language;

  @Value("${app.ocr.page-segmentation-mode:6}")
  private int pageSegmentationMode;

  private volatile String bundledDataPath;

  public OcrResult extract(MultipartFile upload) throws IOException {
    long started = System.nanoTime();
    boolean acquired = false;
    try {
      permits.acquire();
      acquired = true;
      String filename =
          upload.getOriginalFilename() == null ? "upload" : upload.getOriginalFilename();
      String type =
          upload.getContentType() == null ? "" : upload.getContentType().toLowerCase(Locale.ROOT);
      String text =
          type.equals("application/pdf") || filename.toLowerCase(Locale.ROOT).endsWith(".pdf")
              ? readPdf(upload)
              : readImage(upload);
      text = normalize(text);
      if (text.isBlank()) {
        throw new IllegalArgumentException(
            "No readable text was found. Try a sharper image with better lighting and larger text.");
      }
      long duration = (System.nanoTime() - started) / 1_000_000L;
      return new OcrResult(text, "Tesseract 5", language, text.length(), duration);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("OCR was interrupted. Please try again.", interrupted);
    } catch (UnsatisfiedLinkError failure) {
      throw new IllegalStateException(
          "OCR could not start. On Windows, install the Microsoft Visual C++ 2015-2022 Redistributable and restart the backend.",
          failure);
    } catch (TesseractException failure) {
      throw new IllegalArgumentException(
          "OCR could not read this file. Try a clearer PNG, JPG, TIFF, or text-based PDF.",
          failure);
    } catch (Error nativeFailure) {
      if (nativeFailure instanceof VirtualMachineError || nativeFailure instanceof ThreadDeath)
        throw nativeFailure;
      throw new IllegalStateException(
          "The native OCR engine could not safely process this file. Try converting it to PNG and upload it again.",
          nativeFailure);
    } finally {
      if (acquired) permits.release();
    }
  }

  private String readImage(MultipartFile upload) throws IOException, TesseractException {
    BufferedImage source;
    try (var input = upload.getInputStream()) {
      source = ImageIO.read(input);
    }
    if (source == null)
      throw new IllegalArgumentException("The uploaded image format could not be decoded");
    if ((long) source.getWidth() * source.getHeight() > MAX_PIXELS) {
      throw new IllegalArgumentException(
          "The image resolution is too large. Use an image below 28 megapixels.");
    }
    return engine().doOCR(preprocess(source));
  }

  private String readPdf(MultipartFile upload) throws IOException, TesseractException {
    Path temporary = Files.createTempFile("mindfulcart-ocr-", ".pdf");
    try {
      upload.transferTo(temporary);
      return engine().doOCR(temporary.toFile());
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private Tesseract engine() {
    Tesseract engine = new Tesseract();
    engine.setDatapath(dataPath());
    engine.setLanguage(language);
    engine.setPageSegMode(pageSegmentationMode);
    engine.setVariable("user_defined_dpi", "300");
    engine.setVariable("preserve_interword_spaces", "1");
    return engine;
  }

  private String dataPath() {
    String cached = bundledDataPath;
    if (cached != null) return cached;
    synchronized (this) {
      if (bundledDataPath == null) {
        File folder = LoadLibs.extractTessResources("tessdata");
        bundledDataPath = folder.getAbsolutePath();
      }
      return bundledDataPath;
    }
  }

  private BufferedImage preprocess(BufferedImage source) {
    double scale =
        source.getWidth() < 1200 ? Math.min(2.0, 2400.0 / Math.max(1, source.getWidth())) : 1.0;
    int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
    int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
    BufferedImage gray = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
    Graphics2D graphics = gray.createGraphics();
    try {
      graphics.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      graphics.drawImage(source, 0, 0, width, height, null);
    } finally {
      graphics.dispose();
    }
    return new RescaleOp(1.25f, -12f, null).filter(gray, null);
  }

  private String normalize(String value) {
    if (value == null) return "";
    String cleaned =
        value
            .replace('\u0000', ' ')
            .replaceAll("[\\t ]+", " ")
            .replaceAll("(?m)^[ ]+|[ ]+$", "")
            .replaceAll("\\R{3,}", "\n\n")
            .trim();
    return cleaned.length() > MAX_OUTPUT_CHARS ? cleaned.substring(0, MAX_OUTPUT_CHARS) : cleaned;
  }

  public record OcrResult(
      String text, String engine, String language, int characterCount, long durationMs) {}
}
