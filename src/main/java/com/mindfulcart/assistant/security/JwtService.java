package com.mindfulcart.assistant.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  @Value("${app.jwt.secret}")
  private String secret;

  @Value("${app.jwt.expiration-ms}")
  private long expiration;

  private SecretKey key() {
    return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  public String generate(UserDetails user) {
    Date issuedAt = new Date();
    return Jwts.builder()
        .subject(user.getUsername())
        .issuedAt(issuedAt)
        .expiration(new Date(issuedAt.getTime() + expiration))
        .signWith(key())
        .compact();
  }

  public String username(String token) {
    return Jwts.parser()
        .verifyWith(key())
        .build()
        .parseSignedClaims(token)
        .getPayload()
        .getSubject();
  }

  public boolean valid(String token, UserDetails user) {
    try {
      return username(token).equalsIgnoreCase(user.getUsername())
          && Jwts.parser()
              .verifyWith(key())
              .build()
              .parseSignedClaims(token)
              .getPayload()
              .getExpiration()
              .after(new Date());
    } catch (RuntimeException e) {
      return false;
    }
  }
}
