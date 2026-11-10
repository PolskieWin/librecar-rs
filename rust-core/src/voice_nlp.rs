use serde::Serialize;

#[derive(Debug, Clone, Serialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum VoiceIntentType {
    Navigate,
    MusicPlay,
    MusicPause,
    MusicResume,
    MusicNext,
    MusicPrev,
    #[serde(rename = "open_youtube")]
    OpenYouTube,
    OpenFlappyBird,
    #[serde(rename = "map_satellite_3d")]
    MapSatellite3d,
    MapDarkMode,
    ShowWeather,
    ShowCalendar,
    ResetKmCounter,
    SwitchLayout,
    Unknown,
}

#[derive(Debug, Clone, Serialize)]
pub struct VoiceCommandResult {
    pub intent: VoiceIntentType,
    pub target: String,
    pub detected_lang: String,
    pub eco_priority: bool,
    pub reply_en: String,
    pub reply_pl: String,
}

/// Normalizes Polish diacritics and cleans punctuation for fast deterministic NLP matching
/// while keeping the original UTF-8 target substring intact for geocoding / search.
fn fold_polish_ascii(input: &str) -> String {
    input
        .chars()
        .map(|c| match c {
            'ą' | 'Ą' => 'a',
            'ć' | 'Ć' => 'c',
            'ę' | 'Ę' => 'e',
            'ł' | 'Ł' => 'l',
            'ń' | 'Ń' => 'n',
            'ó' | 'Ó' => 'o',
            'ś' | 'Ś' => 's',
            'ź' | 'Ź' | 'ż' | 'Ż' => 'z',
            _ => c.to_ascii_lowercase(),
        })
        .map(|c| {
            if c.is_alphanumeric() || c.is_whitespace() {
                c
            } else {
                ' '
            }
        })
        .collect::<String>()
        .split_whitespace()
        .collect::<Vec<_>>()
        .join(" ")
}

fn detect_polish(raw: &str, folded: &str) -> bool {
    let has_pl_chars = raw.chars().any(|c| "ąćęłńóśźżĄĆĘŁŃÓŚŹŻ".contains(c));
    if has_pl_chars {
        return true;
    }
    let pl_keywords = [
        "daj mi",
        "pokaz",
        "mape",
        "mapa",
        "prowadz",
        "nawiguj",
        "jedz",
        "trasa",
        "trase",
        "wlacz",
        "otworz",
        "odtworz",
        "muzyke",
        "pauza",
        "wznow",
        "nastepny",
        "poprzedni",
        "pogoda",
        "kalendarz",
        "wyzeruj",
        "licznik",
        "kilometrow",
        "zagraj",
        "nudzi",
        "satelitarny",
        "oszczedna",
        "paliwowa",
        "uklad",
        "gdzie jest",
    ];
    pl_keywords.iter().any(|k| folded.contains(k))
}

/// Extracts destination or search argument from the ORIGINAL raw string by aligning against folded prefix triggers.
fn extract_after_trigger(raw: &str, triggers: &[&str]) -> Option<String> {
    let raw_trimmed = raw.trim();
    let folded_raw = fold_polish_ascii(raw_trimmed);

    for trig in triggers {
        if let Some(pos) = folded_raw.find(trig) {
            let after_folded = folded_raw[pos + trig.len()..].trim();
            if after_folded.is_empty() {
                continue;
            }
            // Count how many words follow the trigger and pull that tail from original raw words
            let tail_word_count = after_folded.split_whitespace().count();
            let raw_words: Vec<&str> = raw_trimmed
                .split_whitespace()
                .map(|w| w.trim_matches(|c: char| !c.is_alphanumeric()))
                .filter(|w| !w.is_empty())
                .collect();
            if raw_words.len() >= tail_word_count {
                let slice = &raw_words[raw_words.len() - tail_word_count..];
                return Some(slice.join(" "));
            }
            return Some(after_folded.to_string());
        }
    }
    None
}

