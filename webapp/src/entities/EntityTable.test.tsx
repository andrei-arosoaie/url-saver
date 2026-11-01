import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, expect, test, vi } from "vitest";
import { EntityTable } from "./EntityTable";
import type { EntitySummary } from "./model";
afterEach(cleanup);
test("TV rows match the Progress layout with year, episode badges, NEW, and released-only completion", () => {
  const entity: EntitySummary = {
    id: "tv",
    type: "TV_SHOW",
    title: "Severance",
    status: "SEEING",
    createdAt: "2026-01-01T00:00:00Z",
    version: 0,
    lastCompletedAt: null,
    totalCompletionCount: 0,
    completionCount: 0,
    latestMatchingCompletionAt: null,
    tvShow: {
      tvmazeId: 1,
      alias: undefined,
      position: "S01E02",
      newEpisodesAvailable: true,
      premiereYear: 2022,
      season: 1,
      episode: 2,
      seasonEpisodeCount: 9,
      watchedCount: 2,
      airedCount: 3,
      catalogReady: true,
    },
  };
  render(<EntityTable items={[entity]} selected={null} onSelect={vi.fn()} />);
  expect(screen.getByRole("columnheader", { name: "Progress" })).toBeTruthy();
  expect(screen.queryByRole("columnheader", { name: "Seen on" })).toBeNull();
  for (const label of [
    "(2022)",
    "S01",
    "E02",
    "66%",
    "1 episode left to catch up",
  ])
    expect(screen.getByText(label)).toBeTruthy();
  expect(screen.getByLabelText("New episodes available")).toBeTruthy();
  expect(
    screen.getByRole("progressbar").getAttribute("aria-valuetext"),
  ).toContain("2 of 3 released episodes watched");
});
