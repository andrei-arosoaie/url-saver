import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { DeleteEntity } from "./DeleteEntity";
import { EntityApiError } from "./api";
import type { Entity } from "./model";
const entity: Entity = {
  id: "00000000-0000-4000-8000-000000000002",
  type: "BOOK",
  title: "My chosen name",
  status: "TO_READ",
  createdAt: "2026-10-30T00:00:00Z",
  version: 3,
  totalCompletionCount: 0,
  lastCompletedAt: null,
};
beforeEach(() => {
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute("open", "");
  };
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute("open");
  };
});
afterEach(cleanup);
it("requires explicit confirmation and cancellation never deletes", async () => {
  const remove = vi.fn().mockResolvedValue(undefined),
    done = vi.fn();
  render(
    <DeleteEntity
      entity={entity}
      remove={remove}
      onDeleted={done}
      onReload={vi.fn()}
    />,
  );
  fireEvent.click(screen.getByRole("button", { name: "Delete entity" }));
  expect(remove).not.toHaveBeenCalled();
  expect(screen.getByText("My chosen name")).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
  expect(remove).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "Delete entity" }));
  fireEvent.click(screen.getByRole("button", { name: "Delete permanently" }));
  await waitFor(() => expect(done).toHaveBeenCalledTimes(1));
  expect(remove).toHaveBeenCalledWith(entity, expect.any(AbortSignal));
});
it("reloads after version conflict and never retries or reports deletion", async () => {
  const remove = vi
      .fn()
      .mockRejectedValue(
        new EntityApiError(
          "Entity changed. Reload it.",
          409,
          "VERSION_CONFLICT",
        ),
      ),
    done = vi.fn(),
    reload = vi.fn();
  render(
    <DeleteEntity
      entity={entity}
      remove={remove}
      onDeleted={done}
      onReload={reload}
    />,
  );
  fireEvent.click(screen.getByRole("button", { name: "Delete entity" }));
  fireEvent.click(screen.getByRole("button", { name: "Delete permanently" }));
  await screen.findByRole("alert");
  expect(
    screen.queryByRole("button", { name: "Delete permanently" }),
  ).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "Reload entity" }));
  expect(reload).toHaveBeenCalledTimes(1);
  expect(done).not.toHaveBeenCalled();
  expect(remove).toHaveBeenCalledTimes(1);
});
