import colors from "tailwindcss/colors";

/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    colors: {
      ...colors,
      brand: {
        50: "rgb(var(--brand-50) / <alpha-value>)",
        100: "rgb(var(--brand-100) / <alpha-value>)",
        500: "rgb(var(--brand-500) / <alpha-value>)",
        600: "rgb(var(--brand-600) / <alpha-value>)",
        700: "rgb(var(--brand-700) / <alpha-value>)",
      },
      accent: {
        50: "rgb(var(--accent-50) / <alpha-value>)",
        100: "rgb(var(--accent-100) / <alpha-value>)",
        200: "rgb(var(--accent-200) / <alpha-value>)",
        300: "rgb(var(--accent-300) / <alpha-value>)",
        400: "rgb(var(--accent-400) / <alpha-value>)",
        500: "rgb(var(--accent-500) / <alpha-value>)",
        600: "rgb(var(--accent-600) / <alpha-value>)",
        700: "rgb(var(--accent-700) / <alpha-value>)",
        800: "rgb(var(--accent-800) / <alpha-value>)",
        900: "rgb(var(--accent-900) / <alpha-value>)",
        950: "rgb(var(--accent-950) / <alpha-value>)",
      },
      emerald: {
        50: "rgb(var(--accent-50) / <alpha-value>)",
        100: "rgb(var(--accent-100) / <alpha-value>)",
        200: "rgb(var(--accent-200) / <alpha-value>)",
        300: "rgb(var(--accent-300) / <alpha-value>)",
        400: "rgb(var(--accent-400) / <alpha-value>)",
        500: "rgb(var(--accent-500) / <alpha-value>)",
        600: "rgb(var(--accent-600) / <alpha-value>)",
        700: "rgb(var(--accent-700) / <alpha-value>)",
        800: "rgb(var(--accent-800) / <alpha-value>)",
        900: "rgb(var(--accent-900) / <alpha-value>)",
        950: "rgb(var(--accent-950) / <alpha-value>)",
      },
    },
    extend: {},
  },
  plugins: [],
};
