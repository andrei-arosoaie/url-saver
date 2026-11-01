import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { AuthenticatedApp } from "./AuthenticatedApp";
import { installAuthenticatedFetch } from "./auth";
import "./global.css";
installAuthenticatedFetch();
createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <AuthenticatedApp />
  </StrictMode>,
);
