/** Matches Better Auth's comma-separated role representation. */
export function hasAdminRole(role: string | null | undefined) {
    return role?.split(",").some((value) => value.trim() === "admin") ?? false;
}
