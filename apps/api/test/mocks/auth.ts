import { mock } from "bun:test";

export const session = {
    user: {
        id: "user-1",
        name: "Test User",
        email: "test@example.com",
        emailVerified: true,
        image: null,
        createdAt: new Date("2026-01-01T00:00:00.000Z"),
        updatedAt: new Date("2026-01-01T00:00:00.000Z"),
    },
    session: {
        id: "session-1",
        userId: "user-1",
        token: "test-token",
        expiresAt: new Date("2099-01-01T00:00:00.000Z"),
        createdAt: new Date("2026-01-01T00:00:00.000Z"),
        updatedAt: new Date("2026-01-01T00:00:00.000Z"),
        ipAddress: null,
        userAgent: null,
    },
};

export const getSessionMock = mock(
    async (): Promise<typeof session | null> => null,
);

export const resetAuthMocks = () => {
    getSessionMock.mockReset();
    getSessionMock.mockResolvedValue(null);
};

mock.module("@/src/auth", () => ({
    auth: {
        api: { getSession: getSessionMock },
        handler: mock(),
    },
}));
