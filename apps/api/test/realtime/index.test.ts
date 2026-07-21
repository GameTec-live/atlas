import {
    afterAll,
    afterEach,
    beforeAll,
    beforeEach,
    describe,
    expect,
    it,
} from "bun:test";
import { Elysia } from "elysia";
import { getSessionMock, resetAuthMocks, session } from "../mocks/auth";

const { realtime } = await import("@/src/realtime");

type TrackInputMessage = {
    type: "update";
    latitude: number;
    longitude: number;
    state: "free" | "onTheWay" | "occupied" | "away";
    fuelLevel?: number;
};

const app = new Elysia().use(realtime);
const sockets = new Set<WebSocket>();

let websocketUrl: string;

const getSessionCalls = () =>
    getSessionMock.mock.calls as unknown as Array<[{ headers: Headers }]>;

const sessionFor = (userId: string) => ({
    user: {
        ...session.user,
        id: userId,
        name: `${userId} name`,
        email: `${userId}@example.com`,
    },
    session: {
        ...session.session,
        id: `session-${userId}`,
        userId,
        token: `token-${userId}`,
    },
});

const closeSocket = (socket: WebSocket) =>
    new Promise<void>((resolve) => {
        sockets.delete(socket);

        if (socket.readyState === WebSocket.CLOSED) {
            resolve();
            return;
        }

        const timeout = setTimeout(resolve, 500);
        socket.addEventListener(
            "close",
            () => {
                clearTimeout(timeout);
                resolve();
            },
            { once: true },
        );
        socket.close();
    });

class WebSocketClient {
    readonly socket: WebSocket;
    private readonly messages: unknown[] = [];
    private readonly waiters: Array<(message: unknown) => void> = [];

    private constructor(socket: WebSocket) {
        this.socket = socket;
        this.socket.addEventListener("message", ({ data }) => {
            const message = JSON.parse(String(data)) as unknown;
            const waiter = this.waiters.shift();

            if (waiter) waiter(message);
            else this.messages.push(message);
        });
    }

    static connect(authorization = "Bearer test-token") {
        return new Promise<WebSocketClient>((resolve, reject) => {
            const socket = new WebSocket(websocketUrl, {
                headers: { authorization },
            });
            const client = new WebSocketClient(socket);
            sockets.add(socket);

            const timeout = setTimeout(() => {
                reject(new Error("Timed out while opening WebSocket"));
            }, 1_000);

            socket.addEventListener(
                "open",
                () => {
                    clearTimeout(timeout);
                    resolve(client);
                },
                { once: true },
            );
            socket.addEventListener(
                "error",
                () => {
                    clearTimeout(timeout);
                    reject(new Error("WebSocket connection failed"));
                },
                { once: true },
            );
        });
    }

    send(message: unknown) {
        this.socket.send(
            typeof message === "string" ? message : JSON.stringify(message),
        );
    }

    nextMessage(timeoutMs = 1_000) {
        const queuedMessage = this.messages.shift();
        if (queuedMessage !== undefined) return Promise.resolve(queuedMessage);

        return new Promise<unknown>((resolve, reject) => {
            const waiter = (message: unknown) => {
                clearTimeout(timeout);
                resolve(message);
            };
            const timeout = setTimeout(() => {
                const waiterIndex = this.waiters.indexOf(waiter);
                if (waiterIndex !== -1) this.waiters.splice(waiterIndex, 1);
                reject(
                    new Error("Timed out while waiting for WebSocket message"),
                );
            }, timeoutMs);

            this.waiters.push(waiter);
        });
    }

    async expectNoMessage(durationMs = 75) {
        expect(this.messages).toHaveLength(0);

        const message = await this.nextMessage(durationMs).catch(
            () => undefined,
        );

        expect(message).toBeUndefined();
    }

    close() {
        return closeSocket(this.socket);
    }
}

beforeAll(() => {
    app.listen({ hostname: "127.0.0.1", port: 0 });
    const port = app.server?.port;

    if (!port) throw new Error("Realtime test server did not start");

    websocketUrl = `ws://127.0.0.1:${port}/realtime/track`;
});

beforeEach(() => {
    resetAuthMocks();
    getSessionMock.mockResolvedValue(session);
});

afterEach(async () => {
    await Promise.all([...sockets].map(closeSocket));
});

afterAll(async () => {
    await app.stop(true);
});

