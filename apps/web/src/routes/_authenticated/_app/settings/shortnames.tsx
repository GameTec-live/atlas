import {
    useMutation,
    useQueryClient,
    useSuspenseQuery,
} from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import type { Row } from "@tanstack/react-table";
import {
    createColumnHelper,
    tableFeatures,
    useTable,
} from "@tanstack/react-table";
import { useMemo, useState } from "react";
import { AddressSearch } from "@/components/address-search";
import { Input } from "@/components/ui/input";
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table";
import { toast } from "@/components/ui/toast";
import { useDebouncedCallback } from "@/hooks/use-debounced-callback";
import { api, unwrapEden } from "@/lib/api-client";
import { cn } from "@/lib/utils";
import { m } from "@/paraglide/messages";
import { addressResolveQueryKey } from "@/queries/geoservices";
import {
    shortnamesQueryKey,
    shortnamesQueryOptions,
} from "@/queries/shortnames";

export const Route = createFileRoute(
    "/_authenticated/_app/settings/shortnames",
)({
    loader: ({ context }) =>
        context.queryClient.ensureQueryData(shortnamesQueryOptions()),
    component: ShortnamesSettings,
});

type ShortnameTableRow = {
    key: string;
    value: string;
    persistedKey: string | null;
};

type ShortnameChange = {
    originalKey: string | null;
    key: string;
    value: string;
};

type ShortnameDraft = {
    key: string;
    value: string;
    isAddressValid: boolean;
};

const tableFeatureSet = tableFeatures({});
const emptyShortnames: Array<{ key: string; value: string }> = [];
const columnHelper = createColumnHelper<
    typeof tableFeatureSet,
    ShortnameTableRow
>();
const columns = columnHelper.columns([
    columnHelper.accessor("key", { header: () => m.shortname_column() }),
    columnHelper.accessor("value", { header: () => m.address_column() }),
]);

async function saveShortname({ originalKey, key, value }: ShortnameChange) {
    if (!key && originalKey) {
        return unwrapEden(api.shortnames({ key: originalKey }).delete());
    }

    if (!originalKey) {
        return unwrapEden(api.shortnames.post({ key, value }));
    }

    if (originalKey === key) {
        return unwrapEden(api.shortnames({ key: originalKey }).put({ value }));
    }

    const created = await unwrapEden(api.shortnames.post({ key, value }));
    try {
        await unwrapEden(api.shortnames({ key: originalKey }).delete());
    } catch (error) {
        // Keep a failed rename from leaving both the old and new keys behind.
        await unwrapEden(api.shortnames({ key }).delete()).catch(
            () => undefined,
        );
        throw error;
    }
    return created;
}

