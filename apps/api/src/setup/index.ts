import { count } from "drizzle-orm";
import { Elysia, status } from "elysia";
import * as v from "valibot";
import { auth } from "../auth";
import { authHandler } from "../authHandler";
import { getDeploymentCapabilities } from "../capabilities";
import { config } from "../config";
import { db } from "../db";
import { user } from "../db/schema";

const adminSchema = v.object({
    email: v.pipe(v.string(), v.trim(), v.email()),
    username: v.pipe(v.string(), v.trim(), v.minLength(3), v.maxLength(30)),
    password: v.pipe(v.string(), v.minLength(8), v.maxLength(128)),
});

const hasUsers = async () => {
    const [result] = await db.select({ count: count() }).from(user);
    return (result?.count ?? 0) > 0;
};

const getSetupStatus = async () => {
    const capabilities = getDeploymentCapabilities();

    if (config.setup.complete) {
        return {
            complete: true,
            adminRequired: false,
            capabilities,
        } as const;
    }

    return {
        complete: false,
        adminRequired: !(await hasUsers()),
        capabilities,
    } as const;
};

let adminCreationQueue = Promise.resolve();

// Prevent concurrent setup requests from creating multiple initial admins.
const serializeAdminCreation = <TResult>(operation: () => Promise<TResult>) => {
    const result = adminCreationQueue.then(operation, operation);
    adminCreationQueue = result.then(
        () => undefined,
        () => undefined,
    );
    return result;
};

export const setup = new Elysia({ prefix: "/setup", tags: ["setup"] })
    .use(authHandler)
    .get("/", getSetupStatus)
    .post(
        "/admin",
        ({ body, request }) =>
            serializeAdminCreation(async () => {
                const setupStatus = await getSetupStatus();
                if (!setupStatus.adminRequired) {
                    return status(409, "An administrator already exists");
                }

                const createResponse = await auth.api.createUser({
                    body: {
                        email: body.email,
                        password: body.password,
                        name: body.username,
                        role: "admin",
                        data: {
                            username: body.username,
                            displayUsername: body.username,
                        },
                    },
                    asResponse: true,
                });
                if (!createResponse.ok) return createResponse;

                return auth.api.signInUsername({
                    body: {
                        username: body.username,
                        password: body.password,
                    },
                    headers: request.headers,
                    asResponse: true,
                });
            }),
        { body: adminSchema },
    )
    .post(
        "/complete",
        async () => {
            await config.$set("setup", { complete: true }, { write: true });
            return { complete: true } as const;
        },
        { admin: true },
    );
