import fs from "node:fs";
import path from "node:path";
import http from "node:http";

const rootDir = path.resolve(process.cwd(), "resources/public");
const port = Number(process.env.PLAYWRIGHT_WEB_PORT || 4173);

const contentTypes = new Map([
  [".css", "text/css; charset=utf-8"],
  [".html", "text/html; charset=utf-8"],
  [".ico", "image/x-icon"],
  [".js", "text/javascript; charset=utf-8"],
  [".json", "application/json; charset=utf-8"],
  [".png", "image/png"],
  [".svg", "image/svg+xml; charset=utf-8"]
]);

function resolveFilePath(requestUrl) {
  const { pathname } = new URL(requestUrl || "/", "http://127.0.0.1");
  const decodedPath = decodeURIComponent(pathname);
  const relativePath = decodedPath === "/" ? "index.html" : decodedPath.replace(/^\/+/, "");
  const candidatePath = path.resolve(rootDir, relativePath);

  if (!candidatePath.startsWith(rootDir)) {
    return null;
  }

  return candidatePath;
}

function sendFile(response, filePath, statusCode = 200) {
  const extension = path.extname(filePath);
  response.writeHead(statusCode, {
    "Content-Type": contentTypes.get(extension) || "application/octet-stream"
  });
  fs.createReadStream(filePath).pipe(response);
}

const server = http.createServer((request, response) => {
  const candidatePath = resolveFilePath(request.url);
  if (!candidatePath) {
    response.writeHead(403, { "Content-Type": "text/plain; charset=utf-8" });
    response.end("Forbidden");
    return;
  }

  fs.stat(candidatePath, (error, stats) => {
    if (!error && stats.isFile()) {
      sendFile(response, candidatePath);
      return;
    }

    const fallbackPath = path.join(rootDir, "index.html");
    fs.stat(fallbackPath, (fallbackError, fallbackStats) => {
      if (fallbackError || !fallbackStats.isFile()) {
        response.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
        response.end("File not found.");
        return;
      }

      sendFile(response, fallbackPath);
    });
  });
});

function shutdown() {
  server.close(() => process.exit(0));
}

server.listen(port, "127.0.0.1", () => {
  process.stdout.write(`Playwright static server listening on http://127.0.0.1:${port}\n`);
});

process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);
