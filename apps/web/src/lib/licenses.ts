import type { LicenseDocument } from "@/queries/licenses";

const groups = ["web", "api", "mobile", "os", "services", "other"] as const;
export type LicenseGroup = (typeof groups)[number];
export type LicenseProject = LicenseDocument["web"][number] & {
    groups: LicenseGroup[];
};

export function getLicenseProjects(document: LicenseDocument) {
    const projects = new Map<string, LicenseProject>();
    for (const group of groups) {
        for (const project of document[group]) {
            const key = JSON.stringify([
                project.name,
                project.version,
                project.license,
                project.link,
            ]);
            const existing = projects.get(key);
            if (existing) {
                if (!existing.groups.includes(group))
                    existing.groups.push(group);
                existing.notices = [
                    ...new Set([...existing.notices, ...project.notices]),
                ];
            } else projects.set(key, { ...project, groups: [group] });
        }
    }
    return [...projects.values()].sort(
        (a, b) =>
            a.name.localeCompare(b.name) ||
            (a.version ?? "").localeCompare(b.version ?? ""),
    );
}

export function licenseProjectUrl(link: string) {
    const normalized = link
        .replace(/^git\+/, "")
        .replace(
            /^(?:git@github\.com:|ssh:\/\/git@github\.com\/)/,
            "https://github.com/",
        )
        .replace(/^git:\/\//, "https://")
        .replace(/^github:/, "https://github.com/");
    if (/^[\w.-]+\/[\w.-]+$/.test(normalized))
        return `https://github.com/${normalized}`;
    try {
        const url = new URL(normalized);
        return ["http:", "https:"].includes(url.protocol)
            ? url.href
            : undefined;
    } catch {
        return undefined;
    }
}
