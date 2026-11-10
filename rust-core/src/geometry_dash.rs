/// 240 TPS (Ticks Per Second) fixed-timestep Geometry Dash cube runner engine.
/// Supports tap & hold-to-chain-jump, solid blocks to land on cleanly, and crisp spike obstacles.

pub const DASH_CUBE_X: f32 = 0.22;
pub const DASH_CUBE_SIZE: f32 = 0.056;
pub const DASH_GROUND_Y: f32 = 0.80;
const DASH_GRAVITY: f32 = 3.10;
const DASH_JUMP_VEL: f32 = -0.98;
const DASH_BASE_SPEED: f32 = 0.42;
const DASH_MAX_SPEED: f32 = 0.60;
const FIXED_DT_240_HZ: f32 = 1.0 / 240.0;
const NUM_OBS: usize = 6;

#[derive(Clone, Copy, PartialEq, Eq)]
pub enum DashState {
    Ready = 0,
    Playing = 1,
    GameOver = 2,
}

#[derive(Clone, Copy)]
pub struct Obstacle {
    pub x: f32,
    pub w: f32,
    pub h: f32,
    /// 0 = single spike, 1 = solid block (landable top!), 2 = double spike
    pub kind: u8,
    pub scored: bool,
}

pub struct GeometryDashEngine {
    state: DashState,
    /// Bottom edge Y of cube (0.0 top .. DASH_GROUND_Y floor)
    cube_bottom_y: f32,
    cube_vy: f32,
    cube_rot_deg: f32,
    on_ground: bool,
    score: u32,
    best_score: u32,
    attempt: u32,
    speed: f32,
    scroll_phase: f32,
    obstacles: [Obstacle; NUM_OBS],
    rng_state: u64,
    accumulator: f32,
}

impl Default for GeometryDashEngine {
    fn default() -> Self {
        Self::new()
    }
}

impl GeometryDashEngine {
    pub fn new() -> Self {
        let mut engine = Self {
            state: DashState::Ready,
            cube_bottom_y: DASH_GROUND_Y,
            cube_vy: 0.0,
            cube_rot_deg: 0.0,
            on_ground: true,
            score: 0,
            best_score: 0,
            attempt: 1,
            speed: DASH_BASE_SPEED,
            scroll_phase: 0.0,
            obstacles: [Obstacle {
                x: 1.5,
                w: 0.05,
                h: 0.07,
                kind: 0,
                scored: false,
            }; NUM_OBS],
            rng_state: 0x1337_BEEF_CAFE_7777,
            accumulator: 0.0,
        };
        engine.reset_obstacles();
        engine
    }

    pub fn set_best_score(&mut self, best: u32) {
        if best > self.best_score {
            self.best_score = best;
        }
    }

    pub fn reset(&mut self) {
        self.state = DashState::Ready;
        self.cube_bottom_y = DASH_GROUND_Y;
        self.cube_vy = 0.0;
        self.cube_rot_deg = 0.0;
        self.on_ground = true;
        self.score = 0;
        self.speed = DASH_BASE_SPEED;
        self.accumulator = 0.0;
        self.reset_obstacles();
    }

    fn next_rand_u32(&mut self) -> u32 {
        self.rng_state = self
            .rng_state
            .wrapping_mul(6364136223846793005)
            .wrapping_add(1442695040888963407);
        (self.rng_state >> 32) as u32
    }

    fn make_obstacle(&mut self, x: f32) -> Obstacle {
        let roll = self.next_rand_u32() % 10;
        match roll {
            0..=3 => Obstacle {
                x,
                w: 0.050,
                h: 0.072,
                kind: 0, // single spike
                scored: false,
            },
            4..=6 => Obstacle {
                x,
                w: 0.078,
                h: 0.070,
                kind: 1, // solid jump-on block
                scored: false,
            },
            _ => Obstacle {
                x,
                w: 0.088,
                h: 0.068,
                kind: 2, // double spike
                scored: false,
            },
        }
    }

    fn reset_obstacles(&mut self) {
        let mut cur_x = 0.96_f32;
        for i in 0..NUM_OBS {
            self.obstacles[i] = self.make_obstacle(cur_x);
            let gap = 0.34 + ((self.next_rand_u32() % 16) as f32) * 0.01;
            cur_x += gap;
        }
    }

    /// Output layout (27 floats):
    /// [0]=cubeBottomY, [1]=cubeRotDeg, [2]=score, [3]=bestScore, [4]=state(0/1/2),
    /// [5]=attempt, [6]=scrollPhase(0..1),
    /// [7..25]=6*(x, w, h_and_kind_packed),Wait -> pack clearly: 27 floats too small for 4-per-obs, make 31 floats cleanly!
    /// Better: Output layout (31 floats):
    /// [0]=cubeBottomY, [1]=cubeRotDeg, [2]=score, [3]=bestScore, [4]=state, [5]=attempt, [6]=scrollPhase,
    /// [7..31]=6*(x, w, h, kind) -> 6*4 = 24 floats -> 7+24 = 31 floats total!
    pub fn step(&mut self, dt_sec: f32, hold_jump: bool) -> [f32; 31] {
        let clamped_dt = dt_sec.clamp(0.0, 0.05);

        if hold_jump {
            match self.state {
                DashState::Ready => {
                    self.state = DashState::Playing;
                    self.cube_vy = DASH_JUMP_VEL;
                    self.on_ground = false;
                }
                DashState::GameOver => {
                    self.attempt += 1;
                    self.reset();
                    self.state = DashState::Playing;
                    self.cube_vy = DASH_JUMP_VEL;
                    self.on_ground = false;
                }
                DashState::Playing => {}
            }
        }

        if self.state == DashState::Playing {
            self.accumulator += clamped_dt;
            while self.accumulator >= FIXED_DT_240_HZ {
                self.accumulator -= FIXED_DT_240_HZ;
                self.tick_240hz(FIXED_DT_240_HZ, hold_jump);
                if self.state != DashState::Playing {
                    self.accumulator = 0.0;
                    break;
                }
            }
        }

        let mut out = [0.0_f32; 31];
        out[0] = self.cube_bottom_y;
        out[1] = self.cube_rot_deg;
        out[2] = self.score as f32;
        out[3] = self.best_score as f32;
        out[4] = (self.state as u8) as f32;
        out[5] = self.attempt as f32;
        out[6] = self.scroll_phase;
        for (i, obs) in self.obstacles.iter().enumerate() {
            let base = 7 + i * 4;
            out[base] = obs.x;
            out[base + 1] = obs.w;
            out[base + 2] = obs.h;
            out[base + 3] = obs.kind as f32;
        }
        out
    }

