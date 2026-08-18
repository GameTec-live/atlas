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
import { m } from "@/paraglide/messages";
import type { Job } from "@/queries/jobs";
import { Spinner } from "../ui/spinner";

export function DeleteJobDialog({
    job,
    isPending,
    onClose,
    onConfirm,
}: {
    job: Job | null;
    isPending: boolean;
    onClose: () => void;
    onConfirm: (job: Job) => void;
}) {
    return (
        <AlertDialog
            open={job !== null}
            onOpenChange={(open) => {
                if (!open && !isPending) onClose();
            }}
        >
            <AlertDialogContent>
                <AlertDialogHeader>
                    <AlertDialogTitle>{m.jobs_delete_title()}</AlertDialogTitle>
                    <AlertDialogDescription>
                        {m.jobs_delete_description()}
                    </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                    <AlertDialogCancel disabled={isPending}>
                        {m.jobs_delete_cancel()}
                    </AlertDialogCancel>
                    <AlertDialogAction
                        variant="destructive"
                        disabled={isPending}
                        onClick={() => {
                            if (job) onConfirm(job);
                        }}
                    >
                        {isPending && <Spinner />}
                        {m.jobs_delete_confirm()}
                    </AlertDialogAction>
                </AlertDialogFooter>
            </AlertDialogContent>
        </AlertDialog>
    );
}
