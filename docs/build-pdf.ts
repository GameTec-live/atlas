import { existsSync } from "node:fs";
import {
  mkdir,
  mkdtemp,
  readFile,
  rm,
  writeFile,
} from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { parseArgs } from "node:util";
import { marked } from "marked";
import { PDFArray, PDFDict, PDFDocument, PDFName, PDFRef } from "pdf-lib";

const root = import.meta.dir;

const manuals = [
  { path: "getting-started.md", language: "en", id: "getting-started-en" },
  { path: "manual-web.md", language: "en", id: "manual-web-en" },
  { path: "manual-mobile.md", language: "en", id: "manual-mobile-en" },
  { path: "manual-recovery.md", language: "en", id: "manual-recovery-en" },
  { path: "de/getting-started.md", language: "de", id: "getting-started-de" },
  { path: "de/manual-web.md", language: "de", id: "manual-web-de" },
  { path: "de/manual-mobile.md", language: "de", id: "manual-mobile-de" },
  { path: "de/manual-recovery.md", language: "de", id: "manual-recovery-de" },
] as const;

type AlertKind = "NOTE" | "TIP" | "IMPORTANT" | "WARNING" | "CAUTION";
type Language = (typeof manuals)[number]["language"];

const alertLabels = {
  en: {
    NOTE: "Note",
    TIP: "Tip",
    IMPORTANT: "Important",
    WARNING: "Warning",
    CAUTION: "Caution",
  },
  de: {
    NOTE: "Hinweis",
    TIP: "Tipp",
    IMPORTANT: "Wichtig",
    WARNING: "Warnung",
    CAUTION: "Vorsicht",
  },
} satisfies Record<Language, Record<AlertKind, string>>;

const alertIcons = {
  NOTE: '<svg viewBox="0 0 16 16" aria-hidden="true"><circle cx="8" cy="8" r="6.25" fill="none" stroke="currentColor" stroke-width="1.5"/><path d="M8 7v4M8 4.75h.01" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>',
  TIP: '<svg viewBox="0 0 16 16" aria-hidden="true"><path d="M5.5 11.25h5M6.25 13.25h3.5M8 2.25a4.25 4.25 0 0 0-2.55 7.65c.45.34.8.75.95 1.1h3.2c.15-.35.5-.76.95-1.1A4.25 4.25 0 0 0 8 2.25Z" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  IMPORTANT: '<svg viewBox="0 0 16 16" aria-hidden="true"><path d="M8 1.75 9.7 6.3l4.55.2-3.55 2.85 1.2 4.4L8 11.2l-3.9 2.55 1.2-4.4L1.75 6.5l4.55-.2L8 1.75Z" fill="none" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/></svg>',
  WARNING: '<svg viewBox="0 0 16 16" aria-hidden="true"><path d="M8 2.1 14.25 13H1.75L8 2.1Z" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round"/><path d="M8 6v3.5M8 11.65h.01" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>',
  CAUTION: '<svg viewBox="0 0 16 16" aria-hidden="true"><path d="M5.2 1.75h5.6L14.25 5.2v5.6l-3.45 3.45H5.2L1.75 10.8V5.2L5.2 1.75Z" fill="none" stroke="currentColor" stroke-width="1.4"/><path d="M8 5v3.5M8 10.75h.01" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>',
} satisfies Record<AlertKind, string>;

const { values } = parseArgs({
  args: Bun.argv.slice(2).filter((argument) => argument !== "--"),
  options: {
    warning: { type: "string" },
    output: { type: "string" },
    browser: { type: "string" },
    "keep-temp": { type: "boolean", default: false },
    help: { type: "boolean", short: "h", default: false },
  },
  strict: true,
});

if (values.help) {
  console.log(`Usage: bun run pdf [options]

Options:
  --warning <path>  Optional warning PDF to prepend
  --output <path>   Combined output PDF
                      Default: ./atlas-manuals-en-de.pdf
  --browser <path>  Edge or Chrome executable
  --keep-temp       Keep intermediate HTML and PDFs for debugging
  -h, --help        Show this help`);
  process.exit(0);
}

const warningPdf = values.warning ? resolve(values.warning) : undefined;
const outputPdf = resolve(
  values.output ?? join(root, "atlas-manuals-en-de.pdf"),
);

const findBrowser = (explicitPath?: string) => {
  const programFiles = process.env.ProgramFiles;
  const programFilesX86 = process.env["ProgramFiles(x86)"];
  const localAppData = process.env.LOCALAPPDATA;
  const candidates = [
    explicitPath,
    process.env.BROWSER_PATH,
    Bun.which("msedge"),
    Bun.which("chrome"),
    programFilesX86 &&
      join(programFilesX86, "Microsoft", "Edge", "Application", "msedge.exe"),
    programFiles &&
      join(programFiles, "Microsoft", "Edge", "Application", "msedge.exe"),
    programFiles &&
      join(programFiles, "Google", "Chrome", "Application", "chrome.exe"),
    localAppData &&
      join(localAppData, "Google", "Chrome", "Application", "chrome.exe"),
  ].filter((candidate): candidate is string => Boolean(candidate));

  return candidates.find(existsSync);
};

