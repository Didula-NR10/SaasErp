# Hi.Ch ERP

A React and Vite ERP frontend with role-based navigation, branch selection,
inventory and sales pages, IMEI lookup, stock receiving, reports, and a
point-of-sale till. The UI uses a shared Hi.Ch design system with responsive
navigation, tables, dialogs, and empty states.

## Run locally

```sh
npm install
npm run dev
```

## Project structure

- `src/App.jsx` manages navigation, role access, search, dialogs, and branch state.
- `src/components/` contains the sidebar, topbar, reusable UI, ERP views, dialog, and POS.
- `src/data.js` contains navigation permissions and the data currently available to this frontend.
- `src/App.css` contains the design tokens, layout, and responsive styles.
