import { paraglideVitePlugin } from "@inlang/paraglide-js";
import tailwindcss from "@tailwindcss/vite";
import { devtools } from "@tanstack/devtools-vite";

import { tanstackRouter } from "@tanstack/router-plugin/vite";

import viteReact from "@vitejs/plugin-react";
import { defineConfig } from "vite";

const config = defineConfig({
    resolve: { tsconfigPaths: true },
    server: {
        port: 3001,
        proxy: {
            "/api": {
                target: "http://localhost:3000",
                ws: true,
                rewrite: (path) => path.replace(/^\/api/, ""),
            },
            "/map": {
                target: "https://atlas.gametec-live.com",
                changeOrigin: true,
                secure: false,
            },
        },
    },
    plugins: [
        paraglideVitePlugin({
            project: "./project.inlang",
            outdir: "./src/paraglide",
            emitTsDeclarations: true,
        }),
        devtools({
            injectSource: {
                enabled: true,
                // react-map-gl forwards unknown props into MapLibre style specs.
                // Source-location attributes are invalid on sources and layers.
                ignore: { components: ["Source", "Layer"] },
            },
        }),
        tailwindcss(),
        tanstackRouter({ target: "react", autoCodeSplitting: true }),
        viteReact(),
    ],
});

export default config;
