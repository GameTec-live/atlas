import { chmod, lstat, mkdir, unlink, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { EMULATOR_TOKEN, managementEmulator } from "../src/system/emulator";

const emulatorEnv = Bun.env as {
    OS_MANAGEMENT_SOCKET?: string;
    OS_MANAGEMENT_TOKEN_FILE?: string;
};
const socketPath = resolve(
    emulatorEnv.OS_MANAGEMENT_SOCKET ?? "data/management-emulator/api.sock",
);
const tokenPath = resolve(
    emulatorEnv.OS_MANAGEMENT_TOKEN_FILE ??
        "data/management-emulator/management-token",
);

await mkdir(dirname(socketPath), { recursive: true });
await mkdir(dirname(tokenPath), { recursive: true });

try {
    const existing = await lstat(socketPath);
    if (!existing.isSocket()) {
        throw new Error(`Refusing to replace non-socket path ${socketPath}`);
    }
    await unlink(socketPath);
} catch (error) {
    if (
        !(error instanceof Error && "code" in error && error.code === "ENOENT")
    ) {
        throw error;
    }
}

await writeFile(tokenPath, `${EMULATOR_TOKEN}\n`, { mode: 0o600 });
await chmod(tokenPath, 0o600);

const server = Bun.serve({
    unix: socketPath,
    fetch: managementEmulator.fetch,
});

console.log(`Atlas management emulator listening on ${socketPath}`);
console.log(`Management token written to ${tokenPath}`);
console.log("Set these variables for the Atlas API:");
console.log(`OS_MANAGEMENT_SOCKET=${socketPath}`);
console.log(`OS_MANAGEMENT_TOKEN_FILE=${tokenPath}`);

const stop = async () => {
    await server.stop();
    await unlink(socketPath).catch(() => undefined);
    process.exit();
};

process.once("SIGINT", stop);
process.once("SIGTERM", stop);