function EditableShortnameRow({
    row,
}: {
    row: Row<typeof tableFeatureSet, ShortnameTableRow>;
}) {
    const queryClient = useQueryClient();
    const original = row.original;
    const [draft, setDraft] = useState<ShortnameDraft>({
        key: original.key,
        value: original.value,
        isAddressValid: original.persistedKey !== null,
    });

    const mutation = useMutation({
        mutationFn: saveShortname,
        onSuccess: async (_, change) => {
            if (change.originalKey === null) {
                setDraft({ key: "", value: "", isAddressValid: false });
            }

            await Promise.all([
                queryClient.invalidateQueries({
                    queryKey: shortnamesQueryKey,
                }),
                queryClient.invalidateQueries({
                    queryKey: addressResolveQueryKey,
                }),
            ]);

            toast.add({
                id: "shortname-save",
                type: "success",
                title: change.key ? m.shortname_saved() : m.shortname_deleted(),
            });
        },
        onError: (_, change) => {
            const persistedKey = change.originalKey;
            if (persistedKey !== null && change.key !== persistedKey) {
                setDraft((current) => ({
                    ...current,
                    key: persistedKey,
                }));
            }

            toast.add({
                id: "shortname-save",
                type: "error",
                title: m.shortname_save_error(),
                priority: "high",
            });
        },
    });
    const { isPending, mutate } = mutation;
    const { debounce: debounceSave, cancel: cancelSave } = useDebouncedCallback(
        (change: ShortnameChange) => mutate(change),
        500,
    );

    const queueSave = (nextDraft: ShortnameDraft) => {
        cancelSave();
        const normalizedKey = nextDraft.key.trim().toLowerCase();
        const normalizedValue = nextDraft.value.trim();

        if (!normalizedKey && !original.persistedKey) return;
        if (normalizedKey && (!normalizedValue || !nextDraft.isAddressValid)) {
            return;
        }
        if (
            normalizedKey === original.persistedKey &&
            normalizedValue === original.value
        ) {
            return;
        }

        debounceSave({
            originalKey: original.persistedKey,
            key: normalizedKey,
            value: normalizedValue,
        });
    };

    const updateDraft = (changes: Partial<ShortnameDraft>) => {
        const nextDraft = { ...draft, ...changes };
        setDraft(nextDraft);
        queueSave(nextDraft);
    };

    return (
        <TableRow
            className={cn("h-11", isPending && "opacity-60")}
            aria-busy={isPending}
        >
            <TableCell className="w-1/2 border-r p-0">
                <Input
                    value={draft.key}
                    onChange={(event) =>
                        updateDraft({ key: event.target.value.toLowerCase() })
                    }
                    disabled={isPending}
                    aria-label={m.shortname_column()}
                    placeholder={
                        original.persistedKey === null
                            ? m.new_shortname_placeholder()
                            : undefined
                    }
                    className="h-11 border-0 px-4 focus-visible:ring-inset"
                />
            </TableCell>
            <TableCell className="w-1/2 p-0">
                <AddressSearch
                    className="h-11 w-full border-0 focus-within:ring-inset"
                    value={draft.value}
                    onValueChange={(value) =>
                        updateDraft({
                            value,
                            isAddressValid: value.trim() === original.value,
                        })
                    }
                    onAddressSelect={(value) =>
                        updateDraft({ value, isAddressValid: true })
                    }
                    disabled={isPending}
                    aria-label={m.address_column()}
                    placeholder={
                        original.persistedKey === null
                            ? m.new_address_placeholder()
                            : undefined
                    }
                />
            </TableCell>
        </TableRow>
    );
}

function ShortnamesSettings() {
    const { data: shortnames } = useSuspenseQuery(shortnamesQueryOptions());
    const loadedShortnames = shortnames ?? emptyShortnames;
    const data = useMemo<ShortnameTableRow[]>(
        () => [
            ...loadedShortnames.map((shortname) => ({
                ...shortname,
                persistedKey: shortname.key,
            })),
            { key: "", value: "", persistedKey: null },
        ],
        [loadedShortnames],
    );
    const table = useTable({
        features: tableFeatureSet,
        columns,
        data,
        getRowId: (row) => row.persistedKey ?? "new-shortname",
    });

    return (
        <main className="mx-auto w-full max-w-5xl p-6 sm:p-10 lg:p-14">
            <div className="mb-8">
                <h1 className="text-3xl font-semibold tracking-tight">
                    {m.shortnames_title()}
                </h1>
                <p className="mt-2 text-muted-foreground">
                    {m.shortnames_description()}
                </p>
            </div>

            <div className="rounded-xl border bg-card shadow-sm">
                <Table>
                    <TableHeader className="bg-muted">
                        {table.getHeaderGroups().map((headerGroup) => (
                            <TableRow key={headerGroup.id}>
                                {headerGroup.headers.map((header, index) => (
                                    <TableHead
                                        key={header.id}
                                        className={cn(
                                            "h-12 px-4 text-base font-semibold",
                                            index === 0 && "border-r",
                                        )}
                                    >
                                        {header.isPlaceholder ? null : (
                                            <table.FlexRender header={header} />
                                        )}
                                    </TableHead>
                                ))}
                            </TableRow>
                        ))}
                    </TableHeader>
                    <TableBody>
                        {table.getRowModel().rows.map((row) => (
                            <EditableShortnameRow key={row.id} row={row} />
                        ))}
                    </TableBody>
                </Table>
            </div>
        </main>
    );
}
