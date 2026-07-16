import { fromTypes, openapi } from "@elysia/openapi";
import { Elysia } from "elysia";
import { OpenAPI } from "./auth";
import { authHandler } from "./authHandler";

export const app = new Elysia()
    .use(
        openapi({
            references: fromTypes("src/index.ts", {
                tmpRoot: "./.cache/elysia-openapi",
            }),
            documentation: {
                components: await OpenAPI.components,
                paths: await OpenAPI.getPaths(),
            },
        }),
    )
    .use(authHandler)
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
