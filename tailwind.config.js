/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        // Primary palette
        tomato: {
          DEFAULT: '#fe4a49',
          50: '#fff1f1',
          100: '#ffe1e1',
          200: '#ffc7c7',
          300: '#ffa3a3',
          400: '#fe6b6b',
          500: '#fe4a49',
          600: '#eb2524',
          700: '#c61a1a',
          800: '#a31919',
          900: '#861c1c',
        },
        mustard: {
          DEFAULT: '#fed766',
          50: '#fefce8',
          100: '#fef9c3',
          200: '#fef08a',
          300: '#fed766',
          400: '#facc15',
          500: '#eab308',
          600: '#ca8a04',
          700: '#a16207',
          800: '#854d0e',
          900: '#713f12',
        },
        pacific: {
          DEFAULT: '#009fb7',
          50: '#ecfeff',
          100: '#cffafe',
          200: '#a5f3fc',
          300: '#67e8f9',
          400: '#22d3ee',
          500: '#009fb7',
          600: '#0284a8',
          700: '#0e7490',
          800: '#155e75',
          900: '#164e63',
        },
        alabaster: {
          DEFAULT: '#e6e6ea',
          50: '#f8f8f9',
          100: '#f4f4f8',
          200: '#e6e6ea',
          300: '#d1d1d7',
          400: '#a8a8b3',
          500: '#8a8a97',
          600: '#6b6b7a',
          700: '#565663',
          800: '#3f3f4a',
          900: '#2a2a32',
        },
        ghost: {
          DEFAULT: '#f4f4f8',
          white: '#f4f4f8',
        },
        // Keep primary as alias to tomato for existing code compatibility
        primary: {
          50: '#fff1f1',
          100: '#ffe1e1',
          200: '#ffc7c7',
          300: '#ffa3a3',
          400: '#fe6b6b',
          500: '#fe4a49',
          600: '#fe4a49',
          700: '#eb2524',
          800: '#c61a1a',
          900: '#a31919',
          950: '#861c1c',
        }
      }
    },
  },
  plugins: [],
}
