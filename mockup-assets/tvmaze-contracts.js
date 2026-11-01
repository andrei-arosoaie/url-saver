(() => {
  const dialog = document.getElementById("tv-contract-dialog");
  let trigger;
  const escape = (text) => String(text).replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
  document.querySelectorAll("[data-tv-contract]").forEach((button) => {
    button.addEventListener("click", () => {
      trigger = button;
      const contract = window.KnowPhase8Contracts[Number(button.dataset.tvContract)];
      document.getElementById("tv-contract-title").textContent = contract.title;
      document.getElementById("tv-contract-body").innerHTML = [
        ["Endpoint", `${contract.method} ${contract.path}`],
        ["Request", contract.request],
        ["Response", contract.response],
        ["Behavior", contract.rules],
        ["Errors", contract.errors],
      ].map(([label, text]) => `<h3>${label}</h3><pre>${escape(text)}</pre>`).join("");
      dialog.showModal();
    });
  });
  document.getElementById("tv-contract-close").addEventListener("click", () => dialog.close());
  dialog.addEventListener("close", () => trigger?.focus());
})();
