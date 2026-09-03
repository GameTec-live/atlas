import { createFileRoute, Link, Outlet } from "@tanstack/react-router";
import {
    BookOpenIcon,
    BriefcaseBusinessIcon,
    HouseIcon,
    Link2Icon,
    MapIcon,
    MenuIcon,
    Settings2Icon,
    TruckIcon,
    XIcon,
} from "lucide-react";
import { useState } from "react";
import { AtlasLogo } from "@/components/atlas-logo";
import LanguageToggle from "@/components/language-toggle";
import { ModeToggle } from "@/components/mode-toggle";
import SignoutButton from "@/components/signout-button";
import { Button } from "@/components/ui/button";
import {
    Drawer,
    DrawerClose,
    DrawerContent,
    DrawerFooter,
    DrawerHeader,
    DrawerTitle,
    DrawerTrigger,
} from "@/components/ui/drawer";
import {
    NavigationMenu,
    NavigationMenuContent,
    NavigationMenuItem,
    NavigationMenuLink,
    NavigationMenuList,
    NavigationMenuTrigger,
} from "@/components/ui/navigation-menu";
import { m } from "@/paraglide/messages";

const navigation = [
    { label: m.plain_topical_ant_compose, icon: HouseIcon, to: "/" },
    { label: m.simple_awful_coyote_quiz, icon: MapIcon, to: "/realtime" },
    {
        label: m.keen_plane_bobcat_value,
        icon: BriefcaseBusinessIcon,
        to: "/jobs",
    },
    { label: m.cool_only_manatee_pout, icon: TruckIcon, to: "/fleet" },
    { label: m.logbook_title, icon: BookOpenIcon, to: "/logbook" },
    {
        label: m.factual_happy_falcon_arise,
        children: [
            {
                label: m.long_topical_wolf_embrace,
                icon: Link2Icon,
                to: "/settings/shortnames",
            },
            {
                label: m.noisy_topical_camel_bloom,
                icon: Settings2Icon,
                to: "/settings",
            },
        ],
    },
] as const;

type NavigationEntry = (typeof navigation)[number];
type NavigationGroup = Extract<NavigationEntry, { children: unknown }>;
type NavigationLinkItem =
    | Extract<NavigationEntry, { to: string }>
    | NavigationGroup["children"][number];

export const Route = createFileRoute("/_authenticated/_app")({
    component: RouteComponent,
});

function RouteComponent() {
    return (
        <div className="flex h-svh flex-col">
            <nav className="flex shrink-0 items-center justify-between gap-2 border-b p-2">
                <div className="flex items-center gap-2">
                    <Link to="/">
                        <AtlasLogo className="h-8" />
                    </Link>
                    <NavigationMenu className="hidden md:flex">
                        <NavigationMenuList>
                            {navigation.map((item) => (
                                <NavigationMenuItem
                                    key={"to" in item ? item.to : "settings"}
                                >
                                    {"to" in item ? (
                                        <NavigationMenuLink
                                            render={<Link to={item.to} />}
                                        >
                                            <item.icon />
                                            {item.label()}
                                        </NavigationMenuLink>
                                    ) : (
                                        <>
                                            <NavigationMenuTrigger>
                                                {item.label()}
                                            </NavigationMenuTrigger>
                                            <NavigationMenuContent>
                                                {item.children.map((child) => {
                                                    const Icon = child.icon;

                                                    return (
                                                        <NavigationMenuLink
                                                            key={child.to}
                                                            render={
                                                                <Link
                                                                    to={
                                                                        child.to
                                                                    }
                                                                />
                                                            }
                                                        >
                                                            <Icon />
                                                            {child.label()}
                                                        </NavigationMenuLink>
                                                    );
                                                })}
                                            </NavigationMenuContent>
                                        </>
                                    )}
                                </NavigationMenuItem>
                            ))}
                        </NavigationMenuList>
                    </NavigationMenu>
                </div>
                <div className="hidden items-center gap-2 md:flex">
                    <LanguageToggle />
                    <ModeToggle />
                    <SignoutButton />
                </div>
                <MobileNavigation />
            </nav>
            <div className="min-h-0 flex-1">
                <Outlet />
            </div>
        </div>
    );
}

function MobileNavigation() {
    const [open, setOpen] = useState(false);
    const closeMenu = () => setOpen(false);

    return (
        <Drawer open={open} onOpenChange={setOpen} swipeDirection="right">
            <DrawerTrigger
                className="md:hidden"
                render={<Button variant="outline" size="icon" />}
            >
                <MenuIcon />
                <span className="sr-only">{m.navigation_open_menu()}</span>
            </DrawerTrigger>
            <DrawerContent>
                <DrawerHeader className="flex-row items-center justify-between border-b p-4">
                    <DrawerTitle>{m.navigation_menu()}</DrawerTitle>
                    <DrawerClose
                        render={<Button variant="ghost" size="icon" />}
                    >
                        <XIcon />
                        <span className="sr-only">
                            {m.navigation_close_menu()}
                        </span>
                    </DrawerClose>
                </DrawerHeader>

                <div className="flex min-h-0 flex-1 flex-col overflow-y-auto p-2">
                    {navigation.map((item) =>
                        "to" in item ? (
                            <MobileNavigationLink
                                key={item.to}
                                item={item}
                                onNavigate={closeMenu}
                            />
                        ) : (
                            <div key={item.label()}>
                                <p className="px-2 pt-4 pb-1 text-xs font-medium text-muted-foreground">
                                    {item.label()}
                                </p>
                                {item.children.map((child) => (
                                    <MobileNavigationLink
                                        key={child.to}
                                        item={child}
                                        onNavigate={closeMenu}
                                    />
                                ))}
                            </div>
                        ),
                    )}
                </div>

                <DrawerFooter className="flex-row items-center justify-end border-t p-4">
                    <LanguageToggle />
                    <ModeToggle />
                    <SignoutButton />
                </DrawerFooter>
            </DrawerContent>
        </Drawer>
    );
}

function MobileNavigationLink({
    item,
    onNavigate,
}: {
    item: NavigationLinkItem;
    onNavigate: () => void;
}) {
    const Icon = item.icon;

    return (
        <Button
            className="w-full justify-start"
            nativeButton={false}
            variant="ghost"
            render={<Link to={item.to} onClick={onNavigate} />}
        >
            <Icon />
            {item.label()}
        </Button>
    );
}
