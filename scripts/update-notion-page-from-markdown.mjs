import fs from "node:fs";
import path from "node:path";

const rootDir = process.cwd();
loadDotEnv(path.join(rootDir, ".env"));

const pageId = requiredArg("--page-id");
const markdownPath = path.resolve(rootDir, requiredArg("--markdown"));
const statusName = optionalArg("--status");
const categoryName = optionalArg("--category");
const completedValue = optionalArg("--completed");
const memoText = optionalArg("--memo");

const notionToken = requiredEnv("NOTION_TOKEN");

async function main() {
  const markdown = fs.readFileSync(markdownPath, "utf8");
  const blocks = markdownToBlocks(markdown);

  const children = await listChildren(pageId);
  for (const child of children) {
    if (!child.archived && !child.in_trash) {
      await notionRequest(`blocks/${child.id}`, { method: "DELETE" });
    }
  }

  await appendChildren(pageId, blocks);

  const properties = {};
  if (statusName) {
    properties["상태"] = { status: { name: statusName } };
  }
  if (categoryName) {
    properties["카테고리"] = { select: { name: categoryName } };
  }
  if (completedValue != null) {
    properties["완료"] = { checkbox: completedValue === "true" };
  }
  if (memoText) {
    properties["메모"] = {
      rich_text: [{ type: "text", text: { content: memoText.slice(0, 2000) } }]
    };
  }

  if (Object.keys(properties).length > 0) {
    await notionRequest(`pages/${pageId}`, {
      method: "PATCH",
      body: { properties }
    });
  }

  console.log(`Updated Notion page ${pageId} with ${blocks.length} blocks.`);
}

function markdownToBlocks(markdown) {
  const lines = markdown.replace(/\r\n/g, "\n").split("\n");
  const blocks = [];
  let paragraphBuffer = [];
  let codeBuffer = [];
  let codeLang = "plain text";
  let inCode = false;

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

  const flushCode = () => {
    if (codeBuffer.length === 0) {
      return;
    }

    blocks.push(codeBlock(codeBuffer.join("\n"), codeLang));
    codeBuffer = [];
  };

  for (const rawLine of lines) {
    const line = rawLine.replace(/\t/g, "    ");
    const trimmed = line.trim();

    if (trimmed.startsWith("```")) {
      flushParagraph();
      if (!inCode) {
        inCode = true;
        codeLang = trimmed.slice(3).trim() || "plain text";
      } else {
        inCode = false;
        flushCode();
      }
      continue;
    }

    if (inCode) {
      codeBuffer.push(line);
      continue;
    }

    if (!trimmed) {
      flushParagraph();
      continue;
    }

    if (trimmed.startsWith("# ")) {
      flushParagraph();
      blocks.push(headingBlock("heading_1", trimmed.slice(2).trim()));
      continue;
    }

    if (trimmed.startsWith("## ")) {
      flushParagraph();
      blocks.push(headingBlock("heading_2", trimmed.slice(3).trim()));
      continue;
    }

    if (trimmed.startsWith("### ")) {
      flushParagraph();
      blocks.push(headingBlock("heading_3", trimmed.slice(4).trim()));
      continue;
    }

    if (trimmed.startsWith("- ")) {
      flushParagraph();
      blocks.push(bulletedListBlock(trimmed.slice(2).trim()));
      continue;
    }

    if (/^\d+\.\s/.test(trimmed)) {
      flushParagraph();
      blocks.push(numberedListBlock(trimmed.replace(/^\d+\.\s/, "").trim()));
      continue;
    }

    paragraphBuffer.push(trimmed);
  }

  flushParagraph();
  flushCode();
  return blocks;
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
      rich_text: richText(text),
      color: "default",
      is_toggleable: false
    }
  };
}

function bulletedListBlock(text) {
  return {
    object: "block",
    type: "bulleted_list_item",
    bulleted_list_item: {
      rich_text: richText(text),
      color: "default"
    }
  };
}

function numberedListBlock(text) {
  return {
    object: "block",
    type: "numbered_list_item",
    numbered_list_item: {
      rich_text: richText(text),
      color: "default"
    }
  };
}

function codeBlock(text, language) {
    return {
        object: "block",
        type: "code",
        code: {
            rich_text: richText(text),
            language: normalizeCodeLanguage(language)
        }
    };
}

function normalizeCodeLanguage(language) {
    const supported = new Set([
        "abap", "abc", "agda", "arduino", "ascii art", "assembly", "bash", "basic", "bnf",
        "c", "c#", "c++", "clojure", "coffeescript", "coq", "css", "dart", "dhall", "diff",
        "docker", "ebnf", "elixir", "elm", "erlang", "f#", "flow", "fortran", "gherkin",
        "glsl", "go", "graphql", "groovy", "haskell", "hcl", "html", "idris", "java",
        "javascript", "json", "julia", "kotlin", "latex", "less", "lisp", "livescript",
        "llvm ir", "lua", "makefile", "markdown", "markup", "matlab", "mathematica",
        "mermaid", "nix", "notion formula", "objective-c", "ocaml", "pascal", "perl",
        "php", "plain text", "powershell", "prolog", "protobuf", "purescript", "python",
        "r", "racket", "reason", "ruby", "rust", "sass", "scala", "scheme", "scss",
        "shell", "smalltalk", "solidity", "sql", "swift", "toml", "typescript", "vb.net",
        "verilog", "vhdl", "visual basic", "webassembly", "xml", "yaml", "java/c/c++/c#"
    ]);

    return supported.has(language) ? language : "plain text";
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
  for (let index = 0; index < text.length; index += maxLength) {
    chunks.push(text.slice(index, index + maxLength));
  }
  return chunks.length > 0 ? chunks : [""];
}

async function listChildren(blockId) {
  const all = [];
  let cursor;

  do {
    const query = cursor ? `?start_cursor=${cursor}` : "?page_size=100";
    const response = await notionRequest(`blocks/${blockId}/children${query}`, {
      method: "GET"
    });

    all.push(...(response.results || []));
    cursor = response.has_more ? response.next_cursor : undefined;
  } while (cursor);

  return all;
}

async function appendChildren(blockId, blocks) {
  for (let index = 0; index < blocks.length; index += 100) {
    const chunk = blocks.slice(index, index + 100);
    await notionRequest(`blocks/${blockId}/children`, {
      method: "PATCH",
      body: { children: chunk }
    });
  }
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
    throw new Error(`Notion API error (${response.status}): ${await response.text()}`);
  }

  return response.status === 204 ? {} : response.json();
}

function loadDotEnv(filePath) {
  if (!fs.existsSync(filePath)) {
    return;
  }

  const raw = fs.readFileSync(filePath, "utf8");
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
}

function requiredEnv(name) {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Missing environment variable: ${name}`);
  }
  return value;
}

function requiredArg(name) {
  const index = process.argv.indexOf(name);
  if (index === -1 || !process.argv[index + 1]) {
    throw new Error(`Missing argument: ${name}`);
  }
  return process.argv[index + 1];
}

function optionalArg(name) {
  const index = process.argv.indexOf(name);
  if (index === -1 || !process.argv[index + 1]) {
    return null;
  }
  return process.argv[index + 1];
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
