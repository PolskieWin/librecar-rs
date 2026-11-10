use serde::{Deserialize, Serialize};

const EARTH_RADIUS_M: f64 = 6_371_000.0;
const MIN_MOVING_SPEED_KMH: f64 = 1.8;
const MAX_PLAUSIBLE_SPEED_KMH: f64 = 320.0;
const MAX_ACCEPTABLE_ACCURACY_M: f64 = 55.0;
const MIN_DELTA_METERS_STATIONARY: f64 = 2.5;

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct PersistedTelemetry {
    pub trip_a_km: f64,
    pub trip_b_km: f64,
    pub odometer_km: f64,
    pub max_speed_kmh: f64,
    pub moving_seconds: f64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TelemetrySnapshot {
    pub speed_kmh: f64,
    pub avg_speed_kmh: f64,
    pub max_speed_kmh: f64,
    pub trip_a_km: f64,
    pub trip_b_km: f64,
    pub odometer_km: f64,
    pub moving_seconds: u64,
    pub remaining_km: f64,
    pub eta_minutes: u32,
    pub eco_drive_score: u8,
    pub bearing_deg: f32,
    pub altitude_m: f64,
}

pub struct TelemetryEngine {
    persisted: PersistedTelemetry,
    last_lat: Option<f64>,
    last_lon: Option<f64>,
    last_alt: f64,
    last_ts_ms: Option<i64>,
    smoothed_speed_kmh: f64,
    last_bearing_deg: f32,
    remaining_meters: f64,
    osrm_remaining_seconds: f64,
    eco_score: f64,
}

impl TelemetryEngine {
    pub fn new(saved_json: Option<&str>) -> Self {
        let persisted = saved_json
            .and_then(|s| serde_json::from_str::<PersistedTelemetry>(s).ok())
            .unwrap_or_default();
        Self {
            persisted,
            last_lat: None,
            last_lon: None,
            last_alt: 0.0,
            last_ts_ms: None,
            smoothed_speed_kmh: 0.0,
            last_bearing_deg: 0.0,
            remaining_meters: 0.0,
            osrm_remaining_seconds: 0.0,
            eco_score: 92.0,
        }
    }

    pub fn set_route_remaining(&mut self, remaining_meters: f64, expected_duration_sec: f64) {
        self.remaining_meters = remaining_meters.max(0.0);
        self.osrm_remaining_seconds = expected_duration_sec.max(0.0);
    }

    pub fn reset_trip_a(&mut self) {
        self.persisted.trip_a_km = 0.0;
        self.persisted.max_speed_kmh = 0.0;
        self.persisted.moving_seconds = 0.0;
    }

    pub fn reset_trip_b(&mut self) {
        self.persisted.trip_b_km = 0.0;
    }

    pub fn export_persisted_json(&self) -> String {
        serde_json::to_string(&self.persisted).unwrap_or_else(|_| "{}".to_string())
    }

    pub fn ingest_gps(
        &mut self,
        lat: f64,
        lon: f64,
        altitude_m: f64,
        gps_speed_mps: f64,
        bearing_deg: f32,
        accuracy_m: f64,
        timestamp_ms: i64,
    ) -> TelemetrySnapshot {
        let valid_accuracy = accuracy_m <= 0.0 || accuracy_m <= MAX_ACCEPTABLE_ACCURACY_M;
        let mut instant_speed_kmh = if gps_speed_mps >= 0.0 {
            gps_speed_mps * 3.6
        } else {
            0.0
        };

        if let (Some(prev_lat), Some(prev_lon), Some(prev_ts)) =
            (self.last_lat, self.last_lon, self.last_ts_ms)
        {
            let dt_sec = ((timestamp_ms - prev_ts) as f64 / 1000.0).clamp(0.05, 15.0);
            let dist_m = haversine_meters(prev_lat, prev_lon, lat, lon);
            let derived_kmh = (dist_m / dt_sec) * 3.6;

            if gps_speed_mps < 0.0 {
                instant_speed_kmh = derived_kmh;
            }

            if valid_accuracy && derived_kmh <= MAX_PLAUSIBLE_SPEED_KMH {
                let moving = instant_speed_kmh >= MIN_MOVING_SPEED_KMH
                    || dist_m >= MIN_DELTA_METERS_STATIONARY;
                if moving && dist_m < 2_500.0 {
                    let delta_km = dist_m / 1000.0;
                    self.persisted.trip_a_km += delta_km;
                    self.persisted.trip_b_km += delta_km;
                    self.persisted.odometer_km += delta_km;
                    if instant_speed_kmh >= MIN_MOVING_SPEED_KMH {
                        self.persisted.moving_seconds += dt_sec;
                    }
                    if self.remaining_meters > 0.0 {
                        self.remaining_meters = (self.remaining_meters - dist_m).max(0.0);
                    }
                }
            }
        }

        if valid_accuracy {
            self.last_lat = Some(lat);
            self.last_lon = Some(lon);
            self.last_alt = altitude_m;
            self.last_ts_ms = Some(timestamp_ms);
        }

        if bearing_deg >= 0.0 && instant_speed_kmh >= MIN_MOVING_SPEED_KMH {
            self.last_bearing_deg = bearing_deg % 360.0;
        }

        let clamped_speed = instant_speed_kmh.clamp(0.0, MAX_PLAUSIBLE_SPEED_KMH);
        let prev_smoothed = self.smoothed_speed_kmh;
        // Fast responsiveness while smoothing out single-frame GPS wobble
        let alpha = if clamped_speed < 2.0 { 0.55 } else { 0.38 };
        self.smoothed_speed_kmh = prev_smoothed + alpha * (clamped_speed - prev_smoothed);
        if self.smoothed_speed_kmh < 0.8 {
            self.smoothed_speed_kmh = 0.0;
        }

        if self.smoothed_speed_kmh > self.persisted.max_speed_kmh {
            self.persisted.max_speed_kmh = self.smoothed_speed_kmh;
        }

        // Update eco-efficiency score based on smooth speed band (50-90 km/h optimal) & low jerk
        let accel_delta = (self.smoothed_speed_kmh - prev_smoothed).abs();
        let speed_penalty = if self.smoothed_speed_kmh > 110.0 {
            (self.smoothed_speed_kmh - 110.0) * 0.25
        } else {
            0.0
        };
        let jerk_penalty = if accel_delta > 12.0 {
            (accel_delta - 12.0) * 0.6
        } else {
            0.0
        };
        let target_eco = (98.0 - speed_penalty - jerk_penalty).clamp(25.0, 100.0);
        self.eco_score = self.eco_score * 0.9 + target_eco * 0.1;

        self.snapshot()
    }

    pub fn snapshot(&self) -> TelemetrySnapshot {
        let avg_speed_kmh = if self.persisted.moving_seconds > 5.0 {
            (self.persisted.trip_a_km / (self.persisted.moving_seconds / 3600.0))
                .clamp(0.0, MAX_PLAUSIBLE_SPEED_KMH)
        } else {
            0.0
        };

        let remaining_km = self.remaining_meters / 1000.0;
        let eta_minutes = self.compute_eta_minutes(remaining_km);

        TelemetrySnapshot {
            speed_kmh: round1(self.smoothed_speed_kmh),
            avg_speed_kmh: round1(avg_speed_kmh),
            max_speed_kmh: round1(self.persisted.max_speed_kmh),
            trip_a_km: round2(self.persisted.trip_a_km),
            trip_b_km: round2(self.persisted.trip_b_km),
            odometer_km: round2(self.persisted.odometer_km),
            moving_seconds: self.persisted.moving_seconds as u64,
            remaining_km: round2(remaining_km),
            eta_minutes,
            eco_drive_score: self.eco_score.round() as u8,
            bearing_deg: self.last_bearing_deg,
            altitude_m: round1(self.last_alt),
        }
    }

    fn compute_eta_minutes(&self, remaining_km: f64) -> u32 {
        if remaining_km <= 0.02 {
            return 0;
        }
        // Blend live smoothed speed with OSRM baseline speed for stable ETA even at red lights
        let baseline_kmh = if self.osrm_remaining_seconds > 30.0 && remaining_km > 0.05 {
            (remaining_km / (self.osrm_remaining_seconds / 3600.0)).clamp(18.0, 130.0)
        } else {
            48.0
        };
        let live_weight = if self.smoothed_speed_kmh >= 12.0 {
            0.45
        } else {
            0.0
        };
        let effective_kmh =
            baseline_kmh * (1.0 - live_weight) + self.smoothed_speed_kmh * live_weight;
        ((remaining_km / effective_kmh.max(12.0)) * 60.0).ceil() as u32
    }
}

pub fn haversine_meters(lat1: f64, lon1: f64, lat2: f64, lon2: f64) -> f64 {
    let d_lat = (lat2 - lat1).to_radians();
    let d_lon = (lon2 - lon1).to_radians();
    let r_lat1 = lat1.to_radians();
    let r_lat2 = lat2.to_radians();
    let a = (d_lat * 0.5).sin().powi(2) + r_lat1.cos() * r_lat2.cos() * (d_lon * 0.5).sin().powi(2);
    let c = 2.0 * a.sqrt().asin();
    EARTH_RADIUS_M * c
}

#[inline]
fn round1(v: f64) -> f64 {
    (v * 10.0).round() / 10.0
}

#[inline]
fn round2(v: f64) -> f64 {
    (v * 100.0).round() / 100.0
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn accumulates_km_and_calculates_eta() {
        let mut engine = TelemetryEngine::new(None);
        engine.set_route_remaining(10_000.0, 600.0); // 10 km, 10 min OSRM
        let _ = engine.ingest_gps(52.2297, 21.0122, 110.0, 16.67, 90.0, 6.0, 1_000);
        // Move ~1.11 km north over 60s (~66.7 km/h)
        let snap = engine.ingest_gps(52.2397, 21.0122, 112.0, 18.0, 0.0, 6.0, 61_000);
        assert!(snap.trip_a_km > 1.0 && snap.trip_a_km < 1.25);
        assert!(snap.odometer_km >= snap.trip_a_km);
        assert!(snap.speed_kmh > 10.0);
        assert!(snap.eta_minutes >= 7 && snap.eta_minutes <= 12);
    }

    #[test]
    fn ignores_stationary_gps_drift() {
        let mut engine = TelemetryEngine::new(None);
        let _ = engine.ingest_gps(52.2297, 21.0122, 100.0, 0.0, 0.0, 8.0, 1_000);
        // 0.8m drift with 0 speed should not tick km counter
        let snap = engine.ingest_gps(52.229705, 21.012205, 100.0, 0.0, 0.0, 8.0, 5_000);
        assert_eq!(snap.trip_a_km, 0.0);
    }

    #[test]
    fn persists_and_resets_trips() {
        let mut engine = TelemetryEngine::new(None);
        let _ = engine.ingest_gps(50.0, 20.0, 200.0, 20.0, 45.0, 5.0, 0);
        let _ = engine.ingest_gps(50.01, 20.0, 200.0, 20.0, 45.0, 5.0, 50_000);
        let json = engine.export_persisted_json();
        let mut restored = TelemetryEngine::new(Some(&json));
        assert!(restored.snapshot().trip_a_km > 1.0);
        restored.reset_trip_a();
        assert_eq!(restored.snapshot().trip_a_km, 0.0);
        assert!(restored.snapshot().trip_b_km > 1.0);
    }
}