const browser = findBrowser(values.browser);

if (!browser) {
  throw new Error(
    "Could not find Microsoft Edge or Google Chrome. Pass its path with --browser.",
  );
}

const assertInputExists = (path: string) => {
  if (!existsSync(path)) {
    throw new Error(`Missing input: ${path}`);
  }
};

if (warningPdf) {
  assertInputExists(warningPdf);
}
manuals.forEach((manual) => assertInputExists(join(root, manual.path)));

const renderAlerts = (html: string, language: Language) =>
  html.replace(
    /<blockquote>\s*<p>\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\](?:<br>)?\s*([\s\S]*?)<\/blockquote>/g,
    (_match, rawKind: string, body: string) => {
      const kind = rawKind as AlertKind;
      const label = alertLabels[language][kind];

      return `<aside class="alert alert-${kind.toLowerCase()}"><div class="alert-title">${alertIcons[kind]}<span>${label}</span></div><div class="alert-body"><p>${body}</div></aside>`;
    },
  );

const manualsByPath = new Map(
  manuals.map((manual) => [resolve(root, manual.path), manual]),
);

// Cross-document Markdown links become same-document anchors in the PDF.
const renderManualLinks = (html: string, sourcePath: string) =>
  html.replace(/href="([^"]+)"/g, (match, href: string) => {
    const path = href.split(/[?#]/, 1)[0];

    if (!path?.toLowerCase().endsWith(".md")) {
      return match;
    }

    const targetPath = fileURLToPath(
      new URL(href, pathToFileURL(sourcePath)),
    );
    const target = manualsByPath.get(targetPath);

    if (!target) {
      throw new Error(
        `Linked Markdown file is not included in the PDF: ${href} from ${sourcePath}`,
      );
    }

    return `href="#${target.id}"`;
  });

const createStyles = (fontBase64: string) => `
@font-face { font-family: "Geist"; font-style: normal; font-weight: 100 900; font-display: block; src: url("data:font/woff2;base64,${fontBase64}") format("woff2-variations"); }
@page { size: A4; margin: 17mm 18mm 18mm; }
* { box-sizing: border-box; }
html { color: #172033; background: white; }
body { margin: 0; font-family: "Geist", Arial, sans-serif; font-size: 10pt; line-height: 1.42; }
.manual + .manual { break-before: page; page-break-before: always; }
h1, h2, h3 { color: #10264b; line-height: 1.18; break-after: avoid-page; page-break-after: avoid; }
h1 { margin: 0 0 14pt; padding-bottom: 6pt; font-size: 24pt; font-weight: 700; border-bottom: 2px solid #2f6fed; }
h2 { margin: 16pt 0 6pt; font-size: 16pt; font-weight: 700; }
h3 { margin: 12pt 0 4pt; font-size: 12.5pt; font-weight: 650; }
p { margin: 0 0 7pt; orphans: 3; widows: 3; }
ul, ol { margin: 4pt 0 8pt; padding-left: 21pt; }
li { margin: 1.5pt 0; }
li > p { margin-bottom: 3pt; }
a { color: #1c5fcc; text-decoration: none; }
code { font-family: Consolas, "Courier New", monospace; font-size: 0.9em; background: #f1f4f8; padding: 1px 3px; border-radius: 3px; }
pre { margin: 7pt 0 9pt; padding: 9pt; overflow-wrap: anywhere; white-space: pre-wrap; background: #f4f6f9; border: 1px solid #d8dee8; border-radius: 5px; break-inside: avoid-page; }
pre code { padding: 0; background: transparent; }
blockquote { margin: 8pt 0; padding: 7pt 10pt; background: #eef4ff; border-left: 4px solid #2f6fed; break-inside: avoid-page; }
blockquote p:last-child { margin-bottom: 0; }
.alert { margin: 8pt 0; padding: 7pt 10pt; border-left: 4px solid; break-inside: avoid-page; page-break-inside: avoid; }
.alert-title { display: flex; align-items: center; gap: 5pt; margin-bottom: 3pt; font-weight: 700; }
.alert-title svg { width: 13pt; height: 13pt; flex: 0 0 13pt; }
.alert-body { color: #172033; }
.alert-body p:last-child { margin-bottom: 0; }
.alert-note { color: #0969da; border-color: #2f81f7; background: #eef5ff; }
.alert-tip { color: #1a7f37; border-color: #2da44e; background: #effaf2; }
.alert-important { color: #8250df; border-color: #8250df; background: #f5f0ff; }
.alert-warning { color: #9a6700; border-color: #bf8700; background: #fff8e6; }
.alert-caution { color: #cf222e; border-color: #cf222e; background: #fff0f0; }
table { width: 100%; margin: 8pt 0 11pt; border-collapse: collapse; font-size: 9.1pt; break-inside: auto; }
thead { display: table-header-group; }
tr { break-inside: avoid-page; page-break-inside: avoid; }
th, td { padding: 5pt 6pt; border: 1px solid #cdd5e1; text-align: left; vertical-align: top; }
th { color: #10264b; background: #e9eef6; font-weight: 700; }
hr { border: 0; border-top: 1px solid #cdd5e1; }
`;

