import {
    AlertDialog,
    AlertDialogAction,
    AlertDialogCancel,
    AlertDialogContent,
    AlertDialogDescription,
    AlertDialogFooter,
    AlertDialogHeader,
    AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Spinner } from "@/components/ui/spinner";
import { m } from "@/paraglide/messages";
import type { LogbookEntry } from "@/queries/logbooks";

export function InvalidateEntryDialog({
    entry,
    isPending,
    onClose,
    onConfirm,
}: {
    entry: LogbookEntry | null;
    isPending: boolean;
    onClose: () => void;
    onConfirm: (entry: LogbookEntry) => void;
}) {
    return (
        <AlertDialog
            open={entry !== null}
            onOpenChange={(open) => {
                if (!open && !isPending) onClose();
            }}
        >
            <AlertDialogContent>
                <AlertDialogHeader>
                    <AlertDialogTitle>
                        {m.logbook_invalidate_title()}
                    </AlertDialogTitle>
                    <AlertDialogDescription>
                        {m.logbook_invalidate_description()}
                    </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                    <AlertDialogCancel disabled={isPending}>
                        {m.logbook_cancel()}
                    </AlertDialogCancel>
                    <AlertDialogAction
                        variant="destructive"
                        disabled={isPending}
                        onClick={() => {
                            if (entry) onConfirm(entry);
                        }}
                    >
                        {isPending && <Spinner />}
                        {m.logbook_mark_invalid()}
                    </AlertDialogAction>
                </AlertDialogFooter>
            </AlertDialogContent>
        </AlertDialog>
    );
}
