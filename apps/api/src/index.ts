import { resolve } from "node:path";
import { type ElysiaOpenAPIConfig, fromTypes, openapi } from "@elysia/openapi";
import { Elysia } from "elysia";
import { OpenAPI } from "./auth";
import { authHandler } from "./authHandler";
import { authed } from "./protected";

type ScalarConfiguration = Partial<
    NonNullable<ElysiaOpenAPIConfig["scalar"]>
> & {
    // Agent is a valid key, Elysia just doesnt pass it through
    agent?: {
        key?: string;
        disabled?: boolean;
        hideAddApi?: boolean;
    };
};

const scalar = {
    defaultOpenFirstTag: false,
    mcp: {
        disabled: true,
    },
    agent: {
        disabled: true,
    },
    hideClientButton: true,
    showDeveloperTools: "never",
} satisfies ScalarConfiguration;

export const app = new Elysia()
    .use(
        openapi({
            references: fromTypes("src/index.ts", {
                tmpRoot: resolve(".cache/elysia-openapi"),
            }),
            documentation: {
                components: await OpenAPI.components,
                paths: await OpenAPI.getPaths(),
                info: {
                    title: "Atlas API",
                    description: "The backend API for Atlas",
                    license: {
                        name: "AGPLV3",
                        url: "https://www.gnu.org/licenses/agpl-3.0.txt",
                    },
                    version: "0.0.0",
                },
            },
            // Scalar supports agent but elysia doesnt pass it through
            scalar: scalar as unknown as NonNullable<
                ElysiaOpenAPIConfig["scalar"]
            >,
        }),
    )
    .use(authHandler)
    .use(authed)
    .get("/", () => "Atlas API")
    .listen(3000);

const banner = `
       d8888 888    888
      d88888 888    888
     d88P888 888    888
    d88P 888 888888 888  8888b.  .d8888b
   d88P  888 888    888     "88b 88K
  d88P   888 888    888 .d888888 "Y8888b.
 d8888888888 Y88b.  888 888  888      X88
d88P     888  "Y888 888 "Y888888  88888P' 
`;

console.log(`Thank you for trusting\n${banner}`);
console.log(
    `🌐 Atlas API is running at http://${app.server?.hostname}:${app.server?.port}`,
);
