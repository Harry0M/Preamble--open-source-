import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    globals: true,
    environment: "node",
    include: [
      "tests/unit/**/*.test.ts",
      "tests/property/**/*.test.ts",
      "tests/integration/**/*.test.ts",
    ],
    coverage: {
      provider: "v8",
      include: ["src/v2/**/*.ts"],
      exclude: ["src/v2/**/*.d.ts", "src/v2/**/index.ts"],
    },
    testTimeout: 30000,
  },
});
