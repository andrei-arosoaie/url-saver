package com.know.identity.infrastructure;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IdentityCallback {
  @GetMapping("/auth/callback")
  public String callback() {
    return "forward:/index.html";
  }
}