const renderPdf = async (
  htmlPath: string,
  pdfPath: string,
  profilePath: string,
) => {
  const process = Bun.spawn(
    [
      browser,
      "--headless",
      "--disable-gpu",
      "--allow-file-access-from-files",
      "--no-pdf-header-footer",
      `--user-data-dir=${profilePath}`,
      `--print-to-pdf=${pdfPath}`,
      pathToFileURL(htmlPath).href,
    ],
    { stdout: "ignore", stderr: "pipe" },
  );

  const [exitCode, stderr] = await Promise.all([
    process.exited,
    new Response(process.stderr).text(),
  ]);

  if (exitCode !== 0 || !existsSync(pdfPath)) {
    throw new Error(`Browser rendering failed:\n${stderr.trim()}`);
  }
};

const mergePdfs = async (inputs: string[], output: string) => {
  const merged = await PDFDocument.create();
  const destinations = merged.context.obj({});

  for (const input of inputs) {
    const source = await PDFDocument.load(await readFile(input));
    const sourcePages = source.getPages();
    const pages = await merged.copyPages(source, source.getPageIndices());
    pages.forEach((page) => merged.addPage(page));

    const sourceDestinations = source.catalog.lookupMaybe(
      PDFName.of("Dests"),
      PDFDict,
    );

    if (!sourceDestinations) {
      continue;
    }

    const sourcePageIndexes = new Map(
      sourcePages.map((page, index) => [page.ref.toString(), index]),
    );

    for (const [name, rawDestination] of sourceDestinations.entries()) {
      const destination = source.context.lookupMaybe(
        rawDestination,
        PDFArray,
      );
      const sourcePage = destination?.get(0);

      if (!(sourcePage instanceof PDFRef)) {
        continue;
      }

      const pageIndex = sourcePageIndexes.get(sourcePage.toString());

      if (pageIndex === undefined) {
        continue;
      }

      const targetPage = pages[pageIndex];

      if (!targetPage) {
        continue;
      }

      destinations.set(
        PDFName.of(name.decodeText()),
        merged.context.obj([
          targetPage.ref,
          "XYZ",
          0,
          targetPage.getHeight(),
          0,
        ]),
      );
    }
  }

  if (destinations.entries().length > 0) {
    merged.catalog.set(PDFName.of("Dests"), destinations);
  }

  merged.setTitle("Atlas Manuals - English and German");
  merged.setSubject("Atlas beta-test documentation");
  merged.setCreator("Atlas documentation build");

  await writeFile(output, await merged.save());
};

const main = async () => {
  const buildDir = await mkdtemp(join(tmpdir(), "atlas-manuals-"));

  try {
    const fontPath = join(
      root,
      "node_modules",
      "@fontsource-variable",
      "geist",
      "files",
      "geist-latin-wght-normal.woff2",
    );
    assertInputExists(fontPath);

    const styles = createStyles((await readFile(fontPath)).toString("base64"));
    const sections: string[] = [];

    for (const manual of manuals) {
      const sourcePath = join(root, manual.path);
      const markdown = await readFile(sourcePath, "utf8");
      const body = renderManualLinks(
        renderAlerts(
          await marked.parse(markdown, { gfm: true }),
          manual.language,
        ),
        sourcePath,
      );

      sections.push(
        `<section class="manual" id="${manual.id}" lang="${
          manual.language
        }">${body}</section>`,
      );
      console.log(`Prepared ${manual.path}`);
    }

    const htmlPath = join(buildDir, "manuals.html");
    const renderedPdf = join(buildDir, "manuals.pdf");
    const profilePath = join(buildDir, "browser-profile");
    const html = `<!doctype html><html><head><meta charset="utf-8"><style>${styles}</style></head><body>${sections.join(
      "",
    )}</body></html>`;

    await writeFile(htmlPath, html, "utf8");
    await renderPdf(htmlPath, renderedPdf, profilePath);
    console.log("Rendered manuals");

    await mkdir(dirname(outputPdf), { recursive: true });
    await mergePdfs(
      warningPdf ? [warningPdf, renderedPdf] : [renderedPdf],
      outputPdf,
    );
    console.log(`Created ${outputPdf}`);
  } finally {
    if (values["keep-temp"]) {
      console.log(`Kept temporary files in ${buildDir}`);
    } else {
      await rm(buildDir, { recursive: true, force: true });
    }
  }
};

await main();
