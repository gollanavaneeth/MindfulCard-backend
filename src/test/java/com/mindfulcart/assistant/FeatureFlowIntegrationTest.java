package com.mindfulcart.assistant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FeatureFlowIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  String token;

  @BeforeAll
  void registerRealAccount() throws Exception {
    String email = "integration-" + UUID.randomUUID() + "@example.com";
    String body =
        "{\"fullName\":\"Integration User\",\"email\":\""
            + email
            + "\",\"password\":\"Strong@123\"}";
    String output =
        mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    token = json.readTree(output).get("token").asText();
  }

  String auth() {
    return "Bearer " + token;
  }

  @Test
  void budgetGoalDecisionAndReassessmentWorkTogether() throws Exception {
    mvc.perform(
            put("/api/budget")
                .header("Authorization", auth())
                .contentType("application/json")
                .content(
                    "{\"monthlyIncome\":80000,\"essentialExpenses\":40000,\"savingsTarget\":15000}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.discretionaryBudget").value(25000));

    String evaluation =
        mvc.perform(
                post("/api/evaluations")
                    .header("Authorization", auth())
                    .contentType("application/json")
                    .content(
                        "{\"productName\":\"Test Camera\",\"category\":\"ELECTRONICS\",\"price\":12000,\"description\":\"test\",\"sourceOfInterest\":\"ADVERTISEMENT\",\"discountAvailable\":true,\"discountPercentage\":10,\"genuinelyNeeded\":false,\"previouslyPlanned\":false,\"considerationPeriod\":\"Today\",\"ownsSimilar\":true,\"withinBudget\":true,\"advertisementTriggered\":true,\"discountTriggered\":true,\"offerUrgency\":true,\"emotionalState\":\"EXCITED\",\"canWait\":false,\"frequentlyUsed\":false,\"cheaperAlternative\":true}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = json.readTree(evaluation).get("id").asLong();

    mvc.perform(
            put("/api/evaluations/" + id + "/decision")
                .header("Authorization", auth())
                .contentType("application/json")
                .content("{\"decision\":\"AVOIDED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.decision").value("AVOIDED"));
    mvc.perform(
            post("/api/reassessments")
                .header("Authorization", auth())
                .contentType("application/json")
                .content(
                    "{\"evaluationId\":"
                        + id
                        + ",\"stillWantIt\":false,\"needChanged\":true,\"priceChanged\":false,\"foundAlternative\":true,\"fitsBudgetNow\":true,\"reflection\":\"The urge passed\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.change").isNumber());
    mvc.perform(get("/api/budget").header("Authorization", auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pendingExposure").isNumber());
  }

  @Test
  void adminConsoleAndDirectTextExtractionWork() throws Exception {
    mvc.perform(get("/api/admin/metrics").header("Authorization", auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalUsers").isNumber());
    mvc.perform(get("/api/admin/users").header("Authorization", auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].role").value("ADMIN"));

    MockMultipartFile text =
        new MockMultipartFile(
            "file",
            "receipt.txt",
            "text/plain",
            "Wireless Headphones Rs 7999 20% off Instagram".getBytes(StandardCharsets.UTF_8));
    mvc.perform(multipart("/api/tools/extract-metadata").file(text).header("Authorization", auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.extractionStatus").value("TEXT_EXTRACTED"))
        .andExpect(jsonPath("$.draft.productName").isNotEmpty())
        .andExpect(jsonPath("$.ocrText").value(org.hamcrest.Matchers.containsString("Headphones")));
  }

  @Test
  void imageUploadRunsNativeOcr() throws Exception {
    BufferedImage image = new BufferedImage(1500, 420, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    graphics.setColor(Color.WHITE);
    graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
    graphics.setColor(Color.BLACK);
    graphics.setFont(new Font("SansSerif", Font.BOLD, 68));
    graphics.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    graphics.drawString("Wireless Headphones", 80, 150);
    graphics.drawString("Price Rs 7999", 80, 265);
    graphics.drawString("SALE 20% OFF", 80, 365);
    graphics.dispose();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ImageIO.write(image, "png", bytes);

    MockMultipartFile receipt =
        new MockMultipartFile("file", "receipt.png", "image/png", bytes.toByteArray());
    mvc.perform(
            multipart("/api/tools/extract-metadata").file(receipt).header("Authorization", auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.extractionStatus").value("OCR_COMPLETE"))
        .andExpect(jsonPath("$.engine").value("Tesseract 5"))
        .andExpect(jsonPath("$.ocrText").value(org.hamcrest.Matchers.containsString("Headphones")));
  }
}
