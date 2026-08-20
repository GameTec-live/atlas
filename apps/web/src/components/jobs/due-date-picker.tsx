import { de, enUS } from "date-fns/locale";
import { CalendarClockIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Calendar } from "@/components/ui/calendar";
import { Input } from "@/components/ui/input";
import {
    Popover,
    PopoverContent,
    PopoverTrigger,
} from "@/components/ui/popover";
import { formatDateTime } from "@/lib/date";
import { m } from "@/paraglide/messages";

type DueDatePickerProps = {
    value: Date;
    disabled: boolean;
    locale: string;
    onChange: (value: Date) => void;
};

export function DueDatePicker({
    value,
    disabled,
    locale,
    onChange,
}: DueDatePickerProps) {
    const timeValue = `${String(value.getHours()).padStart(2, "0")}:${String(
        value.getMinutes(),
    ).padStart(2, "0")}`;

    const selectDate = (date: Date | undefined) => {
        if (!date) return;

        const next = new Date(date);
        next.setHours(value.getHours(), value.getMinutes(), 0, 0);
        onChange(next);
    };

    const selectTime = (time: string) => {
        if (!time) return;

        const [hours = 0, minutes = 0] = time.split(":").map(Number);
        const next = new Date(value);
        next.setHours(hours, minutes, 0, 0);
        onChange(next);
    };

    return (
        <Popover>
            <PopoverTrigger
                render={
                    <Button
                        variant="ghost"
                        className="h-auto max-w-44 shrink-0 rounded-none border-l px-3 sm:max-w-none"
                        disabled={disabled}
                        aria-label={m.job_details_edit_due()}
                    />
                }
            >
                <CalendarClockIcon className="hidden text-muted-foreground sm:block" />
                <span className="text-right text-xs font-medium sm:text-sm">
                    {formatDateTime(value, locale)}
                </span>
            </PopoverTrigger>
            <PopoverContent
                align="end"
                sideOffset={8}
                className="w-auto gap-3 p-3"
            >
                <Calendar
                    mode="single"
                    selected={value}
                    defaultMonth={value}
                    captionLayout="dropdown"
                    startMonth={new Date(value.getFullYear() - 10, 0)}
                    endMonth={new Date(value.getFullYear() + 10, 11)}
                    locale={locale.startsWith("de") ? de : enUS}
                    className="p-0"
                    onSelect={selectDate}
                />
                <Input
                    type="time"
                    value={timeValue}
                    aria-label={m.jobs_due()}
                    onChange={(event) => selectTime(event.target.value)}
                />
            </PopoverContent>
        </Popover>
    );
}
