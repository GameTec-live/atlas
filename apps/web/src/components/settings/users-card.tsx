import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
    createColumnHelper,
    tableFeatures,
    useTable,
} from "@tanstack/react-table";
import {
    CheckIcon,
    PencilIcon,
    PlusIcon,
    Trash2Icon,
    UsersIcon,
    XIcon,
} from "lucide-react";
import { useMemo, useState } from "react";
import { DeleteConfirmationDialog } from "@/components/settings/delete-confirmation-dialog";
import { UserFormDialog } from "@/components/settings/user-form-dialog";
import { Button } from "@/components/ui/button";
import {
    Card,
    CardAction,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
} from "@/components/ui/card";
import { Spinner } from "@/components/ui/spinner";
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table";
import { toast } from "@/components/ui/toast";
import { removeAdminUser } from "@/lib/admin-users";
import { hasAdminRole } from "@/lib/auth";
import { authClient } from "@/lib/auth-client";
import { m } from "@/paraglide/messages";
import {
    type SettingsUser,
    settingsUsersQueryKey,
    settingsUsersQueryOptions,
} from "@/queries/settings";

const tableFeatureSet = tableFeatures({});
const columnHelper = createColumnHelper<typeof tableFeatureSet, SettingsUser>();

export function UsersCard() {
    const { data: session } = authClient.useSession();
    const queryClient = useQueryClient();
    const usersQuery = useQuery(settingsUsersQueryOptions());
    const [isCreating, setIsCreating] = useState(false);
    const [editingUser, setEditingUser] = useState<SettingsUser | null>(null);
    const [deletingUser, setDeletingUser] = useState<SettingsUser | null>(null);
    const deleteMutation = useMutation({
        mutationFn: removeAdminUser,
        onSuccess: async () => {
            await queryClient.invalidateQueries({
                queryKey: settingsUsersQueryKey,
            });
            setDeletingUser(null);
            toast.add({
                id: "settings-user-deleted",
                type: "success",
                title: m.settings_user_deleted(),
            });
        },
        onError: () =>
            toast.add({
                id: "settings-user-delete-error",
                type: "error",
                title: m.settings_user_delete_error(),
                priority: "high",
            }),
    });
    const columns = useMemo(
        () =>
            columnHelper.columns([
                columnHelper.accessor((user) => user.username ?? "—", {
                    id: "username",
                    header: m.settings_username(),
                    cell: ({ getValue }) => (
                        <span className="font-medium">{getValue()}</span>
                    ),
                }),
                columnHelper.accessor("email", {
                    header: m.settings_email(),
                }),
                columnHelper.accessor("name", {
                    header: m.settings_name(),
                }),
                columnHelper.accessor((user) => hasAdminRole(user.role), {
                    id: "administrator",
                    header: m.settings_administrator(),
                    cell: ({ getValue }) => (
                        <div className="flex items-center gap-2">
                            {getValue() ? (
                                <CheckIcon className="h-4 w-4 text-emerald-500" />
                            ) : (
                                <XIcon className="h-4 w-4 text-destructive" />
                            )}
                            <span>
                                {getValue()
                                    ? m.settings_yes()
                                    : m.settings_no()}
                            </span>
                        </div>
                    ),
                }),
                columnHelper.display({
                    id: "actions",
                    header: m.settings_actions(),
                    cell: ({ row }) => {
                        const isCurrentUser =
                            row.original.id === session?.user.id;
                        return (
                            <div className="flex justify-end gap-1">
                                <Button
                                    type="button"
                                    variant="ghost"
                                    size="icon-sm"
                                    aria-label={m.settings_edit_user()}
                                    title={m.settings_edit_user()}
                                    onClick={() => setEditingUser(row.original)}
                                >
                                    <PencilIcon />
                                </Button>
                                <Button
                                    type="button"
                                    variant="ghost"
                                    size="icon-sm"
                                    className="text-destructive hover:text-destructive"
                                    aria-label={m.settings_delete_user()}
                                    title={
                                        isCurrentUser
                                            ? m.settings_cannot_delete_self()
                                            : m.settings_delete_user()
                                    }
                                    disabled={isCurrentUser}
                                    onClick={() =>
                                        setDeletingUser(row.original)
                                    }
                                >
                                    <Trash2Icon />
                                </Button>
                            </div>
                        );
                    },
                }),
            ]),
        [session?.user.id],
    );
    const table = useTable({
        features: tableFeatureSet,
        columns,
        data: usersQuery.data ?? [],
        getRowId: (user) => user.id,
    });

    return (
        <>
            <Card className="lg:col-span-12">
                <CardHeader>
                    <CardTitle className="flex items-center gap-2">
                        <UsersIcon className="size-4" />
                        {m.settings_users()}
                    </CardTitle>
                    <CardDescription>
                        {m.settings_users_description()}
                    </CardDescription>
                    <CardAction>
                        <Button
                            type="button"
                            size="sm"
                            onClick={() => setIsCreating(true)}
                        >
                            <PlusIcon />
                            {m.settings_new_user()}
                        </Button>
                    </CardAction>
                </CardHeader>
                <CardContent className="p-0">
                    <div className="overflow-hidden">
                        <Table>
                            <TableHeader className="bg-muted">
                                {table.getHeaderGroups().map((group) => (
                                    <TableRow key={group.id}>
                                        {group.headers.map((header) => (
                                            <TableHead
                                                key={header.id}
                                                className="last:sticky last:right-0 last:bg-muted last:text-right"
                                            >
                                                {header.isPlaceholder ? null : (
                                                    <table.FlexRender
                                                        header={header}
                                                    />
                                                )}
                                            </TableHead>
                                        ))}
                                    </TableRow>
                                ))}
                            </TableHeader>
                            <TableBody>
                                {usersQuery.isPending ? (
                                    <TableRow>
                                        <TableCell
                                            colSpan={columns.length}
                                            className="h-28 text-center"
                                        >
                                            <Spinner className="mx-auto" />
                                        </TableCell>
                                    </TableRow>
                                ) : usersQuery.isError ? (
                                    <TableRow>
                                        <TableCell
                                            colSpan={columns.length}
                                            className="h-28 text-center text-destructive"
                                        >
                                            {m.settings_users_error()}
                                        </TableCell>
                                    </TableRow>
                                ) : table.getRowModel().rows.length ? (
                                    table.getRowModel().rows.map((row) => (
                                        <TableRow
                                            key={row.id}
                                            className="group"
                                        >
                                            {row.getAllCells().map((cell) => (
                                                <TableCell
                                                    key={cell.id}
                                                    className="last:sticky last:right-0 last:bg-background group-hover:last:bg-muted/50"
                                                >
                                                    <table.FlexRender
                                                        cell={cell}
                                                    />
                                                </TableCell>
                                            ))}
                                        </TableRow>
                                    ))
                                ) : (
                                    <TableRow>
                                        <TableCell
                                            colSpan={columns.length}
                                            className="h-28 text-center text-muted-foreground"
                                        >
                                            {m.settings_no_users()}
                                        </TableCell>
                                    </TableRow>
                                )}
                            </TableBody>
                        </Table>
                    </div>
                </CardContent>
            </Card>

            {isCreating && (
                <UserFormDialog
                    key="new-user"
                    user={null}
                    onClose={() => setIsCreating(false)}
                />
            )}
            {editingUser && (
                <UserFormDialog
                    key={editingUser.id}
                    user={editingUser}
                    onClose={() => setEditingUser(null)}
                />
            )}
            <DeleteConfirmationDialog
                open={deletingUser !== null}
                title={m.settings_delete_user_title()}
                description={m.settings_delete_user_description({
                    user: deletingUser?.name ?? "",
                })}
                actionLabel={m.settings_delete_user()}
                isPending={deleteMutation.isPending}
                onClose={() => setDeletingUser(null)}
                onConfirm={() =>
                    deletingUser && deleteMutation.mutate(deletingUser.id)
                }
            />
        </>
    );
}
