package com.know.entities.application;

import static com.know.entities.application.EntityModels.*;

import com.know.accounts.application.CurrentAccount;
import com.know.articles.application.ArticlePosition;
import com.know.articles.application.ArticleReader;
import com.know.articles.application.ArticleSummary;
import com.know.shared.application.*;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EntityService {
  private final CurrentAccount current;
  private final EntityStore store;
  private final ArticleReader articles;
  private final PageLimits limits;
  private final EntitySettings settings;
  private final Clock entityClock;
  private final org.springframework.context.ApplicationEventPublisher events;

  public Details detail(UUID id) {
    return owned(current.requireId(), id);
  }

  @Transactional
  public Details create(Create command) {
    validateCreate(command);
    var owner = writeOwner();
    rejectDuplicate(owner, command.type(), command.title(), null);
    var now = now();
    var status = command.status() == null ? command.type().initialStatus : command.status();
    var entity =
        store.insert(owner, new Create(command.type(), command.title(), status, null), now);
    if (status.equals(command.type().completedStatus))
      store.addCompletion(owner, entity.id(), time(command.completedAt(), now), now);
    return owned(owner, entity.id());
  }

  @Transactional
  public Details edit(UUID id, Edit command) {
    var owner = writeOwner();
    var entity = owned(owner, id);
    version(command.version(), entity.version());
    if (command.title() == null && command.status() == null)
      throw invalid("Supply title and/or status.");
    var title = command.title() == null ? entity.title() : command.title();
    validateTitle(title);
    var status = command.status() == null ? entity.status() : command.status();
    entity.type().validateStatus(status);
    boolean completing =
        !entity.status().equals(status) && status.equals(entity.type().completedStatus);
    if (command.completedAt() != null && !completing)
      throw invalid("completedAt requires a transition to completed status.");
    rejectDuplicate(owner, entity.type(), title, id);
    if (completing) {
      var now = now();
      store.addCompletion(owner, id, time(command.completedAt(), now), now);
    }
    return store.revise(owner, id, title, status, completing);
  }

  public Page<Summary> list(Filter filter, Integer limit, String cursor) {
    validateFilter(filter);
    var owner = current.requireId();
    var scope =
        ScopedCursor.scope(
            "entities",
            owner,
            filter.type(),
            filter.query(),
            filter.status(),
            filter.completionYear());
    int size = limit(limit);
    var rows = store.list(owner, filter, ScopedCursor.decode(cursor, scope), size + 1);
    return page(rows, size, scope, e -> new ScopedCursor.Position(e.createdAt(), e.id()));
  }

  public List<Year> years(Filter filter) {
    validateFilter(filter);
    return store.years(current.requireId(), filter);
  }

  public Page<Completion> completions(UUID id, Integer limit, String cursor) {
    var owner = current.requireId();
    owned(owner, id);
    var scope = ScopedCursor.scope("completions", owner, id);
    int size = limit(limit);
    return page(
        store.completions(owner, id, ScopedCursor.decode(cursor, scope), size + 1),
        size,
        scope,
        e -> new ScopedCursor.Position(e.completedAt(), e.id()));
  }

  @Transactional
  public CompletionResult addCompletion(UUID id, Long version, Instant completedAt) {
    var owner = writeOwner();
    var entity = owned(owner, id);
    version(version, entity.version());
    var now = now();
    var completion = store.addCompletion(owner, id, time(completedAt, now), now);
    var updated = store.revise(owner, id, entity.title(), entity.type().completedStatus, true);
    return new CompletionResult(updated, completion);
  }

  @Transactional
  public Completion correctCompletion(UUID entityId, UUID id, Long version, Instant completedAt) {
    var owner = writeOwner();
    var entity = owned(owner, entityId);
    var completion = completion(owner, entityId, id);
    version(version, completion.version());
    if (completedAt == null) throw invalid("completedAt is required.");
    var timestamp = time(completedAt, now());
    if (completion.completedAt().equals(timestamp)) return completion;
    var updated = store.correctCompletion(owner, id, timestamp);
    store.revise(owner, entityId, entity.title(), entity.status(), true);
    return updated;
  }

  @Transactional
  public void deleteCompletion(UUID entityId, UUID id, Long version) {
    var owner = writeOwner();
    var entity = owned(owner, entityId);
    var event = completion(owner, entityId, id);
    version(version, event.version());
    store.deleteCompletion(owner, id);
    store.revise(owner, entityId, entity.title(), entity.status(), true);
  }

  public Page<LinkedEntity> linkedEntities(UUID articleId, Integer limit, String cursor) {
    var owner = current.requireId();
    article(owner, articleId);
    var scope = ScopedCursor.scope("article-entities", owner, articleId);
    int size = limit(limit);
    return page(
        store.linkedEntities(owner, articleId, ScopedCursor.decode(cursor, scope), size + 1),
        size,
        scope,
        e -> new ScopedCursor.Position(e.createdAt(), e.id()));
  }

  @Transactional
  public LinkResult addFromArticle(UUID articleId, Create command) {
    if (command.status() != null || command.completedAt() != null)
      throw invalid("Article Add accepts only type and title.");
    validateCreate(command);
    var owner = writeOwner();
    article(owner, articleId);
    var existing = store.findExact(owner, command.type(), command.title());
    var entity =
        existing.orElseGet(
            () ->
                store.insert(
                    owner,
                    new Create(command.type(), command.title(), command.type().initialStatus, null),
                    now()));
    var previous = store.link(owner, articleId, entity.id());
    var link = previous.orElseGet(() -> store.addLink(owner, articleId, entity.id(), now()));
    if (previous.isEmpty()) events.publishEvent(new ArticleEntitiesChanged(owner, articleId));
    return new LinkResult(
        existing.isEmpty() ? "CREATED" : previous.isPresent() ? "UNCHANGED" : "LINKED",
        entity,
        link);
  }

  @Transactional
  public LinkResult link(UUID articleId, UUID entityId) {
    var owner = writeOwner();
    article(owner, articleId);
    var entity = owned(owner, entityId);
    var previous = store.link(owner, articleId, entityId);
    var link = previous.orElseGet(() -> store.addLink(owner, articleId, entityId, now()));
    if (previous.isEmpty()) events.publishEvent(new ArticleEntitiesChanged(owner, articleId));
    return new LinkResult(previous.isPresent() ? "UNCHANGED" : "LINKED", entity, link);
  }

  @Transactional
  public void unlink(UUID articleId, UUID entityId) {
    var owner = writeOwner();
    article(owner, articleId);
    owned(owner, entityId);
    if (store.link(owner, articleId, entityId).isPresent()) {
      store.unlink(owner, articleId, entityId);
      events.publishEvent(new ArticleEntitiesChanged(owner, articleId));
    }
  }

  public Page<ArticleSummary> sources(UUID entityId, Integer limit, String cursor) {
    var owner = current.requireId();
    owned(owner, entityId);
    var scope = ScopedCursor.scope("entity-articles", owner, entityId);
    int size = limit(limit);
    var before = ScopedCursor.decode(cursor, scope);
    return page(
        articles.findLinkedToEntity(
            owner,
            entityId,
            before == null ? null : new ArticlePosition(before.time(), before.id()),
            size + 1),
        size,
        scope,
        a -> new ScopedCursor.Position(a.savedAt(), a.id()));
  }

  private UUID writeOwner() {
    var owner = current.requireId();
    store.lockWrites(owner);
    return owner;
  }

  private Details owned(UUID owner, UUID id) {
    return store.find(owner, id).orElseThrow(EntityService::missing);
  }

  private Completion completion(UUID owner, UUID parent, UUID id) {
    return store.completion(owner, parent, id).orElseThrow(EntityService::missing);
  }

  private void article(UUID owner, UUID id) {
    if (!articles.existsOwned(owner, id)) throw missing();
  }

  private void rejectDuplicate(UUID owner, EntityType type, String title, UUID except) {
    store
        .findExact(owner, type, title)
        .filter(e -> !e.id().equals(except))
        .ifPresent(
            e -> {
              throw new RequestFailure(
                  409,
                  "DUPLICATE_ENTITY",
                  "An entity with this exact title and type already exists.",
                  e.id());
            });
  }

  private void validateCreate(Create command) {
    if (command.type() == null) throw invalid("type is required.");
    validateTitle(command.title());
    var status = command.status() == null ? command.type().initialStatus : command.status();
    command.type().validateStatus(status);
    if (command.completedAt() != null && !status.equals(command.type().completedStatus))
      throw invalid("completedAt requires completed status.");
  }

  private void validateTitle(String title) {
    if (title == null
        || title.isBlank()
        || title.codePointCount(0, title.length()) > settings.maxTitleCodePoints())
      throw invalid("Title must be nonblank and within the configured length limit.");
  }

  private void validateFilter(Filter filter) {
    if (filter.type() == null) throw invalid("Select one entity type.");
    if (filter.status() != null) filter.type().validateStatus(filter.status());
    if (filter.completionYear() != null
        && (filter.completionYear() < 1 || filter.completionYear() > 9999))
      throw invalid("completionYear must be between 1 and 9999.");
  }

  private int limit(Integer requested) {
    int value = requested == null ? limits.defaultLimit() : requested;
    if (value < 1 || value > limits.maxLimit()) throw invalid("Invalid page limit.");
    return value;
  }

  private static void version(Long expected, long actual) {
    if (expected == null || expected < 0) throw invalid("A non-negative version is required.");
    if (expected != actual)
      throw new RequestFailure(409, "VERSION_CONFLICT", "Record changed; reload before updating.");
  }

  private Instant now() {
    return entityClock.instant().truncatedTo(ChronoUnit.MICROS);
  }

  private static Instant time(Instant supplied, Instant now) {
    var value = supplied == null ? now : supplied;
    if (value.isBefore(Instant.parse("0001-01-01T00:00:00Z"))
        || value.isAfter(Instant.parse("9999-12-31T23:59:59.999999Z")))
      throw invalid("Timestamp is outside the supported range.");
    return value;
  }

  private static RequestFailure invalid(String message) {
    return new RequestFailure(400, "INVALID_REQUEST", message);
  }

  private static RequestFailure missing() {
    return new RequestFailure(404, "RESOURCE_NOT_FOUND", "Resource not found.");
  }

  private static <T> Page<T> page(
      List<T> rows, int size, String scope, Function<T, ScopedCursor.Position> position) {
    boolean more = rows.size() > size;
    var items = rows.subList(0, Math.min(size, rows.size()));
    return new Page<>(
        items, more ? ScopedCursor.encode(position.apply(items.getLast()), scope) : null);
  }
}
