import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { useState } from "react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { EntitiesPage } from "./EntitiesPage";
import { EntityApiError, type EntityApi } from "./api";
import {
  types,
  type Entity,
  type EntityRoute,
  type EntityType,
  type EntitySummary,
} from "./model";

export const baseEntity: Entity = {
  id: "00000000-0000-4000-8000-000000000001",
  type: "BOOK",
  title: "A real book",
  status: "READ",
  createdAt: "2026-10-25T18:00:00Z",
  version: 3,
  totalCompletionCount: 2,
  lastCompletedAt: "2026-10-25T18:10:00Z",
};
export function fixture(type: EntityType = "BOOK") {
  const entity = {
    ...baseEntity,
    type,
    title: `A real ${types[type].singular}`,
    status: types[type].done,
  };
  const event = {
    id: "00000000-0000-4000-8000-000000000020",
    entityId: entity.id,
    version: 0,
    completedAt: "2026-10-25T18:10:00.123456Z",
    createdAt: "2026-10-25T18:10:00Z",
  };
  return {
    list: vi.fn<EntityApi["list"]>().mockResolvedValue({
      items: [
        {
          ...entity,
          completionCount: 2,
          latestMatchingCompletionAt: entity.lastCompletedAt,
        },
      ],
      nextCursor: null,
    }),
    read: vi.fn<EntityApi["read"]>().mockResolvedValue(entity),
    years: vi
      .fn<EntityApi["years"]>()
      .mockResolvedValue([{ year: 2026, entityCount: 1, completionCount: 2 }]),
    create: vi.fn<EntityApi["create"]>().mockResolvedValue(entity),
    update: vi.fn<EntityApi["update"]>().mockResolvedValue(entity),
    history: vi
      .fn<EntityApi["history"]>()
      .mockResolvedValue({ items: [event], nextCursor: null }),
    complete: vi
      .fn<EntityApi["complete"]>()
      .mockResolvedValue({ entity, completion: event }),
    correct: vi
      .fn<EntityApi["correct"]>()
      .mockResolvedValue({ ...event, version: 1 }),
    deleteCompletion: vi
      .fn<EntityApi["deleteCompletion"]>()
      .mockResolvedValue(undefined),
    sources: vi
      .fn<EntityApi["sources"]>()
      .mockResolvedValue({ items: [], nextCursor: null }),
    linked: vi
      .fn<EntityApi["linked"]>()
      .mockResolvedValue({ items: [], nextCursor: null }),
    addFromArticle: vi.fn<EntityApi["addFromArticle"]>(),
    unlink: vi.fn<EntityApi["unlink"]>().mockResolvedValue(undefined),
  };
}
const initial: EntityRoute = {
  type: "BOOK",
  view: "table",
  id: null,
  query: "",
  year: "",
};
function Harness({
  api,
  route = initial,
  onArticle = vi.fn(),
}: {
  api: EntityApi;
  route?: EntityRoute;
  onArticle?: (id: string) => void;
}) {
  const [current, setCurrent] = useState(route);
  return (
    <EntitiesPage
      api={api}
      route={current}
      navigate={setCurrent}
      onOpenArticle={onArticle}
    />
  );
}
beforeEach(() => {
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute("open", "");
  };
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute("open");
  };
});
afterEach(cleanup);
it.each(
  (Object.keys(types) as EntityType[]).flatMap((type) =>
    (["table", "board", "year"] as const).map((view) => ({ type, view })),
  ),
)(
  "renders $type / $view with exactly one selected type",
  async ({ type, view }) => {
    const api = fixture(type);
    render(<Harness api={api} route={{ ...initial, type, view }} />);
    const tabs = within(screen.getByRole("group", { name: "Entity type" }));
    expect(tabs.getAllByRole("button")).toHaveLength(4);
    expect(
      tabs
        .getByRole("button", { name: types[type].label })
        .getAttribute("aria-pressed"),
    ).toBe("true");
    const views = within(screen.getByRole("group", { name: "Entity view" }));
    expect(
      views
        .getByRole("button", {
          name:
            view === "year" ? "By year" : view === "table" ? "Table" : "Board",
        })
        .getAttribute("aria-pressed"),
    ).toBe("true");
    await screen.findByRole("button", {
      name: new RegExp(`A real ${types[type].singular}`),
    });
    if (view === "board")
      for (const status of types[type].statuses)
        expect(
          screen.getAllByRole("option", { name: statusLabelForTest(status) }),
        ).toBeTruthy();
    if (view === "year")
      expect(screen.getByText(/1 entity · 2 completions/)).toBeTruthy();
  },
);
function statusLabelForTest(value: string) {
  return (
    {
      TO_READ: "To read",
      READING: "Reading",
      READ: "Read",
      TO_BE_SEEN: "To be seen",
      SEEN: "Seen",
      TO_PLAY: "To play",
      PLAYING: "Playing",
      FINISHED: "Finished",
    } as Record<string, string>
  )[value];
}
it("creates an exact-title entity without prompting for a completion time", async () => {
  const api = fixture();
  render(<Harness api={api} />);
  fireEvent.click(screen.getByRole("button", { name: "＋ Add book" }));
  const dialog = screen.getByRole("dialog");
  fireEvent.change(within(dialog).getByLabelText("Title"), {
    target: { value: " Exact title " },
  });
  fireEvent.change(within(dialog).getByLabelText("Status"), {
    target: { value: "READ" },
  });
  expect(within(dialog).queryByLabelText(/date/i)).toBeNull();
  fireEvent.click(within(dialog).getByRole("button", { name: "Add" }));
  await waitFor(() =>
    expect(api.create).toHaveBeenCalledWith(
      { type: "BOOK", title: " Exact title ", status: "READ" },
      expect.any(AbortSignal),
    ),
  );
  expect(screen.queryByRole("dialog")).toBeNull();
});
it("keeps manual duplicates explicit rather than silently reusing or retrying", async () => {
  const api = fixture();
  api.create.mockRejectedValue(
    new EntityApiError(
      "Already exists",
      409,
      "DUPLICATE_ENTITY",
      baseEntity.id,
    ),
  );
  render(<Harness api={api} />);
  fireEvent.click(screen.getByRole("button", { name: "＋ Add book" }));
  const dialog = screen.getByRole("dialog");
  fireEvent.change(within(dialog).getByLabelText("Title"), {
    target: { value: "Duplicate" },
  });
  fireEvent.click(within(dialog).getByRole("button", { name: "Add" }));
  await within(dialog).findByRole("alert");
  expect(api.create).toHaveBeenCalledTimes(1);
  fireEvent.click(
    within(dialog).getByRole("button", { name: "Open existing book" }),
  );
  await screen.findByLabelText("Entity details");
  expect(screen.queryByRole("dialog")).toBeNull();
});
it("requires explicit reload after a stale details version and keeps creation time separate", async () => {
  const api = fixture();
  api.update.mockRejectedValueOnce(
    new EntityApiError("Reload before editing", 409, "VERSION_CONFLICT"),
  );
  render(<Harness api={api} />);
  const title = await screen.findByLabelText("Title");
  fireEvent.change(title, { target: { value: "Revised" } });
  fireEvent.click(screen.getByRole("button", { name: "Save" }));
  await screen.findByRole("alert");
  expect(
    (screen.getByRole("button", { name: "Save" }) as HTMLButtonElement)
      .disabled,
  ).toBe(true);
  expect(api.update).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByRole("button", { name: "Reload entity" }));
  await waitFor(() =>
    expect((screen.getByLabelText("Title") as HTMLInputElement).value).toBe(
      "A real book",
    ),
  );
  expect(api.read.mock.calls.length).toBeGreaterThan(1);
});
it("uses exact event versions and UTC timestamps for history corrections and deletion", async () => {
  const api = fixture();
  render(<Harness api={api} />);
  fireEvent.click(
    await screen.findByRole("button", { name: "Edit timestamp" }),
  );
  fireEvent.change(screen.getByLabelText("Correct completed at (UTC)"), {
    target: { value: "2025-12-31T23:59:59.123456" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Save timestamp" }));
  await waitFor(() =>
    expect(api.correct).toHaveBeenCalledWith(
      expect.objectContaining({ version: 0 }),
      "2025-12-31T23:59:59.123456Z",
      expect.any(AbortSignal),
    ),
  );
  fireEvent.click(
    await screen.findByRole("button", { name: "Remove completion" }),
  );
  await waitFor(() =>
    expect(api.deleteCompletion).toHaveBeenCalledWith(
      expect.objectContaining({ version: 0 }),
      expect.any(AbortSignal),
    ),
  );
});
it("records another completion using the current entity version without a time prompt", async () => {
  const api = fixture();
  render(<Harness api={api} />);
  fireEvent.click(
    await screen.findByRole("button", { name: "＋ Add completion" }),
  );
  await waitFor(() =>
    expect(api.complete).toHaveBeenCalledWith(
      expect.objectContaining({ version: 3 }),
      undefined,
      expect.any(AbortSignal),
    ),
  );
  expect(screen.queryByLabelText("Completed at (UTC)")).toBeNull();
});
it("shows the selected year's completion date while keeping entity details' lifetime history", async () => {
  const api = fixture();
  api.list.mockResolvedValue({
    items: [
      {
        ...baseEntity,
        completionCount: 1,
        latestMatchingCompletionAt: "2024-06-15T12:00:00Z",
      },
    ],
    nextCursor: null,
  });
  render(<Harness api={api} route={{ ...initial, year: "2024" }} />);
  const table = await screen.findByRole("table");
  expect(table.textContent).toContain("2024");
  expect(table.textContent).toContain("1 completion");
  await screen.findByLabelText("Title");
  expect(api.read).toHaveBeenCalledWith(baseEntity.id, expect.any(AbortSignal));
});
it("keeps filters while switching views and opens linked timeline entries", async () => {
  const api = fixture();
  api.sources.mockResolvedValue({
    items: [
      {
        id: "00000000-0000-4000-8000-000000000030",
        url: "https://example.test",
        title: "Original source",
        savedAt: baseEntity.createdAt,
        version: 0,
        category: "BOOKS",
        entityName: "A real book",
        hasContent: true,
      },
    ],
    nextCursor: null,
  });
  const opened = vi.fn();
  render(
    <Harness
      api={api}
      route={{ ...initial, query: "real", year: "2026" }}
      onArticle={opened}
    />,
  );
  fireEvent.click(screen.getByRole("button", { name: "Board" }));
  await screen.findByRole("link", { name: "Original source ↗" });
  expect(api.list.mock.lastCall?.[0]).toEqual({
    type: "BOOK",
    query: "real",
    completionYear: 2026,
  });
  fireEvent.click(screen.getByRole("link", { name: "Original source ↗" }));
  expect(opened).toHaveBeenCalledWith("00000000-0000-4000-8000-000000000030");
});
it("ignores an old type's delayed response and preserves type-specific status choices", async () => {
  const api = fixture();
  let resolve!: (page: { items: EntitySummary[]; nextCursor: null }) => void;
  api.list
    .mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    )
    .mockResolvedValueOnce({
      items: [
        {
          ...baseEntity,
          type: "MOVIE",
          title: "A movie",
          status: "SEEN",
          completionCount: 2,
          latestMatchingCompletionAt: baseEntity.lastCompletedAt,
        },
      ],
      nextCursor: null,
    });
  render(<Harness api={api} />);
  fireEvent.click(screen.getByRole("button", { name: "Movies" }));
  await screen.findByRole("button", { name: /A movie/ });
  await act(async () =>
    resolve({
      items: [
        {
          ...baseEntity,
          completionCount: 2,
          latestMatchingCompletionAt: baseEntity.lastCompletedAt,
        },
      ],
      nextCursor: null,
    }),
  );
  expect(screen.queryByRole("button", { name: /A real book/ })).toBeNull();
});
