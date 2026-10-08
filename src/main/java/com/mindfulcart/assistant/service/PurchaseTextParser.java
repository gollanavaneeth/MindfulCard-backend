package com.mindfulcart.assistant.service;

import com.mindfulcart.assistant.model.Enums;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * A deterministic, private parser for quick purchase capture. It intentionally does not call an
 * external AI service; the returned draft can be reviewed by the user before it is submitted as an
 * evaluation.
 */
@Service
public class PurchaseTextParser {
  private static final Pattern CURRENCY_PRICE =
      Pattern.compile("(?i)(?:₹|rs\\.?|inr|\\$|usd)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)");
  private static final Pattern CONTEXT_PRICE =
      Pattern.compile(
          "(?i)\\b(?:costs?|priced(?:\\s+at)?|price(?:\\s+is|\\s+of)?|for)\\s*(?:about\\s*)?(?:₹|rs\\.?|inr|\\$|usd)?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)");
  private static final Pattern DISCOUNT =
      Pattern.compile("(?i)\\b([1-9][0-9]?)\\s*%\\s*(?:off|discount)?");
  private static final Pattern PRODUCT =
      Pattern.compile(
          "(?i)(?:i\\s+(?:found|saw|want|need)|want\\s+to\\s+buy|thinking\\s+about|considering|buying|purchase(?:ing)?)\\s+(?:a|an|the|some)?\\s*(.+?)(?=\\s+(?:for|costs?|priced|at\\s+₹|on\\s+(?:instagram|facebook|amazon|flipkart)|from\\s+|because\\s+)|[,.!?]|$)");

  public PurchaseDraft parse(String rawText) {
    String text = rawText == null ? "" : rawText.trim().replaceAll("\\s+", " ");
    if (text.isBlank()) {
      throw new IllegalArgumentException("Describe the purchase you are considering");
    }
    if (text.length() > 4_000) {
      throw new IllegalArgumentException("Purchase description must be 4000 characters or fewer");
    }

    String lower = text.toLowerCase(Locale.ROOT);
    BigDecimal price = findDecimal(CURRENCY_PRICE, text);
    if (price == null) {
      price = findDecimal(CONTEXT_PRICE, text);
    }
    Integer discount = findInteger(DISCOUNT, text);
    Enums.Category category = category(lower);
    Enums.EmotionalState emotion = emotion(lower);
    String source = source(lower);
    String productName = product(text);

    Set<String> detected = new LinkedHashSet<>();
    List<String> warnings = new ArrayList<>();
    if (productName != null) detected.add("productName");
    else warnings.add("Add a clear product name.");
    if (price != null) detected.add("price");
    else warnings.add("Add a price, for example ₹8,000.");
    if (discount != null) detected.add("discountPercentage");
    if (category != Enums.Category.OTHER) detected.add("category");
    if (source != null) detected.add("sourceOfInterest");
    if (emotion != Enums.EmotionalState.NORMAL) detected.add("emotionalState");

    boolean discountTriggered =
        discount != null || containsAny(lower, "sale", "deal", "coupon", "offer");
    boolean offerUrgency =
        containsAny(
            lower,
            "only today",
            "last chance",
            "ending soon",
            "limited time",
            "few left",
            "flash sale");
    boolean advertisementTriggered =
        source != null
            && containsAny(
                lower,
                "instagram",
                "facebook",
                "youtube",
                "tiktok",
                "ad",
                "advertisement",
                "influencer");
    boolean previouslyPlanned =
        containsAny(lower, "planned", "saved for", "shopping list", "need to replace");
    boolean genuinelyNeeded =
        containsAny(lower, "need", "essential", "replacement", "required for", "broken");
    boolean canWait =
        !containsAny(lower, "urgent", "right now", "immediately", "today only", "last chance");

    int confidencePoints = detected.size();
    double confidence = Math.min(0.97, 0.25 + confidencePoints * 0.11);
    return new PurchaseDraft(
        text,
        productName,
        price,
        discount,
        discount != null,
        category,
        source,
        emotion,
        advertisementTriggered,
        discountTriggered,
        offerUrgency,
        previouslyPlanned,
        genuinelyNeeded,
        canWait,
        Math.round(confidence * 100.0) / 100.0,
        List.copyOf(detected),
        List.copyOf(warnings));
  }

  private BigDecimal findDecimal(Pattern pattern, String text) {
    Matcher matcher = pattern.matcher(text);
    while (matcher.find()) {
      try {
        BigDecimal value = new BigDecimal(matcher.group(1).replace(",", ""));
        if (value.signum() >= 0) return value;
      } catch (NumberFormatException ignored) {
        // Try the next candidate.
      }
    }
    return null;
  }

  private Integer findInteger(Pattern pattern, String text) {
    Matcher matcher = pattern.matcher(text);
    return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
  }