describe("WS /realtime/track", () => {
    it("rejects a connection without an authenticated session", async () => {
        getSessionMock.mockResolvedValue(null);

        await expect(WebSocketClient.connect()).rejects.toThrow(
            "WebSocket connection failed",
        );
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(getSessionCalls()[0]?.[0].headers).toBeInstanceOf(Headers);
    });

    it("authenticates the WebSocket upgrade using its request headers", async () => {
        const client = await WebSocketClient.connect("Bearer socket-token");

        expect(client.socket.readyState).toBe(WebSocket.OPEN);
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(getSessionCalls()[0]?.[0].headers.get("authorization")).toBe(
            "Bearer socket-token",
        );
    });

    it("broadcasts connection and disconnection events with the session user identity", async () => {
        getSessionMock
            .mockResolvedValueOnce(sessionFor("driver-1"))
            .mockResolvedValueOnce(sessionFor("driver-2"));

        const firstClient = await WebSocketClient.connect();
        await firstClient.expectNoMessage();

        const secondClient = await WebSocketClient.connect();

        expect(await firstClient.nextMessage()).toEqual({
            type: "connectionChange",
            userId: "driver-2",
            userName: "driver-2 name",
            state: "connected",
        });
        await secondClient.expectNoMessage();

        await secondClient.close();

        expect(await firstClient.nextMessage()).toEqual({
            type: "connectionChange",
            userId: "driver-2",
            userName: "driver-2 name",
            state: "disconnected",
        });
    });

    it("relays every valid tracking state with the authenticated sender's identity", async () => {
        const authenticatedUserId = "driver-1";

        getSessionMock
            .mockResolvedValueOnce(sessionFor(authenticatedUserId))
            .mockResolvedValueOnce(sessionFor("receiver"));

        const sender = await WebSocketClient.connect();
        const receiver = await WebSocketClient.connect();
        await sender.nextMessage();

        const messages = [
            {
                type: "update",
                latitude: 48.2082,
                longitude: 16.3738,
                state: "free",
            },
            {
                type: "update",
                latitude: 48.21,
                longitude: 16.38,
                state: "onTheWay",
                fuelLevel: 78.5,
            },
            {
                type: "update",
                latitude: 48.22,
                longitude: 16.39,
                state: "occupied",
                fuelLevel: 0,
            },
            {
                type: "update",
                latitude: -33.8688,
                longitude: 151.2093,
                state: "away",
            },
        ] satisfies TrackInputMessage[];

        for (const message of messages) {
            sender.send(message);
            expect(await receiver.nextMessage()).toEqual({
                ...message,
                userId: authenticatedUserId,
                userName: `${authenticatedUserId} name`,
            });
        }

        await sender.expectNoMessage();
    });

    it.each([
        ["non-object input", "not-json"],
        [
            "an unsupported message type",
            {
                type: "connectionChange",
                userId: "driver-1",
                state: "connected",
            },
        ],
        [
            "a client-supplied user id",
            {
                type: "update",
                userId: "spoofed-driver",
                latitude: 48.2,
                longitude: 16.3,
                state: "free",
            },
        ],
        [
            "a missing coordinate",
            {
                type: "update",
                userId: "driver-1",
                longitude: 16.3,
                state: "free",
            },
        ],
        [
            "a non-numeric coordinate",
            {
                type: "update",
                userId: "driver-1",
                latitude: "48.2",
                longitude: 16.3,
                state: "free",
            },
        ],
        [
            "an unsupported tracking state",
            {
                type: "update",
                userId: "driver-1",
                latitude: 48.2,
                longitude: 16.3,
                state: "offline",
            },
        ],
        [
            "a non-numeric fuel level",
            {
                type: "update",
                userId: "driver-1",
                latitude: 48.2,
                longitude: 16.3,
                state: "free",
                fuelLevel: "full",
            },
        ],
    ])("rejects %s without broadcasting it", async (_name, invalidMessage) => {
        getSessionMock
            .mockResolvedValueOnce(sessionFor("sender"))
            .mockResolvedValueOnce(sessionFor("receiver"));

        const sender = await WebSocketClient.connect();
        const receiver = await WebSocketClient.connect();
        await sender.nextMessage();

        sender.send(invalidMessage);

        expect(await sender.nextMessage()).toMatchObject({
            type: "validation",
            on: "message",
        });
        await receiver.expectNoMessage();
    });
});
