package com.know.connections.infrastructure.config;

import static com.know.connections.application.ConnectionModels.*;

import com.know.connections.application.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
@Slf4j
public class ConnectionWorker {
  private final ConnectionStore store;
  private final EmbeddingProvider provider;
  private final ConnectionProfiles profiles;
  private final ConnectionProperties properties;
  private final ConnectionPolicy policy;
  private final Clock clock;
  private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
  private final org.springframework.core.task.AsyncTaskExecutor tasks =
      new org.springframework.core.task.support.TaskExecutorAdapter(workers);
  private final ScheduledExecutorService heartbeats =
      Executors.newScheduledThreadPool(1, Thread.ofPlatform().daemon().factory());
  private final AtomicInteger active = new AtomicInteger();
  private volatile boolean closed;

  public ConnectionWorker(
      ConnectionStore store,
      EmbeddingProvider provider,
      ConnectionProfiles profiles,
      ConnectionProperties properties,
      Clock clock) {
    this.store = store;
    this.provider = provider;
    this.profiles = profiles;
    this.properties = properties;
    this.policy = properties.policy();
    this.clock = clock;
  }

  @PostConstruct
  public void configuration() {
    log.info(
        "Connection processing configuration: enabled={}, schedulingEnabled={}, liveRequestsEnabled={}, provider={}, model={}, keyConfigured={}, unbilledProjectConfirmed={}",
        policy.enabled(),
        properties.schedulingEnabled(),
        policy.liveRequestsEnabled(),
        policy.provider(),
        policy.model(),
        !properties.embedding().gemini().apiKey().isBlank(),
        properties.embedding().unbilledProjectConfirmed());
    if (!policy.enabled())
      log.warn(
          "Article connections are disabled: set know.connections.enabled=true to enable processing.");
    else if (!policy.liveRequestsEnabled())
      log.warn(
          "Semantic connections are paused: know.connections.embedding.live-requests-enabled=false. Shared-entity matching remains enabled; no Gemini requests will be sent.");
  }

