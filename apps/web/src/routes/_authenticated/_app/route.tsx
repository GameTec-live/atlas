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
                            <NavigationMenuItem>
                                <NavigationMenuLink render={<Link to="/" />}>
                                    {m.plain_topical_ant_compose()}
                                </NavigationMenuLink>
                            </NavigationMenuItem>
                            <NavigationMenuItem>
                                <NavigationMenuLink
                                    render={<Link to="/realtime" />}
                                >
                                    {m.simple_awful_coyote_quiz()}
                                </NavigationMenuLink>
                            </NavigationMenuItem>
                            <NavigationMenuItem>
                                <NavigationMenuLink
                                    render={<Link to="/jobs" />}
                                >
                                    {m.keen_plane_bobcat_value()}
                                </NavigationMenuLink>
                            </NavigationMenuItem>
                            <NavigationMenuItem>
                                <NavigationMenuLink
                                    render={<Link to="/fleet" />}
                                >
                                    {m.cool_only_manatee_pout()}
                                </NavigationMenuLink>
                            </NavigationMenuItem>
                            <NavigationMenuItem>
                                <NavigationMenuLink
                                    render={<Link to="/logbook" />}
                                >
                                    {m.logbook_title()}
                                </NavigationMenuLink>
                            </NavigationMenuItem>
                            <NavigationMenuItem>
                                <NavigationMenuTrigger>
                                    {m.factual_happy_falcon_arise()}
                                </NavigationMenuTrigger>
                                <NavigationMenuContent>
                                    <NavigationMenuLink
                                        render={
                                            <Link to="/settings/shortnames" />
                                        }
                                    >
                                        <Link2Icon />
                                        {m.long_topical_wolf_embrace()}
                                    </NavigationMenuLink>
                                    <NavigationMenuLink
                                        render={<Link to="/settings" />}
                                    >
                                        <Settings2Icon />
                                        {m.noisy_topical_camel_bloom()}
                                    </NavigationMenuLink>
                                </NavigationMenuContent>
                            </NavigationMenuItem>
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
                    <Button
                        className="w-full justify-start"
                        nativeButton={false}
                        variant="ghost"
                        render={<Link to="/" onClick={closeMenu} />}
                    >
                        <HouseIcon />
                        {m.plain_topical_ant_compose()}
                    </Button>
                    <Button
                        className="w-full justify-start"
                        nativeButton={false}
                        variant="ghost"
                        render={<Link to="/realtime" onClick={closeMenu} />}
                    >
                        <MapIcon />
                        {m.simple_awful_coyote_quiz()}
                    </Button>
                    <Button
                        className="w-full justify-start"
                        nativeButton={false}
                        variant="ghost"
                        render={<Link to="/jobs" onClick={closeMenu} />}
                    >
                        <BriefcaseBusinessIcon />
                        {m.keen_plane_bobcat_value()}
                    </Button>
                    <Button
                        className="w-full justify-start"
                        nativeButton={false}
                        variant="ghost"
                        render={<Link to="/fleet" onClick={closeMenu} />}
                    >
                        <TruckIcon />
                        {m.cool_only_manatee_pout()}
                    </Button>
                    <Button
                        className="w-full justify-start"
                        nativeButton={false}
                        variant="ghost"
                        render={<Link to="/logbook" onClick={closeMenu} />}
                    >
                        <BookOpenIcon />
                        {m.logbook_title()}
                    </Button>

                    <p className="px-2 pt-5 pb-1 text-xs font-medium text-muted-foreground">
                        {m.factual_happy_falcon_arise()}
                    </p>
                    <Button
                        className="w-full justify-start"
                        nativeButton={false}
                        variant="ghost"
                        render={
                            <Link
                                to="/settings/shortnames"
                                onClick={closeMenu}
                            />
                        }
                    >
                        <Link2Icon />
                        {m.long_topical_wolf_embrace()}
                    </Button>
                    <Button
                        className="w-full justify-start"
                        nativeButton={false}
                        variant="ghost"
                        render={<Link to="/settings" onClick={closeMenu} />}
                    >
                        <Settings2Icon />
                        {m.noisy_topical_camel_bloom()}
                    </Button>
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
