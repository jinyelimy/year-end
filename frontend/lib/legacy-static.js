import path from "node:path";

export const LEGACY_STATIC_DIR = path.join(
  process.cwd(),
  "..",
  "backend",
  "src",
  "main",
  "resources",
  "static"
);

export function resolveLegacyStaticPath(...segments) {
  return path.join(LEGACY_STATIC_DIR, ...segments);
}
