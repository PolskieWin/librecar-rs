/// Zero-allocation 60fps Flappy Bird physics engine for Android CarPlay idle entertainment.
/// Coordinates are normalized to [0.0, 1.0] in both X and Y so it adapts cleanly to any screen aspect ratio.

const BIRD_X: f32 = 0.24;
const BIRD_RADIUS: f32 = 0.028;
const PIPE_WIDTH: f32 = 0.11;
const PIPE_SPEED: f32 = 0.42;
const GRAVITY: f32 = 2.35;
const FLAP_VELOCITY: f32 = -0.68;
const GROUND_Y: f32 = 0.90;
const NUM_PIPES: usize = 4;

#[derive(Clone, Copy, PartialEq, Eq)]
pub enum GameState {
    Ready = 0,
    Playing = 1,
    GameOver = 2,
}

#[derive(Clone, Copy)]
struct Pipe {
    x: f32,
    gap_y: f32,
    scored: bool,
}

pub struct FlappyEngine {
    state: GameState,
    bird_y: f32,
    bird_vy: f32,
    score: u32,
    best_score: u32,
    gap_half: f32,
    pipes: [Pipe; NUM_PIPES],
    rng_state: u64,
}

impl Default for FlappyEngine {
    fn default() -> Self {
        Self::new()
    }
}

impl FlappyEngine {
    pub fn new() -> Self {
        let mut engine = Self {
            state: GameState::Ready,
            bird_y: 0.46,
            bird_vy: 0.0,
            score: 0,
            best_score: 0,
            gap_half: 0.135,
            pipes: [Pipe {
                x: 1.0,
                gap_y: 0.5,
                scored: false,
            }; NUM_PIPES],
            rng_state: 0x5DEECE66D,
        };
        engine.reset_pipes();
        engine
    }

    pub fn set_best_score(&mut self, best: u32) {
        if best > self.best_score {
            self.best_score = best;
        }
    }

    pub fn reset(&mut self) {
        self.state = GameState::Ready;
        self.bird_y = 0.46;
        self.bird_vy = 0.0;
        self.score = 0;
        self.gap_half = 0.135;
        self.reset_pipes();
    }

    fn reset_pipes(&mut self) {
        let spacing = 0.36;
        for i in 0..NUM_PIPES {
            let gy = self.next_gap_y();
            self.pipes[i] = Pipe {
                x: 1.05 + (i as f32) * spacing,
                gap_y: gy,
                scored: false,
            };
        }
    }

    fn next_gap_y(&mut self) -> f32 {
        // Fast deterministic LCG — zero dependencies, zero allocations
        self.rng_state = self
            .rng_state
            .wrapping_mul(6364136223846793005)
            .wrapping_add(1442695040888963407);
        let unit = ((self.rng_state >> 33) as f32) / (u32::MAX as f32 * 0.5);
        0.24 + (unit.clamp(0.0, 1.0)) * 0.44
    }

    /// Advances simulation by `dt_sec` and optionally triggers a flap impulse.
    /// Returns fixed 15-float snapshot for direct zero-GC JNI array transfer:
    /// [0]=birdY, [1]=birdVy, [2]=birdRotDeg, [3]=score, [4]=bestScore, [5]=state(0/1/2),
    /// [6]=p0x, [7]=p0gapY, [8]=p1x, [9]=p1gapY, [10]=p2x, [11]=p2gapY, [12]=p3x, [13]=p3gapY,
    /// [14]=gapHalf
    pub fn step(&mut self, dt_sec: f32, flap: bool) -> [f32; 15] {
        let dt = dt_sec.clamp(0.0, 0.05);

        if flap {
            match self.state {
                GameState::Ready => {
                    self.state = GameState::Playing;
                    self.bird_vy = FLAP_VELOCITY;
                }
                GameState::Playing => {
                    self.bird_vy = FLAP_VELOCITY;
                }
                GameState::GameOver => {
                    self.reset();
                    self.state = GameState::Playing;
                    self.bird_vy = FLAP_VELOCITY;
                }
            }
        }

        if self.state == GameState::Playing {
            self.bird_vy = (self.bird_vy + GRAVITY * dt).clamp(-1.2, 1.35);
            self.bird_y += self.bird_vy * dt;

            // Slowly tighten gap as score climbs, capped fairly for touch screens
            self.gap_half = (0.135 - (self.score as f32 * 0.0015)).max(0.095);

            // Track furthest pipe x for wrapping
            let mut max_x = 0.0_f32;
            for p in &self.pipes {
                if p.x > max_x {
                    max_x = p.x;
                }
            }

            let speed = PIPE_SPEED + (self.score as f32 * 0.004).min(0.16);
            for i in 0..NUM_PIPES {
                self.pipes[i].x -= speed * dt;
                if !self.pipes[i].scored && (self.pipes[i].x + PIPE_WIDTH * 0.5) < BIRD_X {
                    self.pipes[i].scored = true;
                    self.score += 1;
                    if self.score > self.best_score {
                        self.best_score = self.score;
                    }
                }
                if self.pipes[i].x < -PIPE_WIDTH {
                    let next_y = self.next_gap_y();
                    self.pipes[i].x = (max_x + 0.36).max(1.05);
                    self.pipes[i].gap_y = next_y;
                    self.pipes[i].scored = false;
                    max_x = self.pipes[i].x;
                }
            }

            // Ground & ceiling collision
            if self.bird_y + BIRD_RADIUS >= GROUND_Y {
                self.bird_y = GROUND_Y - BIRD_RADIUS;
                self.state = GameState::GameOver;
            } else if self.bird_y - BIRD_RADIUS < 0.02 {
                self.bird_y = 0.02 + BIRD_RADIUS;
                self.bird_vy = 0.0;
            } else {
                // Pipe collision (slightly forgiving inner radius)
                let hit_r = BIRD_RADIUS * 0.82;
                for p in &self.pipes {
                    let in_x = BIRD_X + hit_r > p.x && BIRD_X - hit_r < p.x + PIPE_WIDTH;
                    if in_x {
                        let gap_top = p.gap_y - self.gap_half;
                        let gap_bot = p.gap_y + self.gap_half;
                        if (self.bird_y - hit_r) < gap_top || (self.bird_y + hit_r) > gap_bot {
                            self.state = GameState::GameOver;
                            break;
                        }
                    }
                }
            }
        }

        let rot_deg = (self.bird_vy * 48.0).clamp(-32.0, 68.0);
        [
            self.bird_y,
            self.bird_vy,
            rot_deg,
            self.score as f32,
            self.best_score as f32,
            (self.state as u8) as f32,
            self.pipes[0].x,
            self.pipes[0].gap_y,
            self.pipes[1].x,
            self.pipes[1].gap_y,
            self.pipes[2].x,
            self.pipes[2].gap_y,
            self.pipes[3].x,
            self.pipes[3].gap_y,
            self.gap_half,
        ]
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn flap_starts_game_and_gravity_pulls_down() {
        let mut game = FlappyEngine::new();
        let s0 = game.step(0.016, true);
        assert_eq!(s0[5], 1.0); // Playing
        let initial_y = s0[0];
        let s1 = game.step(0.016, false);
        assert!(s1[0] < initial_y); // Moving up right after flap
        for _ in 0..80 {
            game.step(0.02, false);
        }
        let s_end = game.step(0.02, false);
        assert_eq!(s_end[5], 2.0); // Hit ground -> GameOver
    }
}
