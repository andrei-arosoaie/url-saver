package com.know.articles.infrastructure.config;

import static org.assertj.core.api.Assertions.*;

import com.know.articles.application.ArticleFailure;
import org.junit.jupiter.api.Test;

class HtmlArticleContentTest {
  private final HtmlArticleContent policy =
      new HtmlArticleContent(new ArticleProperties(4194304, 1000));

  @Test
  void preservesArticleTextAndFormattingWhileRemovingExecutableMarkup() {
    var clean =
        policy.sanitize(
            "<article><h1>Title</h1><custom>Unusual wrapper text</custom><form>Form prose</form><p onclick='bad()'>Hello <em>世界</em></p><pre><code>&lt;script&gt;example&lt;/script&gt;</code></pre><script>alert('evil')</script><img src='https://tracker.test/x'><a href='javascript:alert(1)'>Unsafe link text</a><table><tr><td>Cell</td></tr></table></article>");
    assertThat(clean)
        .contains(
            "Title",
            "Unusual wrapper text",
            "Form prose",
            "Hello",
            "世界",
            "example",
            "Unsafe link text",
            "Cell",
            "<h1>",
            "<em>",
            "<table>",
            "&lt;script&gt;")
        .doesNotContain("onclick", "javascript:", "<script", "<img", "evil", "<form", "<custom");
  }

  @Test
  void checksUtf8InputAndExpandedSanitizedOutput() {
    var small = new HtmlArticleContent(new ArticleProperties(10, 2));
    assertThatThrownBy(() -> small.sanitize("世".repeat(4)))
        .isInstanceOfSatisfying(
            ArticleFailure.class, e -> assertThat(e.getStatus()).isEqualTo(413));
    assertThatThrownBy(() -> small.sanitize("&".repeat(3)))
        .isInstanceOfSatisfying(
            ArticleFailure.class, e -> assertThat(e.getStatus()).isEqualTo(413));
    small.validateTitle("😀😀");
    assertThatThrownBy(() -> small.validateTitle("😀😀😀")).isInstanceOf(ArticleFailure.class);
  }

  @Test
  void rejectsUnsafeUrlsAndBlankContentWithoutDowngradingToLinkOnly() {
    for (var url :
        new String[] {"javascript:alert(1)", "https://example.test/ ", "/relative", "https://"}) {
      assertThatThrownBy(() -> policy.validateUrl(url)).isInstanceOf(ArticleFailure.class);
    }
    assertThatThrownBy(() -> policy.sanitize("<script>bad()</script>"))
        .isInstanceOf(ArticleFailure.class);
    assertThatThrownBy(() -> policy.validateTitle(" \t ")).isInstanceOf(ArticleFailure.class);
    assertThatThrownBy(() -> policy.sanitize("<p></p><br>")).isInstanceOf(ArticleFailure.class);
  }

  @Test
  void keepsLinkLabelsButOnlyAllowsAbsoluteHttpLinks() {
    var clean =
        policy.sanitize(
            "<a href='/relative'>Relative label</a><a href='https://example.test/'>Safe label</a><a href='data:text/html,bad'>Data label</a>");
    assertThat(clean)
        .contains("Relative label", "Safe label", "Data label", "https://example.test/")
        .doesNotContain("href=\"/relative\"", "data:");
  }
}
