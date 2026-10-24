package com.know.articles.presentation;

import com.know.articles.application.ArticleFailure;
import com.know.articles.application.UrlHashCollisionException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ArticleController.class)
public class ArticleErrors {
  @ExceptionHandler(ArticleFailure.class)
  public void failure(
      ArticleFailure failure, HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    request.setAttribute(ArticleFailure.class.getName(), failure);
    response.sendError(failure.getStatus(), failure.getMessage());
  }

  @ExceptionHandler(UrlHashCollisionException.class)
  public void collision(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    failure(
        new ArticleFailure(500, "URL_HASH_COLLISION", "Unable to save this URL."),
        request,
        response);
  }
}
