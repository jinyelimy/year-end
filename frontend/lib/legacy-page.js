import fs from "node:fs";
import Script from "next/script";

import { LegacyDocumentAttrs } from "@/components/legacy-document-attrs";
import { resolveLegacyStaticPath } from "@/lib/legacy-static";

const SCRIPT_REGEX = /<script\b([^>]*)>([\s\S]*?)<\/script>/gi;
const STYLE_REGEX = /<style\b[^>]*>([\s\S]*?)<\/style>/gi;
const ATTR_REGEX = /([:\w-]+)(?:="([^"]*)")?/g;
const DEFAULT_THEME = {
  primary: "#4f86e3",
  secondary: "#f3f4f6",
  backgroundLight: "#f6f7f8",
  backgroundDark: "#121820",
  shadowSoft: "0 12px 30px -18px rgba(15, 23, 42, 0.20)"
};
const ROUTE_ALIASES = {
  "/index.html": "/",
  "/simplified-data.html": "/import-data",
  "/evidence.html": "/evidence-docs",
  "/js/app.js": "/legacy-static/js/app.js"
};

function toNextRoute(target) {
  if (!target) return target;
  if (ROUTE_ALIASES[target]) {
    return ROUTE_ALIASES[target];
  }
  return target.replace(/\/([a-z-]+)\.html$/, "/$1");
}

function normalizeMarkup(html) {
  return html
    .replace(/href="(\/[a-z-]+\.html)"/g, (_, target) => `href="${toNextRoute(target)}"`)
    .replace(/window\.location\.href = "(\/[a-z-]+\.html)"/g, (_, target) => `window.location.href = "${toNextRoute(target)}"`)
    .replace(/window\.location\.replace\("(\/[a-z-]+\.html)"\)/g, (_, target) => `window.location.replace("${toNextRoute(target)}")`);
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function extractConfigValue(content, key) {
  const pattern = new RegExp(`["']?${escapeRegExp(key)}["']?\\s*:\\s*"([^"]+)"`, "i");
  const match = content.match(pattern);
  return match ? match[1] : null;
}

function hexToRgbChannels(hex) {
  if (!hex) {
    return null;
  }

  const normalized = hex.replace("#", "");
  const value = normalized.length === 3
    ? normalized.split("").map((char) => char + char).join("")
    : normalized;

  if (!/^[0-9a-f]{6}$/i.test(value)) {
    return null;
  }

  const red = Number.parseInt(value.slice(0, 2), 16);
  const green = Number.parseInt(value.slice(2, 4), 16);
  const blue = Number.parseInt(value.slice(4, 6), 16);

  return `${red} ${green} ${blue}`;
}

function extractTheme(content) {
  return {
    primary: extractConfigValue(content, "primary") || DEFAULT_THEME.primary,
    secondary: extractConfigValue(content, "secondary") || DEFAULT_THEME.secondary,
    backgroundLight: extractConfigValue(content, "background-light") || DEFAULT_THEME.backgroundLight,
    backgroundDark: extractConfigValue(content, "background-dark") || DEFAULT_THEME.backgroundDark,
    shadowSoft: extractConfigValue(content, "soft") || DEFAULT_THEME.shadowSoft
  };
}

function buildThemeStyle(theme) {
  const primary = hexToRgbChannels(theme.primary) || hexToRgbChannels(DEFAULT_THEME.primary);
  const secondary = hexToRgbChannels(theme.secondary) || hexToRgbChannels(DEFAULT_THEME.secondary);
  const backgroundLight = hexToRgbChannels(theme.backgroundLight) || hexToRgbChannels(DEFAULT_THEME.backgroundLight);
  const backgroundDark = hexToRgbChannels(theme.backgroundDark) || hexToRgbChannels(DEFAULT_THEME.backgroundDark);

  return `
    :root {
      --legacy-color-primary: ${primary};
      --legacy-color-secondary: ${secondary};
      --legacy-color-background-light: ${backgroundLight};
      --legacy-color-background-dark: ${backgroundDark};
      --legacy-shadow-soft: ${theme.shadowSoft || DEFAULT_THEME.shadowSoft};
    }
  `.trim();
}

function resolveScriptStrategy(src, defaultStrategy) {
  if (src === "/js/app.js" || src === "/legacy-static/js/app.js") {
    return "beforeInteractive";
  }

  return defaultStrategy;
}

function parseAttributes(rawAttributes) {
  const attributes = {};
  let match;

  while ((match = ATTR_REGEX.exec(rawAttributes || "")) !== null) {
    const [, key, value = ""] = match;
    attributes[key] = value;
  }

  return attributes;
}

function extractScripts(markup, strategy) {
  const scripts = [];
  const cleanedMarkup = (markup || "").replace(SCRIPT_REGEX, (_, rawAttributes = "", rawContent = "") => {
    const srcMatch = rawAttributes.match(/src="([^"]+)"/i);

    if (srcMatch) {
      if (/cdn\.tailwindcss\.com/i.test(srcMatch[1])) {
        return "";
      }

      const src = toNextRoute(srcMatch[1]);
      scripts.push({
        kind: "external",
        src,
        strategy: resolveScriptStrategy(src, strategy)
      });
      return "";
    }

    const content = normalizeMarkup(rawContent.trim());
    if (content) {
      if (/tailwind\.config\s*=/.test(content)) {
        return "";
      }

      const idMatch = rawAttributes.match(/id="([^"]+)"/i);
      scripts.push({
        kind: "inline",
        content,
        id: idMatch ? idMatch[1] : null,
        strategy
      });
    }

    return "";
  });

  return {
    markup: cleanedMarkup,
    scripts
  };
}

