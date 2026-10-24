package com.know.articles.presentation;

import com.know.articles.application.ArticleFailure;
import java.util.Map;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;

@Component
public class ArticleErrorAttributes extends DefaultErrorAttributes {
  @Override
  public Map<String, Object> getErrorAttributes(WebRequest request, ErrorAttributeOptions options) {
    var attributes = super.getErrorAttributes(request, options);
    if (Integer.valueOf(400).equals(attributes.get("status"))) {
      attributes.put("reason", "INVALID_REQUEST");
      attributes.put("message", "Invalid request.");
    }
    var failure =
        request.getAttribute(ArticleFailure.class.getName(), RequestAttributes.SCOPE_REQUEST);
    if (failure instanceof ArticleFailure articleFailure) {
      attributes.put("reason", articleFailure.getReason());
      attributes.put("message", articleFailure.getMessage());
      if (articleFailure.getExistingArticleId() != null)
        attributes.put("existingArticleId", articleFailure.getExistingArticleId());
    }
    return attributes;
  }
}
