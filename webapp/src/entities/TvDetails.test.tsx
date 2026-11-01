import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { TvDetails } from "./TvDetails";
import type { Entity } from "./model";
import type { TvDetails as Details, Episode } from "./tvApi";

const entity: Entity = {
  id: "00000000-0000-4000-8000-000000000001",
  type: "TV_SHOW",
  title: "Show",
  status: "TO_BE_SEEN",
  createdAt: "2026-01-01T00:00:00Z",
  version: 0,
  lastCompletedAt: null,
  totalCompletionCount: 0,
};
const initial: Details = {
  entityId: entity.id,
  tvmazeId: 1,
  version: 0,
  entityVersion: 0,
  canonicalTitle: "Show",
  alias: "My label",
  status: "TO_BE_SEEN",
  show: {
    name: "Show",
    posterUrl: null,
    summary: "A story",
    genres: ["Drama"],
    providerStatus: "Running",
    averageRuntime: 45,
    tvmazeRating: 8,
    imdbUrl: null,
  },
  lastWatchedEpisodeId: null,
  watchedCount: 0,
  airedCount: 1,
  newEpisodesAvailable: true,
  canMarkSeen: false,
  metadata: {
    state: "READY",
    fetchedAt: "2026-01-01T00:00:00Z",
    nextAttemptAt: null,
  },
};
const episodes: Episode[] = [
  {
    id: 11,
    seasonId: 10,
    season: 1,
    number: 1,
    title: "Pilot",
    airAt: "2020-01-01T00:00:00Z",
    airDate: "2020-01-01",
    runtime: 45,
    imageUrl: null,
    summary: "Episode story",
    watched: false,
    canMarkWatched: true,
  },
  {
    id: 12,
    seasonId: 10,
    season: 1,
    number: 2,
    title: "Unknown",
    airAt: null,
    airDate: null,
    runtime: 45,
    imageUrl: null,
    summary: null,
    watched: false,
    canMarkWatched: true,
  },
  {
    id: 13,
    seasonId: 10,
    season: 1,
    number: 3,
    title: "Future",
    airAt: "2099-01-01T00:00:00Z",
    airDate: "2099-01-01",
    runtime: 45,
    imageUrl: null,
    summary: null,
    watched: false,
    canMarkWatched: false,
  },
];
beforeEach(() => {
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute("open", "");
  };
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute("open");
  };
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});
function fixture(start: Details = initial) {
  let data = structuredClone(start);
  const mutations: { path: string; body: Record<string, unknown> }[] = [];
  const fetcher = vi.fn(
    async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      let result: unknown;
      if (init?.method && init.method !== "GET") {
        const body = JSON.parse(String(init.body)) as Record<string, unknown>;
        mutations.push({ path, body });
        data = {
          ...data,
          version: data.version + 1,
          entityVersion: data.entityVersion + 1,
        };
        if (path.endsWith("/position"))
          data = {
            ...data,
            lastWatchedEpisodeId: Number(body.episodeId),
            status: "SEEING",
            ...(body.markPreviousWatched
              ? {
                  watchedCount: 1,
                  newEpisodesAvailable: false,
                  canMarkSeen: true,
                }
              : {}),
          };
        if (path.endsWith("/watched"))
          data = { ...data, status: "SEEING", watchedCount: 1 };
        if (path.endsWith("/change-show"))
          data = {
            ...data,
            tvmazeId: Number(body.tvmazeId),
            canonicalTitle: "Replacement",
            status: "TO_BE_SEEN",
            lastWatchedEpisodeId: null,
            watchedCount: 0,
          };
        result = data;
      } else if (path.endsWith("/settings"))
        result = { minimumCharacters: 2, debounceMs: 300, pageSize: 8 };
      else if (path.includes("/tvmaze/shows?"))
        result = {
          items: [
            {
              tvmazeId: 2,
              name: "Replacement",
              premiereYear: 2020,
              posterUrl: null,
              providerStatus: "Running",
            },
          ],
          state: "FRESH",
          matchStatus: "UNAMBIGUOUS",
          minimumCharacters: 2,
          debounceMs: 300,
          pageSize: 8,
        };
      else if (path.endsWith("/seasons"))
        result = {
          items: data.metadata.fetchedAt
            ? [
                {
                  id: 10,
                  number: 1,
                  name: null,
                  regularEpisodeCount: 3,
                  airedCount: 1,
                  watchedCount: data.watchedCount,
                },
                {
                  id: 20,
                  number: 2,
                  name: null,
                  regularEpisodeCount: 1,
                  airedCount: 0,
                  watchedCount: 0,
                },
              ]
            : [],
          metadataStatus: "READY",
        };
      else if (path.endsWith("/episodes"))
        result = {
          items: path.includes("/20/")
            ? [episodes[2]]
            : episodes.map((e) =>
                e.id === 11 ? { ...e, watched: data.watchedCount > 0 } : e,
              ),
          metadataStatus: "READY",
        };
      else result = data;
      return new Response(JSON.stringify(result));
    },
  );
  vi.stubGlobal("fetch", fetcher);
  const rendered = render(
    <TvDetails entity={entity} revision={0} onChanged={vi.fn()} />,
  );
  return {
    mutations,
    fetcher,
    rerender: rendered.rerender,
    loadCatalog: () => {
      data = structuredClone(initial);
    },
  };
}
it("enables season and episode selection when a pending catalog loads and preserves unsaved selection across refreshes", async () => {
  const { rerender, loadCatalog, fetcher } = fixture({
    ...initial,
    show: null,
    metadata: { state: "PENDING", fetchedAt: null, nextAttemptAt: null },
  });
  await screen.findByText("Loading seasons and episodes…");
  expect(
    (screen.getByRole("group", { name: "Last watched" }) as HTMLFieldSetElement)
      .disabled,
  ).toBe(true);
  expect(
    within(screen.getByLabelText("Season")).getAllByRole("option"),
  ).toHaveLength(1);
  loadCatalog();
  rerender(<TvDetails entity={entity} revision={1} onChanged={vi.fn()} />);
  await waitFor(() =>
    expect(
      within(screen.getByLabelText("Season")).getAllByRole("option"),
    ).toHaveLength(2),
  );
  expect(
    (screen.getByRole("group", { name: "Last watched" }) as HTMLFieldSetElement)
      .disabled,
  ).toBe(false);
  fireEvent.change(screen.getByLabelText("Season"), {
    target: { value: "10" },
  });
  await waitFor(() =>
    expect(
      within(screen.getByLabelText("Episode")).getAllByRole("option"),
    ).toHaveLength(2),
  );
  fireEvent.change(screen.getByLabelText("Episode"), {
    target: { value: "11" },
  });
  const episodeReads = () =>
    fetcher.mock.calls.filter((call) =>
      String(call[0]).endsWith("/seasons/10/episodes"),
    ).length;
  const previousReads = episodeReads();
  rerender(<TvDetails entity={entity} revision={2} onChanged={vi.fn()} />);
  await waitFor(() => expect(episodeReads()).toBeGreaterThan(previousReads));
  await waitFor(() =>
    expect((screen.getByLabelText("Episode") as HTMLSelectElement).value).toBe(
      "11",
    ),
  );
});
it("preserves an unsaved alias when parent data refreshes", async () => {
  const { rerender, fetcher } = fixture();
  await screen.findByText("Show · My label");
  fireEvent.click(screen.getByText("Edit show"));
  fireEvent.change(screen.getByLabelText("Alias"), {
    target: { value: "Unsaved label" },
  });
  const reads = fetcher.mock.calls.length;
  rerender(<TvDetails entity={entity} revision={1} onChanged={vi.fn()} />);
  await waitFor(() => expect(fetcher.mock.calls.length).toBeGreaterThan(reads));
  await waitFor(() =>
    expect((screen.getByLabelText("Alias") as HTMLInputElement).value).toBe(
      "Unsaved label",
    ),
  );
});
it("keeps position separate, confirms bulk marking and disables seasons without aired episodes", async () => {
  const { mutations } = fixture();
  await screen.findByText("Show · My label");
  const season = screen.getByLabelText("Season");
  await waitFor(() =>
    expect(within(season).getAllByRole("option")).toHaveLength(2),
  );
  fireEvent.change(season, { target: { value: "10" } });
  const episode = screen.getByLabelText("Episode");
  await waitFor(() =>
    expect(within(episode).getAllByRole("option")).toHaveLength(2),
  );
  fireEvent.change(episode, { target: { value: "11" } });
  expect(mutations).toHaveLength(0);
  fireEvent.click(
    screen.getByRole("button", { name: "Save and mark previous watched…" }),
  );
  fireEvent.click(
    within(screen.getByRole("dialog")).getByRole("button", { name: "Cancel" }),
  );
  expect(mutations).toHaveLength(0);
  fireEvent.click(screen.getByRole("button", { name: "Save position only" }));
  await waitFor(() => expect(mutations).toHaveLength(1));
  expect(mutations[0].body).toMatchObject({
    episodeId: 11,
    markPreviousWatched: false,
  });
  await waitFor(() =>
    expect(screen.getByText("New episodes available")).toBeTruthy(),
  );
  expect(within(season).queryByText("Season 2")).toBeNull();
  fireEvent.change(season, { target: { value: "" } });
  await waitFor(() =>
    expect(
      (screen.getByLabelText("Episode") as HTMLSelectElement).disabled,
    ).toBe(true),
  );
  expect(
    (
      screen.getByRole("button", {
        name: "Save position only",
      }) as HTMLButtonElement
    ).disabled,
  ).toBe(true);
  expect(mutations).toHaveLength(1);
});
it("excludes fully watched seasons and watched episodes from Last watched but retains the episode browser", async () => {
  fixture({ ...initial, watchedCount: 1, canMarkSeen: true });
  await screen.findByText("Show · My label");
  await screen.findByText("All released episodes are watched.");
  expect(
    within(screen.getByLabelText("Season")).queryByText("Season 1"),
  ).toBeNull();
  expect(
    within(screen.getByLabelText("Season")).queryByText("Season 2"),
  ).toBeNull();
  fireEvent.click(screen.getByText(/Episodes · Season/));
  expect(
    within(screen.getByLabelText("Browse season")).getByText("Season 1"),
  ).toBeTruthy();
});
it("allows explicit unknown-date watching while blocking future episodes", async () => {
  const { mutations } = fixture();
  const unknown = await screen.findByRole("checkbox", { name: /Unknown/ });
  expect((unknown as HTMLInputElement).disabled).toBe(false);
  expect(
    (screen.getByRole("checkbox", { name: /Future/ }) as HTMLInputElement)
      .disabled,
  ).toBe(true);
  expect(screen.getByText("Air date unknown")).toBeTruthy();
  fireEvent.click(unknown);
  await waitFor(() => expect(mutations).toHaveLength(1));
  expect(mutations[0].path).toContain("/episodes/12/watched");
  expect(mutations[0].body.watched).toBe(true);
});
it("requires review and confirmation before replacing a show and supports cancellation", async () => {
  const { mutations } = fixture();
  await screen.findByText("Show · My label");
  fireEvent.click(screen.getByText("Edit show"));
  fireEvent.click(screen.getByRole("button", { name: "Change show" }));
  fireEvent.change(screen.getByRole("combobox", { name: "Title" }), {
    target: { value: "Replacement" },
  });
  fireEvent.click(
    await screen.findByRole("option", { name: "Replacement (2020)" }),
  );
  fireEvent.click(screen.getByRole("button", { name: "Review replacement…" }));
  expect(screen.getByRole("dialog").textContent).toContain(
    "alias, linked pages and completion history are preserved",
  );
  fireEvent.click(
    within(screen.getByRole("dialog")).getByRole("button", { name: "Cancel" }),
  );
  expect(mutations).toHaveLength(0);
  fireEvent.click(screen.getByRole("button", { name: "Review replacement…" }));
  fireEvent.click(
    within(screen.getByRole("dialog")).getByRole("button", { name: "Confirm" }),
  );
  await screen.findByText("Replacement · My label");
  expect(mutations).toHaveLength(1);
  expect(mutations[0].body).toMatchObject({
    tvmazeId: 2,
    confirmResetProgress: true,
    version: 0,
    entityVersion: 0,
  });
});
