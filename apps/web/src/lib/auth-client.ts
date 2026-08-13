import { adminClient, usernameClient } from "better-auth/client/plugins";
import { createAuthClient } from "better-auth/react";
import { apiUrl } from "./api-url";

export const authClient = createAuthClient({
    baseURL: `${apiUrl}/api/auth`,
    plugins: [usernameClient(), adminClient()],
});
