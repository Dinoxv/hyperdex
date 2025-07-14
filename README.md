# Hyperopen

A ClojureScript application using [Replicant](https://github.com/cjohansen/replicant) for data-driven rendering.

## Development

### Prerequisites

- [Java 11+](https://adoptium.net/)
- [Clojure CLI](https://clojure.org/guides/install_clojure)

### Setup

```bash
# Install dependencies
clojure -P

# Start development server
npm run dev
# or
clj -M:dev -m shadow.cljs.devtools.cli watch app
```

Open http://localhost:8080 in your browser.

### Build

```bash
# Production build
npm run build
# or
clj -M:dev -m shadow.cljs.devtools.cli release app
```

## License

GNU AGPL v3
