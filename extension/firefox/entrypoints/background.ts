import { browser } from "wxt/browser";
import { ArticleApi } from "../src/api";
import { articleHandler } from "../src/messages";
export default defineBackground(() => {
  const api = new ArticleApi(
    import.meta.env.VITE_API_ORIGIN || "https://url-saver-server.onrender.com",
    async () => {
      const { accessToken } = await browser.storage.session.get("accessToken");
      return typeof accessToken === "string" ? accessToken : undefined;
    },
  );
  browser.runtime.onMessage.addListener(
    articleHandler(api, (id) => browser.tabs.get(id)),
  );
});
