"use client";

import { useLayoutEffect } from "react";

function syncElementAttributes(element, nextAttributes) {
  const previousClassName = element.className;
  const previousAttributes = {};

  Object.entries(nextAttributes).forEach(([key]) => {
    if (key === "class") {
      return;
    }
    previousAttributes[key] = element.getAttribute(key);
  });

  if (Object.prototype.hasOwnProperty.call(nextAttributes, "class")) {
    element.className = nextAttributes.class || "";
  }

  Object.entries(nextAttributes).forEach(([key, value]) => {
    if (key === "class") {
      return;
    }

    if (value === "" || value === null || value === undefined) {
      element.removeAttribute(key);
      return;
    }

    element.setAttribute(key, value);
  });

  return function restoreAttributes() {
    if (Object.prototype.hasOwnProperty.call(nextAttributes, "class")) {
      element.className = previousClassName;
    }

    Object.entries(previousAttributes).forEach(([key, value]) => {
      if (value === null) {
        element.removeAttribute(key);
        return;
      }

      element.setAttribute(key, value);
    });
  };
}

export function LegacyDocumentAttrs({
  htmlAttributes = {},
  bodyAttributes = {}
}) {
  const htmlAttributesKey = JSON.stringify(htmlAttributes);
  const bodyAttributesKey = JSON.stringify(bodyAttributes);

  useLayoutEffect(() => {
    const restoreHtml = syncElementAttributes(document.documentElement, htmlAttributes);
    const restoreBody = syncElementAttributes(document.body, bodyAttributes);

    return () => {
      restoreBody();
      restoreHtml();
    };
  }, [htmlAttributesKey, bodyAttributesKey]);

  return null;
}
