import type { Config } from "tailwindcss";

const config: Config = {
  darkMode: "class",
  content: [
    "./app/**/*.{ts,tsx}",
    "./components/**/*.{ts,tsx}",
    "./features/**/*.{ts,tsx}",
  ],
  theme: {
    container: {
      center: true,
      padding: "1.5rem",
      screens: { "2xl": "1440px" },
    },
    extend: {
      colors: {
        background: "#0B1220",
        surface: "#111827",
        card: "#1F2937",
        border: "#374151",
        input: "#374151",
        ring: "#2563EB",
        primary: {
          DEFAULT: "#2563EB",
          foreground: "#F9FAFB",
          hover: "#1D4ED8",
          muted: "#1E3A8A",
        },
        accent: {
          DEFAULT: "#7C3AED",
          foreground: "#F9FAFB",
          hover: "#6D28D9",
        },
        success: {
          DEFAULT: "#10B981",
          foreground: "#052e21",
          muted: "#064E3B",
        },
        warning: {
          DEFAULT: "#F59E0B",
          foreground: "#3d2705",
          muted: "#78350F",
        },
        error: {
          DEFAULT: "#EF4444",
          foreground: "#450a0a",
          muted: "#7F1D1D",
        },
        foreground: "#F9FAFB",
        muted: {
          DEFAULT: "#9CA3AF",
          foreground: "#6B7280",
        },
        secondary: {
          DEFAULT: "#1F2937",
          foreground: "#F9FAFB",
        },
        destructive: {
          DEFAULT: "#EF4444",
          foreground: "#F9FAFB",
        },
        popover: {
          DEFAULT: "#1F2937",
          foreground: "#F9FAFB",
        },
      },
      fontFamily: {
        sans: [
          "Inter",
          "ui-sans-serif",
          "-apple-system",
          "BlinkMacSystemFont",
          "Segoe UI",
          "system-ui",
          "sans-serif",
        ],
        mono: [
          "JetBrains Mono",
          "ui-monospace",
          "SFMono-Regular",
          "Menlo",
          "monospace",
        ],
      },
      fontSize: {
        xs: ["0.75rem", { lineHeight: "1rem" }],
        sm: ["0.8125rem", { lineHeight: "1.25rem" }],
        base: ["0.875rem", { lineHeight: "1.5rem" }],
        lg: ["1rem", { lineHeight: "1.5rem" }],
        xl: ["1.125rem", { lineHeight: "1.75rem" }],
        "2xl": ["1.375rem", { lineHeight: "1.85rem" }],
        "3xl": ["1.75rem", { lineHeight: "2.1rem" }],
      },
      borderRadius: {
        sm: "4px",
        DEFAULT: "6px",
        md: "8px",
        lg: "10px",
        xl: "14px",
      },
      boxShadow: {
        panel: "0 1px 2px 0 rgb(0 0 0 / 0.4)",
        elevated: "0 4px 24px -4px rgb(0 0 0 / 0.5)",
      },
      keyframes: {
        "fade-in": { from: { opacity: "0" }, to: { opacity: "1" } },
        "slide-up": {
          from: { opacity: "0", transform: "translateY(4px)" },
          to: { opacity: "1", transform: "translateY(0)" },
        },
        "accordion-down": {
          from: { height: "0" },
          to: { height: "var(--radix-accordion-content-height)" },
        },
        "accordion-up": {
          from: { height: "var(--radix-accordion-content-height)" },
          to: { height: "0" },
        },
        pulse: {
          "0%, 100%": { opacity: "1" },
          "50%": { opacity: "0.4" },
        },
      },
      animation: {
        "fade-in": "fade-in 0.15s ease-out",
        "slide-up": "slide-up 0.2s ease-out",
        "accordion-down": "accordion-down 0.2s ease-out",
        "accordion-up": "accordion-up 0.2s ease-out",
        "pulse-slow": "pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite",
      },
    },
  },
  plugins: [require("tailwindcss-animate")],
};

export default config;
