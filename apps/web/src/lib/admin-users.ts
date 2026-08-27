import { hasAdminRole } from "@/lib/auth";
import { authClient } from "@/lib/auth-client";

export async function listAdminUsers() {
    const response = await authClient.admin.listUsers({
        query: {
            sortBy: "name",
            sortDirection: "asc",
        },
    });
    if (response.error) throw response.error;

    return (response.data?.users ?? []).map((user) => ({
        ...user,
        username:
            "username" in user && typeof user.username === "string"
                ? user.username
                : null,
    }));
}

export type AdminUser = Awaited<ReturnType<typeof listAdminUsers>>[number];

export interface AdminUserInput {
    username: string;
    email: string;
    name: string;
    password: string;
    administrator: boolean;
}

export type SaveAdminUserResult =
    | { status: "saved" }
    | { status: "profile-saved" };

export async function saveAdminUser(
    user: AdminUser | null,
    input: AdminUserInput,
) {
    const username = input.username.trim();
    const email = input.email.trim();
    const name = input.name.trim();

    if (!user) {
        const response = await authClient.admin.createUser({
            email,
            name,
            password: input.password,
            role: input.administrator ? "admin" : "user",
            data: { username, displayUsername: username },
        });
        if (response.error) throw response.error;
        return { status: "saved" } satisfies SaveAdminUserResult;
    }

    const data = {
        ...(username !== user.username ? { username } : {}),
        ...(email !== user.email ? { email } : {}),
        ...(name !== user.name ? { name } : {}),
        ...(input.administrator !== hasAdminRole(user.role)
            ? { role: input.administrator ? "admin" : "user" }
            : {}),
    };
    const profileChanged = Object.keys(data).length > 0;
    if (profileChanged) {
        const response = await authClient.admin.updateUser({
            userId: user.id,
            data,
        });
        if (response.error) throw response.error;
    }

    if (input.password) {
        const response = await authClient.admin.setUserPassword({
            userId: user.id,
            newPassword: input.password,
        });
        if (response.error) {
            if (profileChanged) {
                return {
                    status: "profile-saved",
                } satisfies SaveAdminUserResult;
            }
            throw response.error;
        }
    }

    return { status: "saved" } satisfies SaveAdminUserResult;
}

export async function removeAdminUser(userId: string) {
    const response = await authClient.admin.removeUser({ userId });
    if (response.error) throw response.error;
}
