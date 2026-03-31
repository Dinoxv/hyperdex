import fs from "node:fs";
import path from "node:path";
import http from "node:http";

const rootDir = path.resolve(
  process.cwd(),
  process.env.PLAYWRIGHT_STATIC_ROOT || "resources/public"
);
const port = Number(process.env.PLAYWRIGHT_WEB_PORT || 4173);

const contentTypes = new Map([
  [".css", "text/css; charset=utf-8"],
  [".html", "text/html; charset=utf-8"],
  [".ico", "image/x-icon"],
  [".js", "text/javascript; charset=utf-8"],
  [".json", "application/json; charset=utf-8"],
  [".png", "image/png"],
  [".svg", "image/svg+xml; charset=utf-8"],
  [".ttf", "font/ttf"],
  [".txt", "text/plain; charset=utf-8"],
  [".woff2", "font/woff2"],
  [".xml", "application/xml; charset=utf-8"]
]);

function isPathInsideRoot(candidatePath) {
  return candidatePath === rootDir || candidatePath.startsWith(`${rootDir}${path.sep}`);
}

function tryFile(filePath) {
  try {
    return fs.statSync(filePath).isFile();
  } catch (_error) {
    return false;
  }
}

function resolvePathWithinRoot(relativePath) {
  const candidatePath = path.resolve(rootDir, relativePath);
  if (!isPathInsideRoot(candidatePath)) {
    return null;
  }

  return candidatePath;
}

function resolveRequestPathname(requestUrl) {
  const { pathname } = new URL(requestUrl || "/", "http://127.0.0.1");
  const decodedPath = decodeURIComponent(pathname);
  const normalizedPath = decodedPath.replace(/\/+$/, "") || "/";
  return normalizedPath.startsWith("/") ? normalizedPath : `/${normalizedPath}`;
}

function shouldTryDirectoryIndex(requestPathname) {
  return requestPathname.endsWith("/") || path.extname(requestPathname) === "";
}

function findNearest404Path(requestPathname) {
  const trimmedPath = requestPathname.replace(/^\/+|\/+$/g, "");
  const segments = trimmedPath ? trimmedPath.split("/") : [];
  const directorySegments = shouldTryDirectoryIndex(requestPathname)
    ? segments
    : segments.slice(0, -1);

  for (let index = directorySegments.length; index > 0; index -= 1) {
    const candidatePath = resolvePathWithinRoot(
      path.join(...directorySegments.slice(0, index), "404.html")
    );
    if (candidatePath && tryFile(candidatePath)) {
      return candidatePath;
    }
  }

  return null;
}

function resolveFilePath(requestUrl) {
  const requestPathname = resolveRequestPathname(requestUrl);
  const relativePath = requestPathname === "/" ? "" : requestPathname.replace(/^\/+/, "");
  const exactCandidatePath = resolvePathWithinRoot(relativePath);

  if (!exactCandidatePath) {
    return { statusCode: 403 };
  }

  if (tryFile(exactCandidatePath)) {
    return { filePath: exactCandidatePath, statusCode: 200 };
  }

  if (shouldTryDirectoryIndex(requestPathname)) {
    const directoryIndexPath = resolvePathWithinRoot(path.join(relativePath, "index.html"));
    if (directoryIndexPath && tryFile(directoryIndexPath)) {
      return { filePath: directoryIndexPath, statusCode: 200 };
    }
  }

  const root404Path = resolvePathWithinRoot("404.html");
  if (!root404Path || !tryFile(root404Path)) {
    const spaFallbackPath = resolvePathWithinRoot("index.html");
    if (spaFallbackPath && tryFile(spaFallbackPath)) {
      return { filePath: spaFallbackPath, statusCode: 200 };
    }

    return { statusCode: 404 };
  }

  return {
    filePath: findNearest404Path(requestPathname) || root404Path,
    statusCode: 404
  };
}

function sendNotFound(response) {
  response.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
  response.end("File not found.");
}

function sendFile(response, filePath, statusCode = 200) {
  const extension = path.extname(filePath);
  response.writeHead(statusCode, {
    "Content-Type": contentTypes.get(extension) || "application/octet-stream"
  });
  fs.createReadStream(filePath).pipe(response);
}

const server = http.createServer((request, response) => {
  let resolvedFile;
  try {
    resolvedFile = resolveFilePath(request.url);
  } catch (_error) {
    response.writeHead(400, { "Content-Type": "text/plain; charset=utf-8" });
    response.end("Bad request");
    return;
  }

  if (!resolvedFile) {
    sendNotFound(response);
    return;
  }

  if (!resolvedFile.filePath) {
    if (resolvedFile.statusCode === 403) {
      response.writeHead(403, { "Content-Type": "text/plain; charset=utf-8" });
      response.end("Forbidden");
      return;
    }

    sendNotFound(response);
    return;
  }

  const { filePath, statusCode } = resolvedFile;
  if (!tryFile(filePath)) {
    sendNotFound(response);
    return;
  }

  sendFile(response, filePath, statusCode);
});

function shutdown() {
  server.close(() => process.exit(0));
}

server.listen(port, "127.0.0.1", () => {
  process.stdout.write(
    `Playwright static server listening on http://127.0.0.1:${port} from ${rootDir}\n`
  );
});

process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);
