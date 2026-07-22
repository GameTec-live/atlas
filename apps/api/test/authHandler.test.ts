import { beforeEach, describe, expect, it } from "bun:test";
import { Elysia } from "elysia";
import { getSessionMock, resetAuthMocks, session } from "./mocks/auth";

const { authHandler } = await import("@/src/authHandler");

const app = new Elysia()
    .use(authHandler)
    .get(
        "/authenticated",
        ({ user, session }) => ({ userId: user.id, sessionId: session.id }),
        { auth: true },
    )
    .get(
        "/admin",
        ({ user, session }) => ({ userId: user.id, sessionId: session.id }),
        { admin: true },
    );

const sessionWithRole = (role: string) => ({
    ...session,
    user: {
        ...session.user,
        role,
    },
});

beforeEach(() => {
    resetAuthMocks();
});

describe("authHandler macros", () => {
    it("keeps auth routes available to authenticated non-admin users", async () => {
        getSessionMock.mockResolvedValue(sessionWithRole("user"));

        const response = await app.handle(
            new Request("http://localhost/authenticated"),
        );

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            userId: session.user.id,
            sessionId: session.session.id,
        });
    });

    it("returns 401 from admin routes when there is no session", async () => {
        const response = await app.handle(
            new Request("http://localhost/admin"),
        );

        expect(response.status).toBe(401);
    });

    it("returns 403 from admin routes for a non-admin user", async () => {
        getSessionMock.mockResolvedValue(sessionWithRole("user"));

        const response = await app.handle(
            new Request("http://localhost/admin"),
        );

        expect(response.status).toBe(403);
    });

    it.each([
        "admin",
        "user,admin",
    ])("allows an authenticated user with the %s role through admin routes", async (role) => {
        getSessionMock.mockResolvedValue(sessionWithRole(role));

        const response = await app.handle(
            new Request("http://localhost/admin"),
        );

        expect(response.status).toBe(200);
        expect(await response.json()).toEqual({
            userId: session.user.id,
            sessionId: session.session.id,
        });
    });
});
