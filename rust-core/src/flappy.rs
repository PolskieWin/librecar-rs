/// 240 TPS (Ticks Per Second) fixed-timestep Flappy Bird physics engine.
/// Tuned for lighter, floaty gravity and wide, fair pipe gaps for relaxed CarPlay breaks.

const BIRD_X: f32 = 0.24;
const BIRD_RADIUS: f32 = 0.025;
const PIPE_WIDTH: f32 = 0.105;
const PIPE_SPEED: f32 = 0.29;
const PIPE_SPACING: f32 = 0.45;
const GRAVITY: f32 = 1.38;
const FLAP_VELOCITY: f32 = -0.47;
const GROUND_Y: f32 = 0.90;
const NUM_PIPES: usize = 4;
const FIXED_DT_240_HZ: f32 = 1.0 / 240.0;

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
    accumulator: f32,
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
            gap_half: 0.175,
            pipes: [Pipe {
                x: 1.0,
                gap_y: 0.5,
                scored: false,
            }; NUM_PIPES],
            rng_state: 0x5DEECE66D,
            accumulator: 0.0,
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
        self.gap_half = 0.175;
        self.accumulator = 0.0;
        self.reset_pipes();
    }

    fn reset_pipes(&mut self) {
        for i in 0..NUM_PIPES {
            let gy = self.next_gap_y();
            self.pipes[i] = Pipe {
                x: 1.02 + (i as f32) * PIPE_SPACING,
                gap_y: gy,
                scored: false,
            };
        }
    }

    fn next_gap_y(&mut self) -> f32 {
        self.rng_state = self
            .rng_state
            .wrapping_mul(6364136223846793005)
            .wrapping_add(1442695040888963407);
        let unit = ((self.rng_state >> 33) as f32) / (u32::MAX as f32 * 0.5);
        0.27 + (unit.clamp(0.0, 1.0)) * 0.38
    }

    /// Advances simulation using a 240 TPS fixed-step accumulator (`1/240s` per physics tick).
    /// Output layout (15 floats):
    /// [0]=birdY, [1]=birdVy, [2]=birdRotDeg, [3]=score, [4]=bestScore, [5]=state(0/1/2),
    /// [6..14]=pipe[0..3](x, gapY), [14]=gapHalf
    pub fn step(&mut self, dt_sec: f32, flap: bool) -> [f32; 15] {
        let clamped_dt = dt_sec.clamp(0.0, 0.05);

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
            self.accumulator += clamped_dt;
            // Execute exact 240 Hz physics ticks
            while self.accumulator >= FIXED_DT_240_HZ {
                self.accumulator -= FIXED_DT_240_HZ;
                self.tick_240hz(FIXED_DT_240_HZ);
                if self.state != GameState::Playing {
                    self.accumulator = 0.0;
                    break;
                }
            }
        }

        let rot_deg = (self.bird_vy * 42.0).clamp(-26.0, 58.0);
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

    #[inline]
    fn tick_240hz(&mut self, dt: f32) {
        self.bird_vy = (self.bird_vy + GRAVITY * dt).clamp(-0.95, 1.05);
        self.bird_y += self.bird_vy * dt;

        // Easy, wide gap that gently tightens with high scores
        self.gap_half = (0.175 - (self.score as f32 * 0.0012)).max(0.135);

        let mut max_x = 0.0_f32;
        for p in &self.pipes {
            if p.x > max_x {
                max_x = p.x;
            }
        }

        let speed = PIPE_SPEED + (self.score as f32 * 0.0025).min(0.10);
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
                self.pipes[i].x = (max_x + PIPE_SPACING).max(1.05);
                self.pipes[i].gap_y = next_y;
                self.pipes[i].scored = false;
                max_x = self.pipes[i].x;
            }
        }

        if self.bird_y + BIRD_RADIUS >= GROUND_Y {
            self.bird_y = GROUND_Y - BIRD_RADIUS;
            self.state = GameState::GameOver;
            return;
        }
        if self.bird_y - BIRD_RADIUS < 0.02 {
            self.bird_y = 0.02 + BIRD_RADIUS;
            self.bird_vy = 0.0;
        }

        // Forgiving circle-vs-pipe hitbox
        let hit_r = BIRD_RADIUS * 0.72;
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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn runs_240tps_physics_and_flies_through_pipes() {
        let mut game = FlappyEngine::new();
        // Start with initial flap and simulate guided play at 60fps -> 240 TPS internal ticks
        let mut state = game.step(0.016, true);
        assert_eq!(state[5], 1.0);

        for _ in 0..450 {
            // Find nearest pipe ahead of bird
            let mut target_y = 0.48_f32;
            let mut min_dx = 10.0_f32;
            for i in 0..4 {
                let px = state[6 + i * 2];
                let gy = state[7 + i * 2];
                if px + PIPE_WIDTH >= BIRD_X && px < min_dx {
                    min_dx = px;
                    target_y = gy;
                }
            }
            let should_flap = state[0] > target_y + 0.025 && state[1] > -0.10;
            state = game.step(1.0 / 60.0, should_flap);
        }

        assert_eq!(
            state[5], 1.0,
            "Should still be alive with guided light physics"
        );
        assert!(
            state[3] >= 2.0,
            "Should score >= 2 pipes, scored {}",
            state[3]
        );
    }
}
