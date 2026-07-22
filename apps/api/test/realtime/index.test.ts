import {
    afterAll,
    afterEach,
    beforeAll,
    beforeEach,
    describe,
    expect,
    it,
    mock,
} from "bun:test";
import { Elysia } from "elysia";
import { getSessionMock, resetAuthMocks, session } from "../mocks/auth";

const { notify, realtime } = await import("@/src/realtime");

type TrackInputMessage = {
    type: "update";
    latitude: number;
    longitude: number;
    state: "free" | "onTheWay" | "occupied" | "away";
    fuelLevel?: number;
};

const app = new Elysia().use(realtime);
const sockets = new Set<WebSocket>();

let websocketBaseUrl: string;

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

    static connect(path = "/track", authorization = "Bearer test-token") {
        return new Promise<WebSocketClient>((resolve, reject) => {
            const socket = new WebSocket(`${websocketBaseUrl}${path}`, {
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

    websocketBaseUrl = `ws://127.0.0.1:${port}/realtime`;
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
        const client = await WebSocketClient.connect(
            "/track",
            "Bearer socket-token",
        );

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

describe("notify", () => {
    it("returns zero without attempting to publish when no server is available", () => {
        expect(
            notify(null, {
                jobId: "0303a3f7-6ed5-4a89-84f0-bb2f33b892b7",
                from: "Vienna",
            }),
        ).toBe(0);
    });

    it("publishes a JSON-encoded notification to the notify topic and returns the server result", () => {
        const publish = mock(() => 42);
        const server = { publish } as unknown as NonNullable<
            Parameters<typeof notify>[0]
        >;
        const notification = {
            jobId: "9e36962f-b60c-4870-bd39-a8dad8a4025e",
            from: "Vienna Airport",
            to: "Wien Hauptbahnhof",
            note: "Passenger is waiting at gate 3",
        };

        expect(notify(server, notification)).toBe(42);
        expect(publish).toHaveBeenCalledTimes(1);
        expect(publish).toHaveBeenCalledWith(
            "api:ws:notify",
            JSON.stringify(notification),
        );
    });
});

describe("WS /realtime/notify", () => {
    it("rejects a connection without an authenticated session", async () => {
        getSessionMock.mockResolvedValue(null);

        await expect(WebSocketClient.connect("/notify")).rejects.toThrow(
            "WebSocket connection failed",
        );
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(getSessionCalls()[0]?.[0].headers).toBeInstanceOf(Headers);
    });

    it("authenticates the WebSocket upgrade using its request headers", async () => {
        const client = await WebSocketClient.connect(
            "/notify",
            "Bearer notify-token",
        );

        expect(client.socket.readyState).toBe(WebSocket.OPEN);
        expect(getSessionMock).toHaveBeenCalledTimes(1);
        expect(getSessionCalls()[0]?.[0].headers.get("authorization")).toBe(
            "Bearer notify-token",
        );
    });

    it("delivers notifications with required and optional fields to every subscriber", async () => {
        const firstClient = await WebSocketClient.connect("/notify");
        const secondClient = await WebSocketClient.connect("/notify");
        const notifications = [
            {
                jobId: "fc719807-1ab6-4441-8bd6-1f5f480175aa",
                from: "Vienna Airport",
            },
            {
                jobId: "f9fbd240-5625-4fc7-a552-755672ecdb5b",
                from: "Wien Hauptbahnhof",
                to: "Schwechat",
                note: "Meet at platform 8 🚕",
            },
        ];

        await firstClient.expectNoMessage();
        await secondClient.expectNoMessage();

        for (const notification of notifications) {
            notify(app.server, notification);

            expect(await firstClient.nextMessage()).toEqual(notification);
            expect(await secondClient.nextMessage()).toEqual(notification);
        }
    });

    it("keeps notification broadcasts isolated from tracking subscribers", async () => {
        const trackingClient = await WebSocketClient.connect("/track");
        const notifyClient = await WebSocketClient.connect("/notify");
        const notification = {
            jobId: "da420672-0497-434c-9e39-219de58a0bed",
            from: "Dispatch center",
            note: "New assignment",
        };

        notify(app.server, notification);

        expect(await notifyClient.nextMessage()).toEqual(notification);
        await trackingClient.expectNoMessage();
    });

    it("does not let clients broadcast notifications to other subscribers", async () => {
        const sender = await WebSocketClient.connect("/notify");
        const receiver = await WebSocketClient.connect("/notify");

        sender.send({
            jobId: "08fc1da8-28a5-4388-a116-4bc7e90b2dd1",
            from: "Untrusted client",
        });

        await sender.expectNoMessage();
        await receiver.expectNoMessage();
        expect(sender.socket.readyState).toBe(WebSocket.OPEN);
        expect(receiver.socket.readyState).toBe(WebSocket.OPEN);
    });

    it("continues notifying active subscribers after another subscriber disconnects", async () => {
        const disconnectedClient = await WebSocketClient.connect("/notify");
        const activeClient = await WebSocketClient.connect("/notify");
        const notification = {
            jobId: "084600b9-9f5b-49a6-a1da-e00ca900291f",
            from: "Vienna",
            to: "Graz",
        };

        await disconnectedClient.close();
        notify(app.server, notification);

        expect(await activeClient.nextMessage()).toEqual(notification);
        expect(disconnectedClient.socket.readyState).toBe(WebSocket.CLOSED);
    });
});
