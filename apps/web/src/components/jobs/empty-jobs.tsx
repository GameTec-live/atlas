import { BriefcaseBusinessIcon } from "lucide-react";
import {
    Empty,
    EmptyHeader,
    EmptyMedia,
    EmptyTitle,
} from "@/components/ui/empty";

export function EmptyJobs({ title }: { title: string }) {
    return (
        <Empty className="min-h-48 border">
            <EmptyHeader>
                <EmptyMedia variant="icon">
                    <BriefcaseBusinessIcon />
                </EmptyMedia>
                <EmptyTitle>{title}</EmptyTitle>
            </EmptyHeader>
        </Empty>
    );
}
