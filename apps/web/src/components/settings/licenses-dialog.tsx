import { useQuery } from "@tanstack/react-query";
import { ExternalLinkIcon, HeartHandshakeIcon } from "lucide-react";
import { useId, useMemo, useState } from "react";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
} from "@/components/ui/card";
import {
    Collapsible,
    CollapsibleContent,
    CollapsibleTrigger,
} from "@/components/ui/collapsible";
import {
    DialogContent,
    DialogDescription,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import {
    getLicenseProjects,
    type LicenseGroup,
    type LicenseProject,
    licenseProjectUrl,
} from "@/lib/licenses";
import { m } from "@/paraglide/messages";
import { type LicenseDocument, licensesQueryOptions } from "@/queries/licenses";

const groupLabels = {
    web: () => m.settings_licenses_group_web(),
    api: () => m.settings_licenses_group_api(),
    mobile: () => m.settings_licenses_group_mobile(),
    os: () => m.settings_licenses_group_os(),
    services: () => m.settings_licenses_group_services(),
    other: () => m.settings_licenses_group_other(),
} satisfies Record<LicenseGroup, () => string>;

function ProjectLink({ name, link }: { name: string; link: string }) {
    const url = licenseProjectUrl(link);
    return url ? (
        <a
            className="underline-offset-4 hover:underline"
            href={url}
            rel="noreferrer"
            target="_blank"
        >
            {name}{" "}
            <ExternalLinkIcon className="inline size-3" aria-hidden="true" />
        </a>
    ) : (
        <span>{name}</span>
    );
}

function ProjectRow({
    project,
    texts,
}: {
    project: LicenseProject;
    texts: LicenseDocument["licenses"];
}) {
    const references = [
        ...new Set([
            ...(project.license?.match(/[A-Za-z0-9.+-]+/g) ?? []),
            ...project.notices,
        ]),
    ].filter((key) => texts[key]);

    return (
        <li className="min-w-0">
            <Card size="sm">
                <CardContent className="grid min-w-0 gap-2 sm:grid-cols-5">
                    <div className="min-w-0 sm:col-span-3">
                        <CardTitle>
                            <ProjectLink
                                name={project.name}
                                link={project.link}
                            />
                        </CardTitle>
                        <CardDescription>
                            {project.creator ??
                                m.settings_licenses_unknown_creator()}
                            {project.version ? ` · ${project.version}` : ""}
                        </CardDescription>
                        <div className="mt-2 flex flex-wrap gap-1">
                            {project.groups.map((group) => (
                                <Badge key={group} variant="outline">
                                    {groupLabels[group]()}
                                </Badge>
                            ))}
                        </div>
                    </div>
                    <div className="min-w-0 sm:col-span-2 sm:text-right">
                        <p className="text-xs">
                            {project.license?.replace(
                                /LicenseRef-[A-Za-z0-9.-]+/g,
                                m.settings_licenses_custom(),
                            ) ?? m.settings_licenses_unknown_license()}
                        </p>
                    </div>
                    {references.length > 0 && (
                        <Collapsible className="min-w-0 sm:col-span-5">
                            <CollapsibleTrigger
                                render={<Button variant="outline" size="sm" />}
                            >
                                {m.settings_licenses_view_text()}
                            </CollapsibleTrigger>
                            <CollapsibleContent>
                                <div className="mt-3 max-h-64 space-y-4 overflow-y-auto">
                                    {references.map((key) => (
                                        <pre
                                            key={key}
                                            className="whitespace-pre-wrap font-mono text-xs"
                                        >
                                            {texts[key]}
                                        </pre>
                                    ))}
                                </div>
                            </CollapsibleContent>
                        </Collapsible>
                    )}
                </CardContent>
            </Card>
        </li>
    );
}

export function LicensesDialog({ open }: { open: boolean }) {
    const query = useQuery({ ...licensesQueryOptions(), enabled: open });
    const [search, setSearch] = useState("");
    const thanksId = useId();
    const projectsId = useId();
    const projects = useMemo(
        () => (query.data ? getLicenseProjects(query.data) : []),
        [query.data],
    );
    const filtered = useMemo(() => {
        const terms = search.trim().toLowerCase().split(/\s+/);
        return projects.filter((project) => {
            const text = [
                project.name,
                project.creator,
                project.version,
                project.license,
                ...project.groups,
            ]
                .join(" ")
                .toLowerCase();
            return terms.every((term) => text.includes(term));
        });
    }, [projects, search]);

    return (
        <DialogContent className="flex max-h-11/12 flex-col sm:max-w-3xl">
            <DialogHeader>
                <DialogTitle className="flex items-center gap-2">
                    <HeartHandshakeIcon className="size-4" />
                    {m.settings_licenses_title()}
                </DialogTitle>
                <DialogDescription>
                    {m.settings_licenses_description()}
                </DialogDescription>
            </DialogHeader>
            <div className="min-h-0 min-w-0 space-y-6 overflow-y-auto wrap-anywhere pl-1 pr-4">
                {query.isPending ? (
                    <p role="status" className="flex items-center gap-2 py-8">
                        <Spinner />
                        {m.settings_licenses_loading()}
                    </p>
                ) : query.isError ? (
                    <div className="space-y-3 py-4">
                        <Alert variant="destructive">
                            <AlertDescription>
                                {m.settings_licenses_error()}
                            </AlertDescription>
                        </Alert>
                        <Button
                            variant="outline"
                            disabled={query.isFetching}
                            onClick={() => void query.refetch()}
                        >
                            {m.settings_licenses_retry()}
                        </Button>
                    </div>
                ) : (
                    <>
                        <section
                            aria-labelledby={thanksId}
                            className="space-y-3"
                        >
                            <h2
                                id={thanksId}
                                className="font-heading font-semibold"
                            >
                                {m.settings_licenses_thanks()}
                            </h2>
                            <ul className="grid gap-2 sm:grid-cols-2">
                                {query.data.specialThanks.map((thanks) => (
                                    <li key={thanks.name} className="min-w-0">
                                        <Card size="sm" className="h-full">
                                            <CardHeader>
                                                <CardTitle>
                                                    <ProjectLink
                                                        name={thanks.name}
                                                        link={thanks.link}
                                                    />
                                                </CardTitle>
                                                <CardDescription>
                                                    {thanks.creator}
                                                </CardDescription>
                                            </CardHeader>
                                            <CardContent>
                                                {thanks.thanks}
                                            </CardContent>
                                        </Card>
                                    </li>
                                ))}
                            </ul>
                        </section>
                        <section
                            aria-labelledby={projectsId}
                            className="space-y-3"
                        >
                            <h2
                                id={projectsId}
                                className="font-heading font-semibold"
                            >
                                {m.settings_licenses_projects()}
                            </h2>
                            <Input
                                type="search"
                                aria-label={m.settings_licenses_search()}
                                placeholder={m.settings_licenses_search()}
                                value={search}
                                onChange={(event) => {
                                    setSearch(event.target.value);
                                }}
                            />
                            <p role="status" className="text-xs">
                                {m.settings_licenses_count({
                                    count: filtered.length,
                                })}
                            </p>
                            <ul className="space-y-2">
                                {filtered.map((project) => (
                                    <ProjectRow
                                        key={JSON.stringify([
                                            project.name,
                                            project.version,
                                            project.license,
                                            project.link,
                                        ])}
                                        project={project}
                                        texts={query.data.licenses}
                                    />
                                ))}
                            </ul>
                            {filtered.length === 0 && (
                                <p className="py-4">
                                    {m.settings_licenses_empty()}
                                </p>
                            )}
                        </section>
                    </>
                )}
            </div>
        </DialogContent>
    );
}
