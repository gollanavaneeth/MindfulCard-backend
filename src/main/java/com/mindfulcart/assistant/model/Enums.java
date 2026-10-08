package com.mindfulcart.assistant.model;

public final class Enums {
  private Enums() {}

  public enum Category {
    ELECTRONICS,
    CLOTHING,
    FOOD,
    BEAUTY,
    ENTERTAINMENT,
    HOME,
    TRAVEL,
    GAMING,
    ACCESSORIES,
    OTHER
  }

  public enum EmotionalState {
    NORMAL,
    HAPPY,
    EXCITED,
    BORED,
    STRESSED,
    SAD,
    FRUSTRATED
  }

  public enum RiskLevel {
    LOW,
    MODERATE,
    HIGH,
    VERY_HIGH
  }

  public enum WishlistStatus {
    WAITING,
    PURCHASED,
    CANCELLED,
    AVOIDED
  }

  public enum PurchaseDecision {
    PENDING,
    WAITING,
    PURCHASED,
    AVOIDED
  }
}