    fn tick_240hz(&mut self, dt: f32, hold_jump: bool) {
        if hold_jump && self.on_ground {
            self.cube_vy = DASH_JUMP_VEL;
            self.on_ground = false;
        }

        let prev_bottom = self.cube_bottom_y;
        self.cube_vy += DASH_GRAVITY * dt;
        self.cube_bottom_y += self.cube_vy * dt;

        self.scroll_phase = (self.scroll_phase + self.speed * dt * 2.2) % 1.0;

        let mut landed_surface_y: Option<f32> = None;
        if self.cube_bottom_y >= DASH_GROUND_Y {
            landed_surface_y = Some(DASH_GROUND_Y);
        }

        let cube_left = DASH_CUBE_X - DASH_CUBE_SIZE * 0.36;
        let cube_right = DASH_CUBE_X + DASH_CUBE_SIZE * 0.36;

        let mut max_obs_x = 0.0_f32;
        for obs in &self.obstacles {
            if obs.x > max_obs_x {
                max_obs_x = obs.x;
            }
        }

        let mut scored_now = false;
        let mut crashed = false;

        for obs in &mut self.obstacles {
            obs.x -= self.speed * dt;
            if !obs.scored && obs.x + obs.w < cube_left {
                obs.scored = true;
                self.score += 1;
                scored_now = true;
            }

            let obs_left = obs.x + obs.w * 0.14;
            let obs_right = obs.x + obs.w * 0.86;
            let obs_top = DASH_GROUND_Y - obs.h;

            if cube_right > obs_left && cube_left < obs_right {
                if obs.kind == 1 {
                    // Solid block: safe to land smoothly on top surface!
                    if self.cube_vy >= 0.0 && prev_bottom <= obs_top + 0.022 && self.cube_bottom_y >= obs_top {
                        let surf = landed_surface_y.unwrap_or(DASH_GROUND_Y);
                        if obs_top <= surf {
                            landed_surface_y = Some(obs_top);
                        }
                    } else if self.cube_bottom_y > obs_top + 0.015 {
                        // Side impact on block front
                        crashed = true;
                    }
                } else {
                    // Spikes (single/double): forgiving apex hitbox
                    let spike_hitbox_top = DASH_GROUND_Y - obs.h * 0.78;
                    if self.cube_bottom_y > spike_hitbox_top {
                        crashed = true;
                    }
                }
            }
        }

        if let Some(surface) = landed_surface_y {
            self.cube_bottom_y = surface;
            self.cube_vy = 0.0;
            self.on_ground = true;
            // Snap cube rotation cleanly to nearest 90 degrees upon landing
            self.cube_rot_deg = (self.cube_rot_deg / 90.0).round() * 90.0;
        } else {
            self.on_ground = false;
            self.cube_rot_deg = (self.cube_rot_deg + 360.0 * dt * 1.45) % 360.0;
        }

        if crashed {
            self.state = DashState::GameOver;
            if self.score > self.best_score {
                self.best_score = self.score;
            }
            return;
        }

        if scored_now {
            if self.score > self.best_score {
                self.best_score = self.score;
            }
            self.speed = (DASH_BASE_SPEED + (self.score as f32) * 0.005).min(DASH_MAX_SPEED);
        }

        for i in 0..NUM_OBS {
            if self.obstacles[i].x + self.obstacles[i].w < -0.10 {
                let mut furthest = 0.85_f32;
                for o in &self.obstacles {
                    if o.x > furthest {
                        furthest = o.x;
                    }
                }
                let gap = 0.34 + ((self.next_rand_u32() % 18) as f32) * 0.01;
                self.obstacles[i] = self.make_obstacle(furthest + gap);
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn runs_240tps_dash_physics_jumps_and_scores() {
        let mut engine = GeometryDashEngine::new();
        let s0 = engine.step(0.0, false);
        assert_eq!(s0[4], 0.0);

        let mut cleared = 0u32;
        // Play smartly: jump whenever an obstacle gets within trigger zone
        for step_idx in 0..720 {
            let snap = engine.step(1.0 / 120.0, step_idx == 0);
            if snap[4] == 2.0 {
                break;
            }
            cleared = snap[2] as u32;
            let mut danger = false;
            for i in 0..NUM_OBS {
                let ox = snap[7 + i * 4];
                if ox > DASH_CUBE_X && ox - DASH_CUBE_X < 0.14 {
                    danger = true;
                }
            }
            if danger && engine.on_ground {
                let _ = engine.step(1.0 / 240.0, true);
            }
        }
        assert!(cleared >= 1, "Expected smart jumper to clear obstacles, cleared={cleared}");
    }
}
