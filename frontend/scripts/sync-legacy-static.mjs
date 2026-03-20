import fs from "node:fs/promises";
import path from "node:path";

const frontendDir = process.cwd();
const projectRoot = path.resolve(frontendDir, "..");
const legacyStaticDir = path.join(projectRoot, "backend", "src", "main", "resources", "static");
const publicDir = path.join(frontendDir, "public");
const legacyPublicDir = path.join(publicDir, "legacy-static");

async function ensureDir(dirPath) {
  await fs.mkdir(dirPath, { recursive: true });
}

async function syncDirectory(sourceDir) {
  const entries = await fs.readdir(sourceDir, { withFileTypes: true });

  for (const entry of entries) {
    const sourcePath = path.join(sourceDir, entry.name);
    const relativePath = path.relative(legacyStaticDir, sourcePath);
    const targetPath = path.join(legacyPublicDir, relativePath);

    if (entry.isDirectory()) {
      await syncDirectory(sourcePath);
      continue;
    }

    if (path.extname(entry.name).toLowerCase() === ".html") {
      continue;
    }

    await ensureDir(path.dirname(targetPath));
    await fs.copyFile(sourcePath, targetPath);

    if (entry.name === "favicon.ico") {
      await ensureDir(publicDir);
      await fs.copyFile(sourcePath, path.join(publicDir, entry.name));
    }
  }
}

await ensureDir(legacyPublicDir);
await syncDirectory(legacyStaticDir);
