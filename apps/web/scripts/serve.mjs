import { createReadStream, statSync } from "node:fs";
import { createServer } from "node:http";
import { extname, join, normalize } from "node:path";

const root = new URL("../dist/", import.meta.url).pathname;
const port = Number(process.env.PORT || 4173);
const typen = { ".html": "text/html", ".js": "text/javascript", ".css": "text/css" };

createServer((request, response) => {
  const anfragepfad = request.url === "/" ? "/index.html" : request.url;
  const datei = normalize(join(root, anfragepfad));
  if (!datei.startsWith(root)) {
    response.writeHead(403).end();
    return;
  }
  try {
    statSync(datei);
    response.writeHead(200, { "Content-Type": typen[extname(datei)] || "application/octet-stream" });
    createReadStream(datei).pipe(response);
  } catch {
    response.writeHead(404).end("Nicht gefunden");
  }
}).listen(port, "127.0.0.1", () => console.log(`Webclient: http://127.0.0.1:${port}`));