  private String product(String text) {
    Matcher matcher = PRODUCT.matcher(text);
    if (matcher.find()) {
      String value = cleanProduct(matcher.group(1));
      if (!value.isBlank()) return value;
    }
    String withoutPrice = CURRENCY_PRICE.matcher(text).replaceAll("");
    withoutPrice = DISCOUNT.matcher(withoutPrice).replaceAll("");
    withoutPrice =
        withoutPrice
            .replaceFirst("(?i)^(hello[, ]*|hi[, ]*)", "")
            .replaceFirst(
                "(?i)^(i\\s+(?:found|saw|want|need)|want\\s+to\\s+buy|thinking\\s+about|considering|buying)\\s+",
                "");
    String candidate = withoutPrice.split("(?i)\\s+(?:on|from|because|for)\\s+|[,.!?]", 2)[0];
    candidate = cleanProduct(candidate);
    return candidate.isBlank() || candidate.length() > 100 ? null : candidate;
  }

  private String cleanProduct(String value) {
    String cleaned =
        value == null
            ? ""
            : value
                .trim()
                .replaceFirst("(?i)^(a|an|the|some)\\s+", "")
                .replaceAll("(?i)\\s+(?:at|for)\\s*$", "")
                .trim();
    if (cleaned.length() > 120) cleaned = cleaned.substring(0, 120).trim();
    if (cleaned.isEmpty()) return cleaned;
    return Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
  }

  private Enums.Category category(String text) {
    if (containsAny(
        text,
        "phone",
        "laptop",
        "headphone",
        "earbud",
        "tablet",
        "camera",
        "monitor",
        "keyboard",
        "electronic")) return Enums.Category.ELECTRONICS;
    if (containsAny(
        text, "shirt", "dress", "jeans", "jacket", "clothes", "clothing", "saree", "kurta"))
      return Enums.Category.CLOTHING;
    if (containsAny(text, "meal", "food", "snack", "coffee", "restaurant", "delivery"))
      return Enums.Category.FOOD;
    if (containsAny(text, "makeup", "skin care", "skincare", "cosmetic", "perfume", "beauty"))
      return Enums.Category.BEAUTY;
    if (containsAny(text, "movie", "concert", "subscription", "streaming", "entertainment"))
      return Enums.Category.ENTERTAINMENT;
    if (containsAny(text, "furniture", "decor", "kitchen", "mattress", "lamp", "home"))
      return Enums.Category.HOME;
    if (containsAny(text, "flight", "hotel", "trip", "holiday", "travel", "ticket"))
      return Enums.Category.TRAVEL;
    if (containsAny(text, "game", "gaming", "console", "playstation", "xbox"))
      return Enums.Category.GAMING;
    if (containsAny(
        text, "watch", "bag", "wallet", "belt", "sunglasses", "jewellery", "jewelry", "accessory"))
      return Enums.Category.ACCESSORIES;
    return Enums.Category.OTHER;
  }

  private Enums.EmotionalState emotion(String text) {
    if (containsAny(text, "stressed", "stress", "anxious", "overwhelmed"))
      return Enums.EmotionalState.STRESSED;
    if (containsAny(text, "frustrated", "angry", "annoyed")) return Enums.EmotionalState.FRUSTRATED;
    if (containsAny(text, "bored", "boredom")) return Enums.EmotionalState.BORED;
    if (containsAny(text, "sad", "upset", "low mood")) return Enums.EmotionalState.SAD;
    if (containsAny(text, "excited", "thrilled", "can't wait", "cant wait"))
      return Enums.EmotionalState.EXCITED;
    if (containsAny(text, "happy", "celebrating", "reward myself"))
      return Enums.EmotionalState.HAPPY;
    return Enums.EmotionalState.NORMAL;
  }

  private String source(String text) {
    if (text.contains("instagram")) return "Instagram";
    if (text.contains("facebook")) return "Facebook";
    if (text.contains("youtube")) return "YouTube";
    if (text.contains("tiktok")) return "TikTok";
    if (text.contains("amazon")) return "Amazon";
    if (text.contains("flipkart")) return "Flipkart";
    if (text.contains("myntra")) return "Myntra";
    if (text.contains("friend")) return "Friend recommendation";
    if (containsAny(text, "shop", "store", "mall")) return "Physical store";
    return null;
  }

  private boolean containsAny(String text, String... candidates) {
    for (String candidate : candidates) {
      if (text.contains(candidate)) return true;
    }
    return false;
  }

  public record PurchaseDraft(
      String originalText,
      String productName,
      BigDecimal price,
      Integer discountPercentage,
      boolean discountAvailable,
      Enums.Category category,
      String sourceOfInterest,
      Enums.EmotionalState emotionalState,
      boolean advertisementTriggered,
      boolean discountTriggered,
      boolean offerUrgency,
      boolean previouslyPlanned,
      boolean genuinelyNeeded,
      boolean canWait,
      double confidence,
      List<String> detectedFields,
      List<String> warnings) {}
}
