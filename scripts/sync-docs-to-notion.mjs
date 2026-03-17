import fs from "node:fs/promises";
import fsSync from "node:fs";
import path from "node:path";
import crypto from "node:crypto";

const rootDir = process.cwd();
const envPath = path.join(rootDir, ".env");
const statePath = path.join(rootDir, ".notion-sync-state.json");

loadDotEnv(envPath);

const notionToken = requiredEnv("NOTION_TOKEN");
const databaseId = requiredEnv("NOTION_DATABASE_ID");
const docsDir = path.join(rootDir, process.env.NOTION_SYNC_DOCS_DIR || "docs");

async function main() {
  const files = await collectMarkdownFiles(docsDir);
  if (files.length === 0) {
    console.log("No markdown files found in docs directory.");
    return;
  }

  const state = await readState();
  const schema = await getDatabaseSchema(databaseId);
  const titleProperty = resolveTitleProperty(schema);
  const sourcePathProperty = resolveSourcePathProperty(schema);
  validateSchema(schema, titleProperty, sourcePathProperty);

  let syncedCount = 0;

  for (const filePath of files) {
    const content = await fs.readFile(filePath, "utf8");
    const relativePath = toPosix(path.relative(rootDir, filePath));
    const hash = sha256(content);

    if (state[relativePath]?.hash === hash) {
      console.log(`Skip unchanged file: ${relativePath}`);
      continue;
    }

    const title = extractTitle(relativePath, content);
    const blocks = markdownToBlocks(content);
    const existingPageId = await findPageIdBySourcePath(databaseId, sourcePathProperty, relativePath);

    if (existingPageId) {
      await replacePageContent(existingPageId, blocks);
      await updatePageProperties(existingPageId, titleProperty, sourcePathProperty, title, relativePath);
      console.log(`Updated page: ${relativePath}`);
      state[relativePath] = { hash, pageId: existingPageId };
    } else {
      const createdPageId = await createPage(databaseId, titleProperty, sourcePathProperty, title, relativePath, blocks);
      console.log(`Created page: ${relativePath}`);
      state[relativePath] = { hash, pageId: createdPageId };
    }

    syncedCount += 1;
  }

  await fs.writeFile(statePath, JSON.stringify(state, null, 2), "utf8");
  console.log(`Done. Synced ${syncedCount} file(s).`);
}

async function collectMarkdownFiles(dir) {
  const entries = await fs.readdir(dir, { withFileTypes: true }).catch(() => []);
  const files = [];

  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...(await collectMarkdownFiles(fullPath)));
      continue;
    }

    if (entry.isFile() && entry.name.endsWith(".md")) {
      files.push(fullPath);
    }
  }

  return files.sort();
}

async function readState() {
  try {
    const raw = await fs.readFile(statePath, "utf8");
    return JSON.parse(raw);
  } catch {
    return {};
  }
}

async function getDatabaseSchema(id) {
  return notionRequest(`databases/${id}`, { method: "GET" });
}

function validateSchema(schema, titleProp, sourceProp) {
  const titlePropertySchema = schema.properties?.[titleProp];
  const sourcePropertySchema = schema.properties?.[sourceProp];

  if (!titlePropertySchema || titlePropertySchema.type !== "title") {
    throw new Error(`Notion database must contain a title property named "${titleProp}".`);
  }

  if (!sourcePropertySchema || sourcePropertySchema.type !== "rich_text") {
    throw new Error(`Notion database must contain a rich_text property named "${sourceProp}".`);
  }
}

function resolveTitleProperty(schema) {
  const configuredTitleProperty = process.env.NOTION_TITLE_PROPERTY;
  if (configuredTitleProperty && schema.properties?.[configuredTitleProperty]?.type === "title") {
    return configuredTitleProperty;
  }

  const titleEntry = Object.entries(schema.properties || {}).find(([, property]) => property.type === "title");
  if (!titleEntry) {
    throw new Error("Notion database must contain a title property.");
  }

  return titleEntry[0];
}

function resolveSourcePathProperty(schema) {
  const configuredSourcePathProperty = process.env.NOTION_SOURCE_PATH_PROPERTY;
  if (configuredSourcePathProperty && schema.properties?.[configuredSourcePathProperty]?.type === "rich_text") {
    return configuredSourcePathProperty;
  }

  if (schema.properties?.["Source Path"]?.type === "rich_text") {
    return "Source Path";
  }

  return configuredSourcePathProperty || "Source Path";
}

async function findPageIdBySourcePath(databaseIdValue, sourceProp, relativePath) {
  const response = await notionRequest(`databases/${databaseIdValue}/query`, {
    method: "POST",
    body: {
      filter: {
        property: sourceProp,
        rich_text: {
          equals: relativePath
        }
      },
      page_size: 1
    }
  });

  return response.results?.[0]?.id;
}

async function createPage(databaseIdValue, titleProp, sourceProp, title, relativePath, blocks) {
  const response = await notionRequest("pages", {
    method: "POST",
    body: {
      parent: {
        database_id: databaseIdValue
      },
      properties: buildProperties(titleProp, sourceProp, title, relativePath),
      children: blocks.slice(0, 100)
    }
  });

  const pageId = response.id;
  const remaining = blocks.slice(100);
  if (remaining.length > 0) {
    await appendChildren(pageId, remaining);
  }
  return pageId;
}

async function updatePageProperties(pageId, titleProp, sourceProp, title, relativePath) {
  await notionRequest(`pages/${pageId}`, {
    method: "PATCH",
    body: {
      properties: buildProperties(titleProp, sourceProp, title, relativePath)
    }
  });
}

