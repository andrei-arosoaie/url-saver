package com.know.articles.infrastructure.config;

import com.know.articles.application.ArticleContent;
import com.know.articles.application.ArticleFailure;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HtmlArticleContent implements ArticleContent {
  private final ArticleProperties properties;
  private static final PolicyFactory POLICY =
      new HtmlPolicyBuilder()
          .allowElements(
              "article",
              "section",
              "div",
              "span",
              "h1",
              "h2",
              "h3",
              "h4",
              "h5",
              "h6",
              "p",
              "br",
              "hr",
              "strong",
              "b",
              "em",
              "i",
              "u",
              "s",
              "del",
              "ins",
              "sub",
              "sup",
              "ul",
              "ol",
              "li",
              "dl",
              "dt",
              "dd",
              "blockquote",
              "pre",
              "code",
              "kbd",
              "samp",
              "table",
              "caption",
              "thead",
              "tbody",
              "tfoot",
              "tr",
              "th",
              "td",
              "a",
              "figure",
              "figcaption")
          .allowWithoutAttributes("a", "span")
          .allowAttributes("href")
          .matching((element, attribute, value) -> absoluteHttpUrl(value) ? value : null)
          .onElements("a")
          .allowUrlProtocols("http", "https")
          .requireRelNofollowOnLinks()
          .toFactory();

  public String sanitize(String html) {
    checkSize(html);
    if (html.isBlank()) throw invalid("Article content must not be blank.");
    var clean = POLICY.sanitize(html);
    checkSize(clean);
    if (clean.replaceAll("<[^>]*>", "").replace("&#160;", " ").replace("&nbsp;", " ").isBlank())
      throw invalid("No article text remains; supply article content.");
    return clean;
  }

  public void validateTitle(String title) {
    if (title == null || title.isBlank()) throw invalid("Title is required.");
    if (title.codePointCount(0, title.length()) > properties.maxTitleCodePoints())
      throw invalid("Title exceeds the configured limit.");
  }

  public void validateUrl(String url) {
    if (!absoluteHttpUrl(url))
      throw invalid("An absolute HTTP/HTTPS URL without surrounding whitespace is required.");
  }

  private static boolean absoluteHttpUrl(String url) {
    try {
      if (url == null || !url.equals(url.strip())) return false;
      var uri = URI.create(url);
      return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
          && uri.getHost() != null;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  private void checkSize(String html) {
    if (html.getBytes(StandardCharsets.UTF_8).length > properties.maxContentBytes())
      throw new ArticleFailure(
          413, "CONTENT_TOO_LARGE", "Article content exceeds the configured UTF-8 byte limit.");
  }

  private ArticleFailure invalid(String message) {
    return new ArticleFailure(400, "INVALID_REQUEST", message);
  }
}
