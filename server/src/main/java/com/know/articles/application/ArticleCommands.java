package com.know.articles.application;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ArticleCommands {
  private final CurrentAccount currentAccount;
  private final ArticleContent contentPolicy;
  private final ArticleWriter writer;
  private final com.know.categories.application.CategoryCatalog categories;

  @Transactional
  public SaveResult save(SaveArticle command) {
    var account = currentAccount.requireId();
    categories.ensureDefaults(account);
    contentPolicy.validateUrl(command.url());
    contentPolicy.validateTitle(command.title());
    var content = command.content() == null ? null : contentPolicy.sanitize(command.content());
    var clean = new SaveArticle(command.url(), command.title(), content);
    boolean inserted = writer.insertIfAbsent(account, clean);
    var article = writer.lockExactUrl(account, command.url());
    if (inserted) return new SaveResult(SaveResult.Outcome.CREATED, article);
    if (article.content() == null && content != null) {
      article = writer.revise(account, article.id(), article.title(), content);
      return new SaveResult(SaveResult.Outcome.ENRICHED, article);
    }
    if (article.content() != null && content != null) {
      throw new ArticleFailure(
          409,
          "DUPLICATE_URL",
          "Article already has saved content; confirm an explicit update.",
          article.id());
    }
    return new SaveResult(SaveResult.Outcome.UNCHANGED, article);
  }

  @Transactional
  public ArticleSnapshot update(UUID id, UpdateArticle command) {
    var account = currentAccount.requireId();
    var article = writer.lockOwned(account, id);
    if (command.version() == null
        || command.version() < 0
        || (command.title() == null
            && command.content() == null
            && command.category() == null
            && !command.entityNamePresent())) {
      throw new ArticleFailure(
          400, "INVALID_REQUEST", "Supply a non-negative version and title or content.");
    }
    if (article.version() != command.version()) {
      throw new ArticleFailure(409, "VERSION_CONFLICT", "Article changed; reload before updating.");
    }
    if (command.title() != null) contentPolicy.validateTitle(command.title());
    if (command.category() != null) {
      categories.ensureDefaults(account);
      categories.requireEnabled(account, command.category());
    }
    if (command.entityNamePresent() && command.entityName() != null)
      contentPolicy.validateTitle(command.entityName());
    var title = command.title() == null ? article.title() : command.title();
    var content =
        command.content() == null ? article.content() : contentPolicy.sanitize(command.content());
    return writer.revise(
        account,
        id,
        title,
        content,
        command.category() == null ? article.category() : command.category(),
        command.entityNamePresent() ? command.entityName() : article.entityName());
  }
}
