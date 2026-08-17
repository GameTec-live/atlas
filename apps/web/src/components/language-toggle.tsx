import { Button } from "@/components/ui/button";
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { getLocale, locales, setLocale } from "@/paraglide/runtime";

export default function LanguageToggle() {
    return (
        <DropdownMenu>
            <DropdownMenuTrigger
                render={
                    <Button variant="outline" size="icon">
                        {getLocale()}
                    </Button>
                }
            />

            <DropdownMenuContent>
                {locales.map((locale) => (
                    <DropdownMenuItem
                        key={locale}
                        onClick={() => setLocale(locale)}
                    >
                        {locale}
                    </DropdownMenuItem>
                ))}
            </DropdownMenuContent>
        </DropdownMenu>
    );
}
