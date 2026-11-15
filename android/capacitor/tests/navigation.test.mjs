import assert from "node:assert/strict";
import { test } from "node:test";
import { handleAndroidBack } from "../src/navigation.mjs";

for (const [dialog, history, expected] of [[true, true, "dialog"], [true, false, "dialog"], [false, true, "back"], [false, false, "minimize"]]) {
  test(`Back: dialog=${dialog}, history=${history}`, () => {
    const calls = [];
    handleAndroidBack({ canGoBack: history, dismissDialog: () => { if (dialog) calls.push("dialog"); return dialog; }, goBack: () => calls.push("back"), minimize: () => calls.push("minimize") });
    assert.deepEqual(calls, [expected]);
  });
}
