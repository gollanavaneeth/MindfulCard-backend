package com.mindfulcart.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindfulcart.assistant.model.Evaluation;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiAdvisorService {
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

  @Value("${app.ai.api-key:}")
  private String apiKey;

  @Value("${app.ai.model:gpt-4.1-mini}")
  private String model;

  public AiAdvisorService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public record Answer(String answer, String source) {}

  public Answer answer(String question, List<Evaluation> history) {
    String context = retrieveContext(history);

    if (apiKey != null && !apiKey.isBlank()) {
      String generatedAnswer = generateWithAi(question, context);
      if (generatedAnswer != null) {
        return new Answer(generatedAnswer, "OPENAI");
      }
    }

    return new Answer(createLocalAnswer(question, history), "PRIVATE_COACH");
  }

  // RAG retrieval: select and format the latest user-specific purchase facts.
  private String retrieveContext(List<Evaluation> history) {
    return history.stream()
        .limit(8)
        .map(
            evaluation ->
                evaluation.getProductName()
                    + ": score "
                    + evaluation.getImpulseScore()
                    + ", risk "
                    + evaluation.getRiskLevel()
                    + ", decision "
                    + (evaluation.getDecision() == null ? "PENDING" : evaluation.getDecision()))
        .collect(Collectors.joining("\n"));
  }

  // RAG augmentation and generation: add retrieved context to the AI prompt.
  private String generateWithAi(String question, String context) {
    try {
      var requestBody = objectMapper.createObjectNode();
      requestBody.put("model", model);
      requestBody.put(
          "instructions",
          "You are a supportive anti-impulse shopping coach. Give concise, practical, "
              + "non-judgmental guidance. Do not give financial guarantees. Use the supplied "
              + "purchase history only. Never encourage spending. Reply in at most 120 words.");
      requestBody.put("input", "User question: " + question + "\nRecent history:\n" + context);
      requestBody.put("max_output_tokens", 220);

      HttpRequest request =
          HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses"))
              .timeout(Duration.ofSeconds(20))
              .header("Authorization", "Bearer " + apiKey)
              .header("Content-Type", "application/json")
              .POST(
                  HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() / 100 != 2) {
        return null;
      }

      JsonNode responseBody = objectMapper.readTree(response.body());
      for (JsonNode output : responseBody.path("output")) {
        for (JsonNode content : output.path("content")) {
          if (content.hasNonNull("text")) {
            return content.get("text").asText();
          }
        }
      }
    } catch (Exception ignored) {
      // Keep the coach available with a deterministic local response.
    }
    return null;
  }

  private String createLocalAnswer(String question, List<Evaluation> history) {
    if (history.isEmpty()) {
      return "Start with one purchase evaluation. Describe what you want, whether it was "
          + "planned, and how you feel. A small pause creates enough distance to make a "
          + "clearer choice.";
    }

    double averageScore =
        history.stream().mapToInt(Evaluation::getImpulseScore).average().orElse(0);
    long advertisementTriggers =
        history.stream().filter(Evaluation::isAdvertisementTriggered).count();
    long urgencyTriggers = history.stream().filter(Evaluation::isOfferUrgency).count();
    String mainTrigger =
        advertisementTriggers >= urgencyTriggers && advertisementTriggers > 0
            ? "advertising"
            : urgencyTriggers > 0 ? "limited-time urgency" : "unplanned browsing";

    if (question.toLowerCase().matches(".*(buy|purchase|worth|should).*")) {
      return "Before deciding, try the replacement test: if this item disappeared today, what "
          + "existing item or cheaper option would solve the same need? Your recent average "
          + "impulse score is "
          + Math.round(averageScore)
          + ", and your strongest visible trigger is "
          + mainTrigger
          + ". Give this decision at least 24 hours, then reassess the need—not the deal.";
    }

    return "Your recent reflections suggest that "
        + mainTrigger
        + " deserves the most attention. Turn off promotional notifications, keep uncertain "
        + "items in the cooling-off list, and revisit them only after the timer ends. Ask me "
        + "about a specific product for a more focused reflection.";
  }
}
