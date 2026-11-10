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
    ToggleLoop,
    #[serde(rename = "open_youtube")]
    OpenYouTube,
    OpenFlappyBird,
    OpenGeometryDash,
    OpenRadio,
    #[serde(rename = "map_satellite_3d")]
    MapSatellite3d,
    ShowWeather,
    ShowCalendar,
    ResetKmCounter,
    SwitchLayout,
    FillTank,
    CheckFuelRange,
    SetFuelLiters,
    AskAi,
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
            if c.is_alphanumeric() || c.is_whitespace() || c == '.' {
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
    if raw.chars().any(|c| "ąćęłńóśźżĄĆĘŁŃÓŚŹŻ".contains(c)) {
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
        "paliwo",
        "paliwa",
        "tankuje",
        "zasieg",
        "dojade",
    ];
    pl_keywords.iter().any(|k| folded.contains(k))
}

fn extract_after_trigger(raw: &str, triggers: &[&str]) -> Option<String> {
    let raw_trimmed = raw.trim();
    let folded_raw = fold_polish_ascii(raw_trimmed);

    for trig in triggers {
        if let Some(pos) = folded_raw.find(trig) {
            let after_folded = folded_raw[pos + trig.len()..].trim();
            if after_folded.is_empty() {
                continue;
            }
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
            reply_en: "Say a command, e.g. Give me the map to Warsaw.".to_string(),
            reply_pl: "Powiedz polecenie, np.: Daj mi mapę do Warszawa.".to_string(),
        };
    }

    // Fuel / Range commands
    if folded.contains("full tank")
        || folded.contains("tankuje do pelna")
        || folded.contains("zatankowalem do pelna")
        || folded.contains("pelny bak")
    {
        return VoiceCommandResult {
            intent: VoiceIntentType::FillTank,
            target: String::new(),
            detected_lang: lang,
            eco_priority: false,
            reply_en: "Fuel tank reset to full.".to_string(),
            reply_pl: "Ustawiono pełny bak paliwa.".to_string(),
        };
    }

    if (folded.contains("set fuel")
        || folded.contains("ustaw paliwo")
        || folded.contains("mam paliwa"))
        && folded.chars().any(|c| c.is_ascii_digit())
    {
        let num_str: String = folded
            .chars()
            .filter(|c| c.is_ascii_digit() || *c == '.')
            .collect();
        if let Ok(liters) = num_str.parse::<f64>() {
            return VoiceCommandResult {
                intent: VoiceIntentType::SetFuelLiters,
                target: format!("{:.1}", liters),
                detected_lang: lang,
                eco_priority: false,
                reply_en: format!("Fuel level set to {:.1} liters.", liters),
                reply_pl: format!("Ustawiono poziom paliwa: {:.1} litra.", liters),
            };
        }
    }

    if folded.contains("fuel range")
        || folded.contains("ile mam paliwa")
        || folded.contains("ile zasiegu")
        || folded.contains("czy dojade")
        || folded.contains("can i reach")
    {
        return VoiceCommandResult {
            intent: VoiceIntentType::CheckFuelRange,
            target: String::new(),
            detected_lang: lang,
            eco_priority: false,
            reply_en: "Checking fuel level and range.".to_string(),
            reply_pl: "Sprawdzam poziom paliwa i zasięg.".to_string(),
        };
    }

    // Geometry Dash
    if folded.contains("geometry dash")
        || folded.contains("play dash")
        || folded.contains("open dash")
        || folded.contains("wlacz dash")
        || folded.contains("zagraj w dash")
        || folded == "dash"
    {
        return VoiceCommandResult {
            intent: VoiceIntentType::OpenGeometryDash,
            target: String::new(),
            detected_lang: lang,
            eco_priority: false,
            reply_en: "Opening Geometry Dash.".to_string(),
            reply_pl: "Otwieram Geometry Dash.".to_string(),
        };
    }

    // Flappy Bird
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
            reply_en: "Opening Flappy Bird.".to_string(),
            reply_pl: "Otwieram Flappy Bird.".to_string(),
        };
    }

    // Map styles
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
            reply_en: "Switching map to 3D Satellite.".to_string(),
            reply_pl: "Przełączam mapę na tryb satelitarny 3D.".to_string(),
        };
    }

    // Radio
    if folded.contains("radio")
        && !folded.contains("youtube")
    {
        let station = extract_after_trigger(
            trimmed,
            &["wlacz radio", "otworz radio", "play radio", "open radio", "radio"],
        )
        .unwrap_or_default();
        return VoiceCommandResult {
            intent: VoiceIntentType::OpenRadio,
            target: station.clone(),
            detected_lang: lang,
            eco_priority: false,
            reply_en: if station.is_empty() {
                "Starting live radio.".to_string()
            } else {
                format!("Tuning radio to {}", station)
            },
            reply_pl: if station.is_empty() {
                "Włączam radio na żywo.".to_string()
            } else {
                format!("Włączam radio: {}", station)
            },
        };
    }

    // Reset km counter
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
            reply_pl: "Licznik kilometrów wyzerowany.".to_string(),
        };
    }

    // Weather
    if folded.contains("weather") || folded.contains("pogoda") || folded.contains("temperatura") {
        return VoiceCommandResult {
            intent: VoiceIntentType::ShowWeather,
            target: String::new(),
            detected_lang: lang,
            eco_priority: false,
            reply_en: "Opening weather and road advisory.".to_string(),
            reply_pl: "Otwieram pogodę i warunki na drodze.".to_string(),
        };
    }

    // Calendar
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
            reply_en: "Opening calendar.".to_string(),
            reply_pl: "Otwieram kalendarz.".to_string(),
        };
    }

    // Layout adjustment
    if folded.contains("layout")
        || folded.contains("uklad")
        || folded.contains("ultra compact")
        || folded.contains("ultra kompakt")
        || folded.contains("compact mode")
        || folded.contains("focus mode")
        || folded.contains("split mode")
    {
        let mode = if folded.contains("ultra") {
            "ultra_compact"
        } else if folded.contains("compact") || folded.contains("kompakt") {
            "compact"
        } else if folded.contains("focus") || folded.contains("pelny") {
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
            reply_en: "Adjusting layout mode.".to_string(),
            reply_pl: "Zmieniam układ ekranu.".to_string(),
        };
    }

    // Explicit Built-in AI / Gemini conversational prompts
    let ai_triggers = [
        "ask gemini",
        "ask ai",
        "hey ai",
        "hey gemini",
        "zapytaj ai",
        "zapytaj gemini",
        "gemini ",
        "what is my",
        "whats my",
        "how is my",
        "tell me about",
        "tell me a ",
        "eco tip",
        "driving tip",
        "porada eko",
        "opowiedz",
        "czy starczy",
        "co mam dzisiaj",
        "jakie mam",
        "czy pada",
        "hello",
        "czesc",
        "hej ",
        "calculate ",
        "policz ",
    ];
    if ai_triggers.iter().any(|k| folded.contains(k)) || folded.ends_with("?") || raw_input.trim().ends_with('?') {
        let query = extract_after_trigger(
            trimmed,
            &["ask gemini", "ask ai", "hey gemini", "hey ai", "zapytaj gemini", "zapytaj ai", "gemini"],
        )
        .unwrap_or_else(|| trimmed.to_string());
        return VoiceCommandResult {
            intent: VoiceIntentType::AskAi,
            target: query,
            detected_lang: lang,
            eco_priority: false,
            reply_en: "Thinking...".to_string(),
            reply_pl: "Analizuję...".to_string(),
        };
    }

    // YouTube
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

    // Speed-prioritized navigation
    let speed_nav_triggers = [
        "najszybsza trasa do",
        "najlepsza trasa na czas do",
        "najszybsza droga do",
        "best route for speed to",
        "best speed route to",
        "fastest route to",
        "fastest track to",
        "quickest route to",
    ];
    if let Some(dest) = extract_after_trigger(trimmed, &speed_nav_triggers) {
        return VoiceCommandResult {
            intent: VoiceIntentType::Navigate,
            target: dest.clone(),
            detected_lang: lang,
            eco_priority: false,
            reply_en: format!("Finding fastest route for speed to {}", dest),
            reply_pl: format!("Wyznaczam najszybszą trasę do: {}", dest),
        };
    }

    // Navigation / Maps (fuel-saving eco default)
    let nav_triggers = [
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
            eco_priority: true,
            reply_en: format!("Finding fuel-saving track to {}", dest),
            reply_pl: format!("Wyznaczam oszczędną trasę do: {}", dest),
        };
    }

    // Music / Spotify controls
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
            reply_pl: "Wznawiam muzykę.".to_string(),
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
            reply_en: "Next track.".to_string(),
            reply_pl: "Następny utwór.".to_string(),
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
            reply_en: "Previous track.".to_string(),
            reply_pl: "Poprzedni utwór.".to_string(),
        };
    }

    if folded.contains("loop")
        || folded.contains("repeat")
        || folded.contains("zapetl")
        || folded.contains("powtarzaj")
    {
        return VoiceCommandResult {
            intent: VoiceIntentType::ToggleLoop,
            target: String::new(),
            detected_lang: lang,
            eco_priority: false,
            reply_en: "Toggling music loop mode.".to_string(),
            reply_pl: "Przełączam zapętlanie muzyki.".to_string(),
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
            reply_en: "Opening music player.".to_string(),
            reply_pl: "Włączam muzykę.".to_string(),
        };
    }

    VoiceCommandResult {
        intent: VoiceIntentType::Unknown,
        target: trimmed.to_string(),
        detected_lang: lang,
        eco_priority: false,
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
    }

    #[test]
    fn parses_fuel_and_media_commands() {
        let set_fuel = parse_voice_command("Ustaw paliwo 12.5 litrów", "auto");
        assert_eq!(set_fuel.intent, VoiceIntentType::SetFuelLiters);
        assert_eq!(set_fuel.target, "12.5");

        assert_eq!(
            parse_voice_command("Tankuję do pełna", "auto").intent,
            VoiceIntentType::FillTank
        );
        assert_eq!(
            parse_voice_command("Czy dojadę do celu", "auto").intent,
            VoiceIntentType::CheckFuelRange
        );
        assert_eq!(
            parse_voice_command("Zagraj w Flappy Bird bo nudzi mi się", "auto").intent,
            VoiceIntentType::OpenFlappyBird
        );
        let layout = parse_voice_command("Włącz tryb focus layout", "auto");
        assert_eq!(layout.intent, VoiceIntentType::SwitchLayout);
        assert_eq!(layout.target, "focus");

        let speed_route = parse_voice_command("Best route for speed to Gdansk", "auto");
        assert_eq!(speed_route.intent, VoiceIntentType::Navigate);
        assert!(!speed_route.eco_priority);
        assert_eq!(speed_route.target, "Gdansk");

        let radio = parse_voice_command("Włącz radio RMF FM", "auto");
        assert_eq!(radio.intent, VoiceIntentType::OpenRadio);
        assert_eq!(radio.target, "RMF FM");

        let loop_cmd = parse_voice_command("Zapętl utwór", "auto");
        assert_eq!(loop_cmd.intent, VoiceIntentType::ToggleLoop);

        let dash_cmd = parse_voice_command("Open Geometry Dash", "auto");
        assert_eq!(dash_cmd.intent, VoiceIntentType::OpenGeometryDash);

        let ai_cmd = parse_voice_command("Ask AI how is my eco score?", "auto");
        assert_eq!(ai_cmd.intent, VoiceIntentType::AskAi);
        assert_eq!(ai_cmd.target, "how is my eco score");
    }
}
