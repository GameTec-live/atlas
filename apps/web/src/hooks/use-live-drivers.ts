import { useSyncExternalStore } from "react";
import { api } from "@/lib/api-client";

type TrackSocket = ReturnType<typeof api.realtime.track.subscribe>;
type TrackMessage = Parameters<
    Parameters<TrackSocket["subscribe"]>[0]
>[0]["data"];

export type LiveDriver = Extract<TrackMessage, { type: "update" }>;

type Listener = () => void;

const emptyDrivers = new Map<string, LiveDriver>();
const listeners = new Set<Listener>();

let drivers: ReadonlyMap<string, LiveDriver> = emptyDrivers;
let socket: TrackSocket | undefined;
let reconnectTimer: ReturnType<typeof setTimeout> | undefined;
let reconnectDelay = 1_000;

function publish(next: ReadonlyMap<string, LiveDriver>) {
    drivers = next;
    for (const listener of listeners) listener();
}

function handleMessage(message: TrackMessage) {
    const next = new Map(drivers);

    if (message.type === "update") {
        next.set(message.userId, message);
    } else if (message.state === "disconnected") {
        next.delete(message.userId);
    }

    publish(next);
}

function scheduleReconnect() {
    if (reconnectTimer || listeners.size === 0) return;

    reconnectTimer = setTimeout(() => {
        reconnectTimer = undefined;
        connect();
    }, reconnectDelay);
    reconnectDelay = Math.min(reconnectDelay * 2, 30_000);
}

function connect() {
    if (socket || listeners.size === 0) return;

    const connection = api.realtime.track.subscribe();
    socket = connection;

    connection.subscribe(({ data }) => handleMessage(data));
    connection.on("open", () => {
        if (socket !== connection) return;

        reconnectDelay = 1_000;
        // Disconnects may happen while nobody is subscribed, so every new
        // connection starts with a clean view of the live stream.
        publish(emptyDrivers);
    });
    connection.on("close", () => {
        if (socket !== connection) return;

        socket = undefined;
        scheduleReconnect();
    });
    connection.on("error", () => connection.close());
}

function disconnect() {
    clearTimeout(reconnectTimer);
    reconnectTimer = undefined;
    reconnectDelay = 1_000;

    const connection = socket;
    socket = undefined;
    connection?.close();
    drivers = emptyDrivers;
}

function subscribe(listener: Listener) {
    listeners.add(listener);
    connect();

    return () => {
        listeners.delete(listener);
        if (listeners.size === 0) disconnect();
    };
}

function getSnapshot() {
    return drivers;
}

export function useLiveDrivers() {
    return useSyncExternalStore(subscribe, getSnapshot, getSnapshot);
}
