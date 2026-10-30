package com.know.categories.application;

import static org.assertj.core.api.Assertions.*;

import com.know.shared.application.RequestFailure;
import org.junit.jupiter.api.Test;

class CategoryRulesTest {
  @Test
  void validatesUnicodeLengthWithoutTruncationAndTrimsOnlySurroundingWhitespace() {
    var rules = new CategoryRules(2);
    assertThat(rules.name("  😀A\u2003")).isEqualTo("😀A");
    assertThatThrownBy(() -> rules.name("😀AB")).isInstanceOf(RequestFailure.class);
    assertThatThrownBy(() -> rules.name("\u2003 ")).isInstanceOf(RequestFailure.class);
    assertThatThrownBy(() -> rules.name(null)).isInstanceOf(RequestFailure.class);
  }

  @Test
  void acceptsOnlyExplicitSixDigitColors() {
    var rules = new CategoryRules(100);
    assertThatCode(() -> rules.color("#aB1234")).doesNotThrowAnyException();
    for (var invalid : new String[] {"red", "#fff", "112233", "#GG1122", "#11223344"})
      assertThatThrownBy(() -> rules.color(invalid)).isInstanceOf(RequestFailure.class);
    assertThatThrownBy(() -> rules.color(null)).isInstanceOf(RequestFailure.class);
  }
}
