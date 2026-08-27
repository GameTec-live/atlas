/**
 * Languages supported by Valhalla's turn-by-turn narration API.
 * @see https://valhalla.github.io/valhalla/api/route/api-reference/#supported-language-tags
 */
export const valhallaLanguageCodes = [
    "bg-BG",
    "ca-ES",
    "cs-CZ",
    "da-DK",
    "de-DE",
    "el-GR",
    "en-GB",
    "en-US-x-pirate",
    "en-US",
    "es-ES",
    "et-EE",
    "fi-FI",
    "fr-FR",
    "hi-IN",
    "hu-HU",
    "it-IT",
    "ja-JP",
    "nb-NO",
    "nl-NL",
    "pl-PL",
    "pt-BR",
    "pt-PT",
    "ro-RO",
    "ru-RU",
    "sk-SK",
    "sl-SI",
    "sv-SE",
    "tr-TR",
    "uk-UA",
] as const;

export type ValhallaLanguage = (typeof valhallaLanguageCodes)[number];

export function getValhallaLanguageOptions(locale: string) {
    const displayNames = new Intl.DisplayNames(locale, { type: "language" });

    return valhallaLanguageCodes.map((value) => ({
        value,
        label:
            value === "en-US-x-pirate"
                ? "English (Pirate)"
                : (displayNames.of(value) ?? value),
    }));
}
