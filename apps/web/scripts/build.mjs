import { cp, mkdir, rm } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const output = resolve(root, "dist");
await rm(output, { recursive: true, force: true });
await mkdir(output, { recursive: true });
await cp(resolve(root, "src/index.html"), resolve(output, "index.html"));
await cp(resolve(root, "src/styles.css"), resolve(output, "styles.css"));
// main.ts nutzt absichtlich die JavaScript-Teilmenge von TypeScript und braucht keine Laufzeit.
await cp(resolve(root, "src/main.ts"), resolve(output, "main.js"));
console.log(`Web-Build erstellt: ${output}`);
