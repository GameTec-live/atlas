import { type Context, Elysia } from "elysia";
import { auth } from "./auth";

const betterAuthView = (context: Context) => {
    const BETTER_AUTH_ACCEPT_METHODS = ["POST", "GET"];
    // validate request method
    if (BETTER_AUTH_ACCEPT_METHODS.includes(context.request.method)) {
        return auth.handler(context.request);
    } else {
        context.status(405);
        return;
    }
};

export const authHandler = new Elysia()
    .all("/api/auth/*", betterAuthView, {
        detail: {
            hide: true,
        },
    })
    .macro({
        auth: {
            async resolve({ status, request: { headers } }) {
                const session = await auth.api.getSession({
                    headers,
                });
                if (!session) return status(401);
                return {
                    user: session.user,
                    session: session.session,
                };
            },
            detail: {
                security: [{ bearerAuth: [] }],
            },
        },
    });
