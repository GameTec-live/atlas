// Check if infisical is available
if (Bun.which("infisical")) {
    // Check if file already exists
    if (await Bun.file("release-key.jks").exists()) {
        console.log("Signing key already exists. Skipping fetch.");
        process.exit(0);
    }

    const output = Bun.spawnSync(["infisical", "secrets", "get", "signkey", "--plain", "--silent"])
    const base64Key = output.stdout.toString().trim();

    if (base64Key) {
        const keyBuffer = Buffer.from(base64Key, "base64");
        await Bun.write("release-key.jks", keyBuffer);
        console.log("Signing key fetched and saved to release-key.jks");
    }

} else {
    console.error("Infisical CLI is not installed. Cant fetch signing key.");
    process.exit(0);
}

export {};