async function replacePageContent(pageId, blocks) {
  const children = await listBlockChildren(pageId);
  for (const child of children) {
    if (child.archived || child.in_trash) {
      continue;
    }

    await notionRequest(`blocks/${child.id}`, {
      method: "DELETE"
    });
  }

  await appendChildren(pageId, blocks);
}

async function listBlockChildren(blockId) {
  const all = [];
  let cursor = undefined;

  do {
    const query = cursor ? `?start_cursor=${cursor}` : "";
    const response = await notionRequest(`blocks/${blockId}/children${query}`, {
      method: "GET"
    });

    all.push(...(response.results || []));
    cursor = response.has_more ? response.next_cursor : undefined;
  } while (cursor);

  return all;
}

async function appendChildren(blockId, blocks) {
  for (let i = 0; i < blocks.length; i += 100) {
    const chunk = blocks.slice(i, i + 100);
    await notionRequest(`blocks/${blockId}/children`, {
      method: "PATCH",
      body: {
        children: chunk
      }
    });
  }
}

function buildProperties(titleProp, sourceProp, title, relativePath) {
  return {
    [titleProp]: {
      title: [{ text: { content: title.slice(0, 2000) } }]
    },
    [sourceProp]: {
      rich_text: [{ text: { content: relativePath.slice(0, 2000) } }]
    }
  };
}

function extractTitle(relativePath, content) {
  const firstHeading = content
    .split(/\r?\n/)
    .map((line) => line.trim())
    .find((line) => line.startsWith("# "));

  if (firstHeading) {
    return firstHeading.replace(/^#\s+/, "").trim();
  }

  return path.basename(relativePath, ".md");
}

function markdownToBlocks(markdown) {
  const lines = markdown.replace(/\r\n/g, "\n").split("\n");
  const blocks = [];
  let paragraphBuffer = [];

  const flushParagraph = () => {
    if (paragraphBuffer.length === 0) {
      return;
    }

    const text = paragraphBuffer.join(" ").trim();
    if (text) {
      blocks.push(paragraphBlock(text));
    }
    paragraphBuffer = [];
  };

  for (const rawLine of lines) {
    const line = rawLine.trimEnd();

    if (line.trim() === "") {
      flushParagraph();
      continue;
    }

    if (line.startsWith("```")) {
      flushParagraph();
      continue;
    }

    if (line.startsWith("# ")) {
      flushParagraph();
      blocks.push(headingBlock("heading_1", line.slice(2).trim()));
      continue;
    }

    if (line.startsWith("## ")) {
      flushParagraph();
      blocks.push(headingBlock("heading_2", line.slice(3).trim()));
      continue;
    }

    if (line.startsWith("### ")) {
      flushParagraph();
      blocks.push(headingBlock("heading_3", line.slice(4).trim()));
      continue;
    }

    if (line.startsWith("- ")) {
      flushParagraph();
      blocks.push(bulletedListBlock(line.slice(2).trim()));
      continue;
    }

    if (/^\d+\.\s/.test(line)) {
      flushParagraph();
      blocks.push(numberedListBlock(line.replace(/^\d+\.\s/, "").trim()));
      continue;
    }

    if (line.startsWith("|") && line.endsWith("|")) {
      flushParagraph();
      blocks.push(paragraphBlock(line));
      continue;
    }

    paragraphBuffer.push(line.trim());
  }

  flushParagraph();
  return blocks.slice(0, 1000);
}

function paragraphBlock(text) {
  return {
    object: "block",
    type: "paragraph",
    paragraph: {
      rich_text: richText(text)
    }
  };
}

function headingBlock(type, text) {
  return {
    object: "block",
    type,
    [type]: {
      rich_text: richText(text)
    }
  };
}

function bulletedListBlock(text) {
  return {
    object: "block",
    type: "bulleted_list_item",
    bulleted_list_item: {
      rich_text: richText(text)
    }
  };
}

function numberedListBlock(text) {
  return {
    object: "block",
    type: "numbered_list_item",
    numbered_list_item: {
      rich_text: richText(text)
    }
  };
}

function richText(text) {
  return splitText(text, 1800).map((chunk) => ({
    type: "text",
    text: {
      content: chunk
    }
  }));
}

function splitText(text, maxLength) {
  const chunks = [];
  for (let i = 0; i < text.length; i += maxLength) {
    chunks.push(text.slice(i, i + maxLength));
  }
  return chunks.length > 0 ? chunks : [""];
}

async function notionRequest(endpoint, { method, body }) {
  const response = await fetch(`https://api.notion.com/v1/${endpoint}`, {
    method,
    headers: {
      Authorization: `Bearer ${notionToken}`,
      "Content-Type": "application/json",
      "Notion-Version": "2022-06-28"
    },
    body: body ? JSON.stringify(body) : undefined
  });

  if (!response.ok) {
    const errorBody = await response.text();
    throw new Error(`Notion API error (${response.status}): ${errorBody}`);
  }

  return response.status === 204 ? {} : response.json();
}

function sha256(value) {
  return crypto.createHash("sha256").update(value, "utf8").digest("hex");
}

function toPosix(value) {
  return value.split(path.sep).join("/");
}

function requiredEnv(name) {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Missing environment variable: ${name}`);
  }
  return value;
}

function loadDotEnv(filePath) {
  try {
    const raw = fsSync.readFileSync(filePath, "utf8");
    for (const line of raw.split(/\r?\n/)) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith("#")) {
        continue;
      }

      const separatorIndex = trimmed.indexOf("=");
      if (separatorIndex === -1) {
        continue;
      }

      const key = trimmed.slice(0, separatorIndex).trim();
      const value = trimmed.slice(separatorIndex + 1).trim();
      if (!process.env[key]) {
        process.env[key] = value;
      }
    }
  } catch {
    return;
  }
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
