import { builtinEnvironments } from "vitest/runtime";

// Keep Node's native multipart constructors paired with its native fetch while
// jsdom supplies the DOM for the real React UI. Mixing jsdom File with undici
// fetch otherwise changes the body sent over HTTP from what Firefox sends.
export default {
  ...builtinEnvironments.jsdom,
  async setup(global, options) {
    const native = {
      FormData: global.FormData,
      Blob: global.Blob,
      File: global.File,
    };
    const environment = await builtinEnvironments.jsdom.setup(global, options);
    Object.assign(global, native);
    return environment;
  },
};
