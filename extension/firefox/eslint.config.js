import js from "@eslint/js";
import ts from "typescript-eslint";
export default ts.config(
  { ignores: [".output/**", ".wxt/**", "src/generated/**", "node_modules/**"] },
  js.configs.recommended,
  ...ts.configs.recommended,
  {
    files: ["scripts/**/*.mjs"],
    languageOptions: {
      globals: { URL: "readonly", process: "readonly", console: "readonly" },
    },
  },
);
