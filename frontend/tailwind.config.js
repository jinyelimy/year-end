/** @type {import('tailwindcss').Config} */
module.exports = {
  darkMode: "class",
  content: [
    "./app/**/*.{js,jsx,ts,tsx,mdx}",
    "./components/**/*.{js,jsx,ts,tsx,mdx}",
    "./lib/**/*.{js,jsx,ts,tsx,mdx}",
    "../backend/src/main/resources/static/**/*.html"
  ],
  theme: {
    extend: {
      colors: {
        primary: "rgb(var(--legacy-color-primary, 79 134 227) / <alpha-value>)",
        secondary: "rgb(var(--legacy-color-secondary, 243 244 246) / <alpha-value>)",
        "background-light": "rgb(var(--legacy-color-background-light, 246 247 248) / <alpha-value>)",
        "background-dark": "rgb(var(--legacy-color-background-dark, 18 24 32) / <alpha-value>)"
      },
      fontFamily: {
        sans: ["Inter", "Noto Sans KR", "sans-serif"],
        display: ["Inter", "Noto Sans KR", "sans-serif"]
      },
      boxShadow: {
        soft: "var(--legacy-shadow-soft, 0 12px 30px -18px rgba(15, 23, 42, 0.20))"
      },
      borderRadius: {
        DEFAULT: "0.25rem",
        lg: "0.5rem",
        xl: "0.75rem",
        full: "9999px"
      }
    }
  },
  plugins: [require("@tailwindcss/forms")]
};
