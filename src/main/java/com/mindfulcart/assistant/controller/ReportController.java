package com.mindfulcart.assistant.controller;

import com.mindfulcart.assistant.model.Enums;
import com.mindfulcart.assistant.model.Evaluation;
import com.mindfulcart.assistant.model.User;
import com.mindfulcart.assistant.service.CurrentUserService;
import com.mindfulcart.assistant.service.MonthlyInsightsService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {
  private final CurrentUserService currentUser;
  private final MonthlyInsightsService insights;

  @GetMapping(value = "/evaluations.csv", produces = "text/csv")
  public ResponseEntity<byte[]> csv(
      @RequestParam(value = "month", required = false) String monthValue) {
    User user = currentUser.get();
    YearMonth month = insights.parseMonth(monthValue);
    List<Evaluation> items = insights.evaluationsFor(user.getId(), month);
    StringBuilder csv = new StringBuilder("\uFEFF");
    csv.append(
        "Date,Product,Category,Price,Impulse Score,Risk,Decision,Emotion,Source,Discount %,Ad Trigger,Discount Trigger,Urgency,Recommendation\r\n");
    for (Evaluation item : items) {
      appendRow(
          csv,
          item.getCreatedAt() == null
              ? ""
              : item.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
          item.getProductName(),
          enumName(item.getCategory()),
          decimal(item.getPrice()),
          String.valueOf(item.getImpulseScore()),
          enumName(item.getRiskLevel()),
          decision(item).name(),
          enumName(item.getEmotionalState()),
          item.getSourceOfInterest(),
          item.getDiscountPercentage() == null ? "" : item.getDiscountPercentage().toString(),
          yesNo(item.isAdvertisementTriggered()),
          yesNo(item.isDiscountTriggered()),
          yesNo(item.isOfferUrgency()),
          item.getRecommendation());
    }
    byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename("mindful-shopping-" + month + ".csv", StandardCharsets.UTF_8)
                .build()
                .toString())
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .contentLength(body.length)
        .body(body);
  }

  @GetMapping(value = "/monthly.html", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<byte[]> printable(
      @RequestParam(value = "month", required = false) String monthValue) {
    User user = currentUser.get();
    YearMonth month = insights.parseMonth(monthValue);
    List<Evaluation> items = insights.evaluationsFor(user.getId(), month);
    MonthlyInsightsService.MonthlySummary summary = insights.summarize(user.getId(), month);
    String html = html(user, summary, items);
    byte[] body = html.getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline()
                .filename("mindful-shopping-" + month + ".html", StandardCharsets.UTF_8)
                .build()
                .toString())
        .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
        .contentLength(body.length)
        .body(body);
  }

  private String html(
      User user, MonthlyInsightsService.MonthlySummary summary, List<Evaluation> items) {
    StringBuilder rows = new StringBuilder();
    for (Evaluation item : items) {
      rows.append("<tr><td>")
          .append(
              escape(
                  item.getCreatedAt() == null ? "" : item.getCreatedAt().toLocalDate().toString()))
          .append("</td><td><strong>")
          .append(escape(item.getProductName()))
          .append("</strong><br><small>")
          .append(escape(enumName(item.getCategory())))
          .append("</small></td><td>₹")
          .append(escape(decimal(item.getPrice())))
          .append("</td><td>")
          .append(item.getImpulseScore())
          .append("</td><td>")
          .append(escape(enumName(item.getRiskLevel())))
          .append("</td><td>")
          .append(escape(decision(item).name()))
          .append("</td></tr>");
    }
    if (rows.isEmpty())
      rows.append(
          "<tr><td colspan=\"6\" class=\"empty\">No evaluations recorded for this month.</td></tr>");

    StringBuilder insightItems = new StringBuilder();
    summary
        .insights()
        .forEach(value -> insightItems.append("<li>").append(escape(value)).append("</li>"));
    String title = "Mindful shopping report · " + summary.month();
    return "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
        + "<title>"
        + escape(title)
        + "</title><style>"
        + "*{box-sizing:border-box}body{margin:0;background:#f5f7fb;color:#172033;font:15px/1.5 Inter,Segoe UI,sans-serif}.page{max-width:980px;margin:30px auto;background:white;padding:42px;border-radius:24px;box-shadow:0 16px 50px #17335a18}.top{display:flex;justify-content:space-between;gap:20px;border-bottom:2px solid #edf0f6;padding-bottom:24px}.brand{color:#6757df;font-weight:800;letter-spacing:.08em;text-transform:uppercase;font-size:12px}h1{font-size:32px;margin:8px 0 3px}.muted,small{color:#657087}.stats{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin:28px 0}.stat{background:#f7f7fc;border:1px solid #ebeaf5;border-radius:16px;padding:16px}.stat b{display:block;font-size:24px;color:#342b70}.stat span{font-size:12px;color:#657087}.callout{padding:18px 20px;border-radius:16px;background:linear-gradient(120deg,#eeeafe,#f3f9ff);margin:22px 0}.grid{display:grid;grid-template-columns:1fr 1fr;gap:18px}.panel{border:1px solid #e8eaf0;border-radius:16px;padding:18px}h2{font-size:17px;margin:0 0 12px}ul{margin:0;padding-left:20px}li+li{margin-top:8px}table{border-collapse:collapse;width:100%;margin-top:12px}th,td{text-align:left;padding:11px;border-bottom:1px solid #edf0f4}th{font-size:11px;text-transform:uppercase;color:#69758b}.empty{text-align:center;color:#7a8496;padding:28px}.print{border:0;border-radius:10px;background:#6757df;color:white;padding:10px 16px;font-weight:700;cursor:pointer}@media(max-width:700px){.page{margin:0;padding:22px;border-radius:0}.stats,.grid{grid-template-columns:1fr 1fr}.top{display:block}.print{margin-top:14px}}@media print{body{background:white}.page{box-shadow:none;margin:0;max-width:none;padding:20px}.print{display:none}}"
        + "</style></head><body><main class=\"page\"><header class=\"top\"><div><div class=\"brand\">Anti-Impulse Shopping Assistant</div><h1>"
        + escape(title)
        + "</h1><div class=\"muted\">Prepared for "
        + escape(user.getFullName())
        + "</div></div>"
        + "<button class=\"print\" onclick=\"window.print()\">Print / Save PDF</button></header>"
        + "<section class=\"stats\"><div class=\"stat\"><b>"
        + summary.evaluated()
        + "</b><span>evaluated</span></div>"
        + "<div class=\"stat\"><b>"
        + summary.avoided()
        + "</b><span>avoided</span></div>"
        + "<div class=\"stat\"><b>₹"
        + escape(decimal(summary.avoidedAmount()))
        + "</b><span>protected</span></div>"
        + "<div class=\"stat\"><b>"
        + summary.averageImpulseScore()
        + "</b><span>average impulse score</span></div></section>"
        + "<section class=\"callout\"><strong>Monthly reflection</strong><br>"
        + escape(summary.narrative())
        + "</section>"
        + "<section class=\"grid\"><div class=\"panel\"><h2>Patterns</h2><p><b>Top category:</b> "
        + escape(summary.topCategory())
        + "</p><p><b>Top trigger:</b> "
        + escape(summary.topTrigger())
        + "</p><p><b>Top emotion:</b> "
        + escape(summary.topEmotion())
        + "</p></div><div class=\"panel\"><h2>Recommended focus</h2><ul>"
        + insightItems
        + "</ul></div></section>"
        + "<section style=\"margin-top:26px\"><h2>Decision journal</h2><table><thead><tr><th>Date</th><th>Purchase</th><th>Price</th><th>Score</th><th>Risk</th><th>Decision</th></tr></thead><tbody>"
        + rows
        + "</tbody></table></section></main></body></html>";
  }

  private void appendRow(StringBuilder output, String... values) {
    for (int i = 0; i < values.length; i++) {
      if (i > 0) output.append(',');
      output.append(csvCell(values[i]));
    }
    output.append("\r\n");
  }

  private String csvCell(String value) {
    String safe = value == null ? "" : value;
    if (!safe.isBlank() && "=+-@".indexOf(safe.charAt(0)) >= 0) safe = "'" + safe;
    return "\"" + safe.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\"";
  }

  private String escape(String value) {
    if (value == null) return "";
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  private String decimal(BigDecimal value) {
    return value == null ? "0" : value.stripTrailingZeros().toPlainString();
  }

  private String enumName(Enum<?> value) {
    return value == null ? "" : value.name();
  }

  private Enums.PurchaseDecision decision(Evaluation item) {
    return item.getDecision() == null ? Enums.PurchaseDecision.PENDING : item.getDecision();
  }

  private String yesNo(boolean value) {
    return value ? "Yes" : "No";
  }
}
