/// Zero-allocation audio bar equalizer generator for CarPlay Music deck.
pub fn step_visualizer(bars: &mut [f32; 16], playing: bool, phase_ms: i64, dt_sec: f32) {
    let dt = dt_sec.clamp(0.005, 0.1);
    let t = (phase_ms as f32) * 0.0045;
    for (i, b) in bars.iter_mut().enumerate() {
        let target = if playing {
            let fi = i as f32;
            let wave1 = (t * 1.3 + fi * 0.55).sin().abs();
            let wave2 = (t * 2.7 - fi * 0.35).cos().abs();
            (0.18 + 0.55 * wave1 + 0.27 * wave2).clamp(0.12, 1.0)
        } else {
            0.08
        };
        let speed = if target > *b { 14.0 } else { 6.5 };
        *b += (target - *b) * (speed * dt).clamp(0.0, 1.0);
    }
}