  @EventListener(ApplicationReadyEvent.class)
  public void ready() {
    if (policy.enabled() && policy.liveRequestsEnabled()) {
      int resumed = store.resumeConfiguredWork(profiles.current().id(), clock.instant());
      log.info(
          "Connection processing ready: resumed {} embedding jobs previously paused by configuration; provider quota cooldowns are preserved",
          resumed);
      if (properties.schedulingEnabled()) kick();
    }
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void committed(ConnectionWorkAvailable event) {
    if (properties.schedulingEnabled()) kick();
  }

  @Scheduled(fixedDelayString = "${know.connections.poll-interval:2s}")
  public void poll() {
    if (properties.schedulingEnabled()) kick();
  }

  @Scheduled(fixedDelayString = "${know.connections.backfill-interval:1m}")
  public void backfill() {
    if (policy.enabled() && properties.schedulingEnabled() && !closed) {
      store.backfill(profiles.current(), policy.backfillBatchSize(), clock.instant());
      kick();
    }
  }

  public void kick() {
    if (!policy.enabled() || closed) return;
    for (int slots = 0; slots < properties.localWorkers(); slots++) {
      int count = active.get();
      if (count >= properties.localWorkers()) return;
      if (active.compareAndSet(count, count + 1))
        tasks.submit(
            () -> {
              try {
                for (int i = 0; i < properties.workerBatchSize() && !closed; i++) {
                  if (!runOne()) break;
                }
              } finally {
                active.decrementAndGet();
              }
            });
    }
  }

  /** Also exposed to controlled integration tests; every claim remains database coordinated. */
  public boolean runOne() {
    if (!policy.enabled()) return false;
    Job job = store.claim(clock.instant(), policy.leaseDuration());
    if (job == null) return false;
    log.debug(
        "Connection job started: jobId={}, articleId={}, kind={}, revision={}",
        job.id(),
        job.articleId(),
        job.kind(),
        job.revision());
    Thread thread = Thread.currentThread();
    AtomicReference<UUID> permit = new AtomicReference<>();
    AtomicBoolean lost = new AtomicBoolean();
    var heartbeat =
        heartbeats.scheduleAtFixedRate(
            () -> {
              try {
                synchronized (permit) {
                  if (!store.renew(job, permit.get(), clock.instant(), policy.leaseDuration())) {
                    lost.set(true);
                    thread.interrupt();
                  }
                }
              } catch (RuntimeException error) {
                lost.set(true);
                thread.interrupt();
              }
            },
            policy.heartbeatInterval().toMillis(),
            policy.heartbeatInterval().toMillis(),
            TimeUnit.MILLISECONDS);
    try {
      if (!job.profileId().equals(profiles.current().id())) {
        store.defer(
            job,
            clock.instant().plus(policy.pollInterval()),
            "PROFILE_CHANGED",
            null,
            clock.instant());
        return true;
      }
      if (job.kind() == Kind.CONNECTIONS) {
        store.connect(job, policy, clock.instant());
        store.finish(job, null, clock.instant());
      } else embed(job, permit, lost);
    } catch (Deferred ignored) {
      // Already-deferred jobs retain their quota/retry deadline. A revision that
      // changed immediately before a provider call must also release its claim.
      store.defer(job, clock.instant().plus(policy.pollInterval()), null, null, clock.instant());
    } catch (EmbeddingFailure failure) {
      if (failure.status() == 429) {
        var until = clock.instant().plus(policy.providerCooldown());
        if (failure.retryAfter() != null && failure.retryAfter().isAfter(until))
          until = failure.retryAfter();
        store.rateLimited(policy.quotaScope(), until, clock.instant());
        log.warn(
            "Embedding provider rate limited: jobId={}, articleId={}, httpStatus=429, resumeAt={}",
            job.id(),
            job.articleId(),
            until);
        store.defer(
            job,
            until,
            "PROVIDER_QUOTA",
            "Provider quota exhausted; work remains queued.",
            clock.instant());
      } else {
        log.warn(
            "Embedding request failed: jobId={}, articleId={}, httpStatus={}, failureNumber={}, transientFailure={}, retryAfter={}",
            job.id(),
            job.articleId(),
            failure.status(),
            job.attempts() + 1,
            failure.transientFailure(),
            failure.retryAfter());
        store.failed(job, failure, policy, clock.instant());
      }
    } catch (com.know.shared.application.RequestFailure changed) {
      store.defer(job, clock.instant().plus(policy.pollInterval()), null, null, clock.instant());
    } catch (RuntimeException failure) {
      log.error(
          "Connection job failed: jobId={}, exceptionType={}, sqlState={}",
          job.id(),
          failure.getClass().getSimpleName(),
          sqlState(failure));
      store.failed(job, new EmbeddingFailure(503, null), policy, clock.instant());
    } finally {
      heartbeat.cancel(false);
      UUID held = permit.getAndSet(null);
      if (held != null) store.release(new Permit(held, policy.quotaScope(), null, null));
      if (lost.get()) Thread.interrupted();
    }
    return true;
  }

  private void embed(Job job, AtomicReference<UUID> permit, AtomicBoolean lost) {
    var article = store.article(job.accountId(), job.articleId());
    String text = article.content() == null ? "" : Jsoup.parse(article.content()).text();
    byte[] hash = EmbeddingMath.hash(text);
    if (text.isBlank()) {
      store.finish(job, hash, clock.instant());
      return;
    }
    var chunks = new ArrayList<>(store.plan(job, hash));
    int work = 0;
    for (int index = 0; ; index++) {
      if (lost.get() || !store.current(job, clock.instant())) {
        store.defer(job, clock.instant().plus(policy.pollInterval()), null, null, clock.instant());
        throw new Deferred();
      }
      Chunk chunk;
      if (index < chunks.size()) chunk = chunks.get(index);
      else {
        int start = chunks.isEmpty() ? 0 : nextStart(text, chunks.getLast());
        if (start >= text.length()) break;
        int end =
            EmbeddingMath.boundary(text, Math.min(text.length(), start + policy.chunkCharacters()));
        int tokens;
        while (true) {
          final String candidate = text.substring(start, end);
          tokens = call(job, false, permit, () -> provider.countTokens(candidate));
          if (tokens <= policy.maxTokens()) break;
          int shorter = EmbeddingMath.boundary(text, start + Math.max(1, (end - start) / 2));
          if (shorter <= start) throw new EmbeddingFailure(400, null);
          end = shorter;
        }
        if (tokens < 1) throw new EmbeddingFailure(502, null);
        chunk = new Chunk(start, end, tokens);
        chunks.add(chunk);
        store.plan(job, hash, chunks, clock.instant());
      }
      if (!store.cached(job, hash, index, chunk)) {
        if (!policy.liveRequestsEnabled()) {
          store.defer(
              job,
              clock.instant().plus(Duration.ofMinutes(1)),
              "LIVE_REQUESTS_DISABLED",
              "Live embedding requests are disabled.",
              clock.instant());
          throw new Deferred();
        }
        String part = text.substring(chunk.start(), chunk.end());
        float[] vector = call(job, true, permit, () -> provider.embed(part, policy.dimensions()));
        store.vector(
            job,
            article,
            hash,
            index,
            chunk,
            EmbeddingMath.normalized(vector, policy.dimensions()),
            clock.instant());
        work++;
      }
      if (chunk.end() == text.length()) break;
      if (work >= policy.chunksPerPass()) {
        store.defer(job, clock.instant().plus(policy.pollInterval()), null, null, clock.instant());
        throw new Deferred();
      }
    }
    store.finish(job, hash, clock.instant());
  }

  private int nextStart(String text, Chunk previous) {
    if (previous.end() == text.length()) return text.length();
    int overlap = Math.min(policy.overlapCharacters(), (previous.end() - previous.start()) / 2);
    return Math.max(previous.start() + 1, EmbeddingMath.boundary(text, previous.end() - overlap));
  }

  private <T> T call(Job job, boolean embedding, AtomicReference<UUID> held, Supplier<T> request) {
    if (!policy.liveRequestsEnabled()) {
      store.defer(
          job,
          clock.instant().plus(Duration.ofMinutes(1)),
          "LIVE_REQUESTS_DISABLED",
          "Live embedding requests are disabled.",
          clock.instant());
      throw new Deferred();
    }
    if (!store.current(job, clock.instant())) throw new Deferred();
    Permit permit = store.acquire(job, embedding, policy, clock.instant());
    if (!permit.acquired()) {
      store.defer(
          job,
          permit.waitUntil(),
          permit.reason(),
          "Waiting for shared provider capacity.",
          clock.instant());
      throw new Deferred();
    }
    synchronized (held) {
      held.set(permit.token());
    }
    try {
      return request.get();
    } finally {
      // Renewal must not mistake normal permit release for an expired lease.
      synchronized (held) {
        held.set(null);
        store.release(permit);
      }
    }
  }

  @PreDestroy
  public void close() {
    closed = true;
    workers.shutdownNow();
    heartbeats.shutdownNow();
  }

  private static class Deferred extends RuntimeException {}

  private static String sqlState(Throwable failure) {
    for (int depth = 0; failure != null && depth < 12; depth++, failure = failure.getCause())
      if (failure instanceof java.sql.SQLException sql) return sql.getSQLState();
    return "none";
  }
}
