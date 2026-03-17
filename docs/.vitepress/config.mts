import { defineConfig } from "vitepress";

const base = process.env.GITHUB_ACTIONS ? "/Kototoro/" : "/";
const editBranch = process.env.DOCS_EDIT_BRANCH || process.env.GITHUB_REF_NAME || "devel";

export default defineConfig({
  title: "Kototoro Docs",
  description: "Documentation for Kototoro: manga, novels, video, OCR translation, and source integrations.",
  lang: "en-US",
  base,
  ignoreDeadLinks: [/app\/src\//],
  lastUpdated: true,
  cleanUrls: true,
  head: [
    ["link", { rel: "icon", href: `${base}icon.png` }],
    ["meta", { name: "theme-color", content: "#0f766e" }],
  ],
  themeConfig: {
    logo: "/icon.png",
    siteTitle: "Kototoro Docs",
    nav: [
      { text: "Guides", link: "/getting-started" },
      { text: "Reference", link: "/reference/mihon-integration" },
      { text: "Development", link: "/development" },
      { text: "GitHub", link: "https://github.com/skepsun/Kototoro" },
    ],
    search: {
      provider: "local",
    },
    socialLinks: [
      { icon: "github", link: "https://github.com/skepsun/Kototoro" },
    ],
    editLink: {
      pattern: `https://github.com/skepsun/Kototoro/edit/${editBranch}/docs/:path`,
      text: "Edit this page on GitHub",
    },
    sidebar: [
      {
        text: "Guides",
        items: [
          { text: "Getting Started", link: "/getting-started" },
          { text: "Reader Features", link: "/reader-features" },
          { text: "Automatic Translation", link: "/automatic-translation" },
          { text: "Source Integrations", link: "/source-integrations" },
          { text: "WebDAV Sync", link: "/webdav-sync" },
          { text: "FAQ", link: "/faq" },
          { text: "Troubleshooting", link: "/troubleshooting" },
        ],
      },
      {
        text: "Reference",
        items: [
          { text: "Architecture Review", link: "/architecture/architecture-review" },
          { text: "Mihon Integration", link: "/reference/mihon-integration" },
          { text: "TVBox Integration Plan", link: "/architecture/tvbox-integration-implementation-plan" },
          { text: "OCR Pipeline", link: "/architecture/ocr-pipeline-v2" },
          { text: "Extensions Management Design", link: "/architecture/extensions-management-unification" },
          { text: "Extensions Management Plan", link: "/architecture/extensions-management-implementation-plan" },
          { text: "Extensions Management Handoff (2026-03)", link: "/architecture/extensions-management-handoff-2026-03" },
        ],
      },
      {
        text: "Development",
        items: [
          { text: "Development", link: "/development" },
          { text: "Contributing", link: "/contributing" },
        ],
      },
      {
        text: "Archive",
        items: [
          { text: "Archive Overview", link: "/archive/" },
          { text: "OCR Roadmap Review (2026-03)", link: "/archive/ocr-roadmap-review-2026-03" },
          { text: "Archived Mihon Notes (ZH)", link: "/archive/zh/mihon-compatibility" },
        ],
      },
    ],
    footer: {
      message: "Documentation for Kototoro",
      copyright: "Kototoro contributors",
    },
    outline: {
      level: [2, 3],
    },
    docFooter: {
      prev: "Previous page",
      next: "Next page",
    },
  },
});
