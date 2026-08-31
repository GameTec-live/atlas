import {
    CheckIcon,
    LanguagesIcon,
    MonitorIcon,
    MoonIcon,
    PaletteIcon,
    SunIcon,
} from "lucide-react";
import { useTheme } from "@/components/theme-provider";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import { m } from "@/paraglide/messages";
import { getLocale, setLocale } from "@/paraglide/runtime";

const themes = [
    { value: "light", label: m.misty_even_jackdaw_win, icon: SunIcon },
    { value: "dark", label: m.tired_ideal_cobra_dust, icon: MoonIcon },
    { value: "system", label: m.misty_tired_jay_heal, icon: MonitorIcon },
] as const;

const languages = [
    { value: "en", label: m.setup_english },
    { value: "de", label: m.setup_german },
] as const;

export function PreferencesStep() {
    const { theme, setTheme } = useTheme();
    const locale = getLocale();

    return (
        <div className="mx-auto grid max-w-4xl gap-6 md:grid-cols-2">
            <Card>
                <CardHeader>
                    <CardTitle className="flex items-center gap-2">
                        <PaletteIcon className="size-5" />
                        {m.setup_theme()}
                    </CardTitle>
                </CardHeader>
                <CardContent className="grid gap-3">
                    {themes.map((option) => {
                        const Icon = option.icon;
                        const selected = theme === option.value;

                        return (
                            <Button
                                key={option.value}
                                type="button"
                                variant="outline"
                                className={cn(
                                    "h-auto justify-start gap-3 px-4 py-4",
                                    selected && "border-primary bg-primary/5",
                                )}
                                onClick={() => setTheme(option.value)}
                                aria-pressed={selected}
                            >
                                <Icon className="size-5" />
                                <span className="flex-1 text-left">
                                    {option.label()}
                                </span>
                                {selected && <CheckIcon className="size-4" />}
                            </Button>
                        );
                    })}
                </CardContent>
            </Card>

            <Card>
                <CardHeader>
                    <CardTitle className="flex items-center gap-2">
                        <LanguagesIcon className="size-5" />
                        {m.setup_language()}
                    </CardTitle>
                </CardHeader>
                <CardContent className="grid gap-3">
                    {languages.map((option) => {
                        const selected = locale === option.value;

                        return (
                            <Button
                                key={option.value}
                                type="button"
                                variant="outline"
                                className={cn(
                                    "h-auto justify-start gap-3 px-4 py-4",
                                    selected && "border-primary bg-primary/5",
                                )}
                                onClick={() => setLocale(option.value)}
                                aria-pressed={selected}
                            >
                                <span className="flex-1 text-left">
                                    {option.label()}
                                </span>
                                {selected && <CheckIcon className="size-4" />}
                            </Button>
                        );
                    })}
                </CardContent>
            </Card>
        </div>
    );
}