function extractStyles(markup) {
  const styles = [];
  const cleanedMarkup = (markup || "").replace(STYLE_REGEX, (_, css = "") => {
    const trimmedCss = css.trim();
    if (trimmedCss) {
      styles.push(trimmedCss);
    }
    return "";
  });

  return {
    markup: cleanedMarkup,
    styles
  };
}

function parseLegacyHtml(fileName) {
  const filePath = resolveLegacyStaticPath(fileName);
  const html = fs.readFileSync(filePath, "utf8");
  const htmlTagMatch = html.match(/<html\b([^>]*)>/i);
  const headMatch = html.match(/<head[^>]*>([\s\S]*?)<\/head>/i);
  const bodyMatch = html.match(/<body\b([^>]*)>([\s\S]*?)<\/body>/i);
  const rawHeadContent = headMatch ? headMatch[1] : "";
  const rawBodyContent = bodyMatch ? bodyMatch[2] : "";
  const themeStyle = buildThemeStyle(extractTheme(rawHeadContent));
  const { markup: headWithoutStyles, styles } = extractStyles(rawHeadContent);
  const { scripts: headScripts } = extractScripts(headWithoutStyles, "beforeInteractive");
  const { markup: bodyWithoutScripts, scripts: bodyScripts } = extractScripts(rawBodyContent, "afterInteractive");

  return {
    htmlAttributes: parseAttributes(htmlTagMatch ? htmlTagMatch[1] : ""),
    bodyAttributes: parseAttributes(bodyMatch ? bodyMatch[1] : ""),
    bodyContent: normalizeMarkup(bodyWithoutScripts),
    styles: [themeStyle, ...styles],
    headScripts,
    bodyScripts
  };
}

function renderScript(script, fileName, index) {
  const key = `${fileName}-${script.strategy}-${script.kind}-${index}`;

  if (script.kind === "external") {
    return <Script key={key} src={script.src} strategy={script.strategy} />;
  }

  return (
    <Script
      id={script.id || key}
      key={key}
      strategy={script.strategy}
    >
      {`(() => {\n${script.content}\n})();`}
    </Script>
  );
}

export function renderLegacyPage(fileName) {
  const {
    htmlAttributes,
    bodyAttributes,
    bodyContent,
    styles,
    headScripts,
    bodyScripts
  } = parseLegacyHtml(fileName);
  const pageRootClassName = [htmlAttributes.class, bodyAttributes.class].filter(Boolean).join(" ");

  return (
    <>
      <LegacyDocumentAttrs
        htmlAttributes={htmlAttributes}
        bodyAttributes={bodyAttributes}
      />
      {styles.map((css, index) => (
        <style
          key={`${fileName}-style-${index}`}
          dangerouslySetInnerHTML={{ __html: css }}
        />
      ))}
      {headScripts.map((script, index) => renderScript(script, fileName, index))}
      <div
        className={pageRootClassName || undefined}
        dangerouslySetInnerHTML={{ __html: bodyContent }}
        suppressHydrationWarning
      />
      {bodyScripts.map((script, index) =>
        renderScript(script, fileName, index + headScripts.length)
      )}
    </>
  );
}