pub fn parse_voice_command(raw_input: &str, preferred_lang: &str) -> VoiceCommandResult {
    let trimmed = raw_input.trim();
    let folded = fold_polish_ascii(trimmed);
    let is_pl = if preferred_lang == "pl" {
        true
    } else {
        detect_polish(trimmed, &folded)
    };
    let lang = if is_pl { "pl" } else { "en" }.to_string();

    if folded.is_empty() {
        return VoiceCommandResult {
            intent: VoiceIntentType::Unknown,
            target: String::new(),
            detected_lang: lang,
            eco_priority: false,
            reply_en: "I didn't catch that. Try: Give me the map to Warsaw.".to_string(),
            reply_pl: "Nie zrozumiałem. Powiedz np.: Daj mi mapę do Warszawa.".to_string(),
        };
    }

    // 1. Flappy Bird / bored game
    let flappy_triggers = [
        "flappy bird",
        "flappy",
        "play game",
        "open game",
        "i am bored",
        "im bored",
        "bored",
        "zagraj w flappy",
        "otworz gre",
        "wlacz gre",
        "nudzi mi sie",
        "nudze sie",
    ];
    if flappy_triggers.iter().any(|k| folded.contains(k)) {
        return VoiceCommandResult {
            intent: VoiceIntentType::OpenFlappyBird,
            target: String::new(),
            detected_lang: lang,
            eco_priority: false,
            reply_en: "Opening Flappy Bird. Have fun!".to_string(),
            reply_pl: "Otwieram Flappy Bird. Miłej zabawy!".to_string(),
        };
    }

    // 2. Map styles (3D Satellite vs Dark)
    if folded.contains("3d") && (folded.contains("satel") || folded.contains("satell"))
        || folded.contains("widok satelitarny")
        || folded.contains("tryb satelitarny")
        || folded.contains("satellite mode")
    {
        return VoiceCommandResult {
            intent: VoiceIntentType::MapSatellite3d,
            target: String::new(),
            detected_lang: lang,
            eco_priority: false,
            reply_en: "Switching map to 3D Satellite mode.".to_string(),
            reply_pl: "Przełączam mapę na tryb satelitarny 3D.".to_string(),
        };
    }
    if folded.contains("ciemna mapa") || folded.contains("dark map") || folded.contains("night map")
    {
        return VoiceCommandResult {
            intent: VoiceIntentType::MapDarkMode,
            target: String::new(),
            detected_lang: lang,
            eco_priority: false,
            reply_en: "Switching map to AMOLED Night mode.".to_string(),
            reply_pl: "Przełączam mapę na tryb nocny AMOLED.".to_string(),
        };
    }

    // 3. Reset km counter
    if (folded.contains("reset") || folded.contains("wyzeruj") || folded.contains("zeruj"))
        && (folded.contains("km")
            || folded.contains("licznik")
            || folded.contains("trip")
            || folded.contains("kilometr"))
    {
        return VoiceCommandResult {
            intent: VoiceIntentType::ResetKmCounter,
            target: String::new(),
            detected_lang: lang,
            eco_priority: false,
            reply_en: "Trip kilometer counter reset.".to_string(),
            reply_pl: "Licznik kilometrów został wyzerowany.".to_string(),
        };
    }

    // 4. Weather
    if folded.contains("weather") || folded.contains("pogoda") || folded.contains("temperatura") {
        return VoiceCommandResult {
            intent: VoiceIntentType::ShowWeather,
            target: String::new(),
            detected_lang: lang,
            eco_priority: false,
            reply_en: "Opening weather forecast and road conditions.".to_string(),
            reply_pl: "Otwieram pogodę i warunki na drodze.".to_string(),
        };
    }

    // 5. Calendar
    if folded.contains("calendar")
        || folded.contains("kalendarz")
        || folded.contains("events")
        || folded.contains("wydarzenia")
    {
        return VoiceCommandResult {
            intent: VoiceIntentType::ShowCalendar,
            target: String::new(),
            detected_lang: lang,
            eco_priority: false,
            reply_en: "Opening calendar taskbar.".to_string(),
            reply_pl: "Otwieram pasek kalendarza.".to_string(),
        };
    }

    // 6. Layout adjustment
    if folded.contains("layout")
        || folded.contains("uklad")
        || folded.contains("focus mode")
        || folded.contains("split mode")
    {
        let mode = if folded.contains("focus") || folded.contains("pelny") {
            "focus"
        } else if folded.contains("media") {
            "media"
        } else {
            "split"
        };
        return VoiceCommandResult {
            intent: VoiceIntentType::SwitchLayout,
            target: mode.to_string(),
            detected_lang: lang,
            eco_priority: false,
            reply_en: "Adjusting CarPlay layout mode.".to_string(),
            reply_pl: "Dostosowuję układ CarPlay.".to_string(),
        };
    }

    // 7. YouTube
    if folded.contains("youtube") || folded.contains("you tube") {
        let query = extract_after_trigger(
            trimmed,
            &[
                "search youtube for",
                "play on youtube",
                "open youtube and play",
                "szukaj na youtube",
                "wlacz na youtube",
                "otworz youtube",
                "wlacz youtube",
                "open youtube",
                "youtube",
                "you tube",
            ],
        )
        .unwrap_or_default();
        let reply_en = if query.is_empty() {
            "Opening YouTube.".to_string()
        } else {
            format!("Searching YouTube for {}", query)
        };
        let reply_pl = if query.is_empty() {
            "Otwieram YouTube.".to_string()
        } else {
            format!("Szukam na YouTube: {}", query)
        };
        return VoiceCommandResult {
            intent: VoiceIntentType::OpenYouTube,
            target: query,
            detected_lang: lang,
            eco_priority: false,
            reply_en,
            reply_pl,
        };
    }

    // 8. Navigation / Maps ("give me the map to ...", "daj mi mapę do ...")
    let eco_priority = folded.contains("fuel")
        || folded.contains("eco")
        || folded.contains("paliw")
        || folded.contains("oszczed")
        || folded.contains("eko");

    let nav_triggers = [
        // Polish longest prefixes first
        "najlepsza trasa paliwowa do",
        "pokaz oszczedna trase do",
        "oszczedna trasa do",
        "wyznacz trase do",
        "pokaz trase do",
        "daj mi mape do",
        "pokaz mi mape do",
        "pokaz mape do",
        "mapa do",
        "trasa eko do",
        "trasa do",
        "prowadz do",
        "nawiguj do",
        "jedz do",
        "jedziemy do",
        "kieruj do",
        "gdzie jest",
        // English longest prefixes first
        "best fuel saving route to",
        "fuel saving route to",
        "give me the map to",
        "show me the map to",
        "give me map to",
        "show map to",
        "eco route to",
        "directions to",
        "navigate to",
        "take me to",
        "drive me to",
        "drive to",
        "route to",
        "map to",
        "go to",
    ];

    if let Some(dest) = extract_after_trigger(trimmed, &nav_triggers) {
        return VoiceCommandResult {
            intent: VoiceIntentType::Navigate,
            target: dest.clone(),
            detected_lang: lang,
            eco_priority: true, // Default fuel-saving best track prioritized!
            reply_en: format!("Finding best fuel-saving track to {}", dest),
            reply_pl: format!(
                "Wyznaczam najlepszą trasę oszczędzającą paliwo do: {}",
                dest
            ),
        };
    }

    // 9. Music / Spotify controls
    if folded == "pause"
        || folded == "pauza"
        || folded.contains("pause music")
        || folded.contains("zatrzymaj muzyke")
        || folded.contains("stop music")
    {
        return VoiceCommandResult {
            intent: VoiceIntentType::MusicPause,
            target: String::new(),
            detected_lang: lang,
            eco_priority: false,
            reply_en: "Pausing music.".to_string(),
            reply_pl: "Wstrzymuję muzykę.".to_string(),
        };
    }
    if folded == "resume"
        || folded == "wznow"
        || folded.contains("wznow muzyke")
        || folded.contains("resume music")
    {
        return VoiceCommandResult {
            intent: VoiceIntentType::MusicResume,
            target: String::new(),
            detected_lang: lang,
            eco_priority: false,
            reply_en: "Resuming music.".to_string(),
            reply_pl: "Wznawiam odtwarzanie.".to_string(),
        };
    }
    if folded.contains("next track")
        || folded.contains("next song")
        || folded == "next"
        || folded == "skip"
        || folded.contains("nastepny utwor")
        || folded.contains("nastepna piosenka")
        || folded == "nastepny"
        || folded == "pomin"
    {
        return VoiceCommandResult {
            intent: VoiceIntentType::MusicNext,
            target: String::new(),
            detected_lang: lang,
            eco_priority: false,
            reply_en: "Skipping to next track.".to_string(),
            reply_pl: "Przełączam na następny utwór.".to_string(),
        };
    }
    if folded.contains("previous")
        || folded.contains("prev")
        || folded.contains("poprzedni")
        || folded.contains("poprzednia")
    {
        return VoiceCommandResult {
            intent: VoiceIntentType::MusicPrev,
            target: String::new(),
            detected_lang: lang,
            eco_priority: false,
            reply_en: "Going to previous track.".to_string(),
            reply_pl: "Wracam do poprzedniego utworu.".to_string(),
        };
    }

    if let Some(song) = extract_after_trigger(
        trimmed,
        &[
            "odtworz",
            "wlacz muzyke",
            "zagraj",
            "graj",
            "play music",
            "play song",
            "play",
        ],
    ) {
        return VoiceCommandResult {
            intent: VoiceIntentType::MusicPlay,
            target: song.clone(),
            detected_lang: lang,
            eco_priority: false,
            reply_en: format!("Playing {}", song),
            reply_pl: format!("Odtwarzam: {}", song),
        };
    }
    if folded.contains("wlacz muzyke")
        || folded.contains("play music")
        || folded == "muzyka"
        || folded == "music"
    {
        return VoiceCommandResult {
            intent: VoiceIntentType::MusicPlay,
            target: String::new(),
            detected_lang: lang,
            eco_priority: false,
            reply_en: "Starting music player.".to_string(),
            reply_pl: "Uruchamiam odtwarzacz muzyki.".to_string(),
        };
    }

    VoiceCommandResult {
        intent: VoiceIntentType::Unknown,
        target: trimmed.to_string(),
        detected_lang: lang,
        eco_priority,
        reply_en: format!("Unknown command: {}", trimmed),
        reply_pl: format!("Nie rozpoznano polecenia: {}", trimmed),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_english_and_polish_navigation() {
        let en = parse_voice_command("Give me the map to Central Station", "auto");
        assert_eq!(en.intent, VoiceIntentType::Navigate);
        assert_eq!(en.target, "Central Station");
        assert_eq!(en.detected_lang, "en");

        let pl = parse_voice_command("Daj mi mapę do Kraków Główny", "auto");
        assert_eq!(pl.intent, VoiceIntentType::Navigate);
        assert_eq!(pl.target, "Kraków Główny");
        assert_eq!(pl.detected_lang, "pl");

        let pl_eco = parse_voice_command(
            "Prowadź oszczędną trasą: najlepsza trasa paliwowa do Gdańsk",
            "auto",
        );
        assert_eq!(pl_eco.intent, VoiceIntentType::Navigate);
        assert_eq!(pl_eco.target, "Gdańsk");
        assert!(pl_eco.eco_priority);
    }

    #[test]
    fn parses_polish_media_game_weather_calendar() {
        assert_eq!(
            parse_voice_command("Zagraj w Flappy Bird bo nudzi mi się", "auto").intent,
            VoiceIntentType::OpenFlappyBird
        );
        let yt = parse_voice_command("Otwórz YouTube lofi hip hop", "auto");
        assert_eq!(yt.intent, VoiceIntentType::OpenYouTube);
        assert_eq!(yt.target, "lofi hip hop");

        assert_eq!(
            parse_voice_command("Jaka jest pogoda", "auto").intent,
            VoiceIntentType::ShowWeather
        );
        assert_eq!(
            parse_voice_command("Tryb satelitarny 3D", "auto").intent,
            VoiceIntentType::MapSatellite3d
        );
        assert_eq!(
            parse_voice_command("Wyzeruj licznik kilometrów", "auto").intent,
            VoiceIntentType::ResetKmCounter
        );

        let play_pl = parse_voice_command("Odtwórz Taco Hemingway", "auto");
        assert_eq!(play_pl.intent, VoiceIntentType::MusicPlay);
        assert_eq!(play_pl.target, "Taco Hemingway");

        let layout = parse_voice_command("Włącz tryb focus layout", "auto");
        assert_eq!(layout.intent, VoiceIntentType::SwitchLayout);
        assert_eq!(layout.target, "focus");
    }
}
