use serde::{Deserialize, Serialize};

use crate::telemetry::haversine_meters;

#[derive(Debug, Clone, Deserialize)]
pub struct RoutePoint {
    pub lat: f64,
    pub lon: f64,
    #[serde(default)]
    pub elev_m: f64,
}

#[derive(Debug, Clone, Deserialize)]
pub struct CandidateRoute {
    pub index: usize,
    pub distance_m: f64,
    pub duration_s: f64,
    pub steps_count: usize,
    pub points: Vec<RoutePoint>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct EcoRouteRequest {
    #[serde(default = "default_mass_kg")]
    pub vehicle_mass_kg: f64,
    pub routes: Vec<CandidateRoute>,
}

fn default_mass_kg() -> f64 {
    1380.0
}

#[derive(Debug, Clone, Serialize)]
pub struct EvaluatedRoute {
    pub index: usize,
    pub distance_km: f64,
    pub duration_min: f64,
    pub estimated_liters: f64,
    pub l_per_100km: f64,
    pub co2_grams: u32,
    pub elevation_gain_m: f64,
    pub elevation_loss_m: f64,
    pub sharp_turns: usize,
    pub eco_score: u8,
    pub is_best_fuel_saving: bool,
    pub is_fastest: bool,
    pub fuel_saved_vs_fastest_pct: f32,
    pub summary_en: String,
    pub summary_pl: String,
}

#[derive(Debug, Clone, Serialize)]
pub struct EcoRouteResponse {
    pub best_fuel_index: usize,
    pub fastest_index: usize,
    pub routes: Vec<EvaluatedRoute>,
}

/// Physics-based fuel consumption estimator comparing candidate driving tracks.
///
/// Accounts for:
/// - Rolling resistance: C_r * m * g * ds
/// - Aerodynamic drag: 0.5 * rho * CdA * v^2 * ds (heavily penalizes >105 km/h highways)
/// - Gravitational climb resistance: m * g * dh_up (with partial coasting benefit downhill)
/// - Stop-and-go / sharp intersection kinetic losses derived from step density & sharp turns
/// - Engine thermal efficiency + idle fuel burn proportional to trip duration
pub fn analyze_eco_routes(req: &EcoRouteRequest) -> EcoRouteResponse {
    if req.routes.is_empty() {
        return EcoRouteResponse {
            best_fuel_index: 0,
            fastest_index: 0,
            routes: Vec::new(),
        };
    }

    let mass = req.vehicle_mass_kg.clamp(800.0, 3500.0);
    let g = 9.81_f64;
    let c_r = 0.0115_f64;
    let rho_air = 1.225_f64;
    let cd_a = 0.67_f64;
    // 1 liter of gasoline ~ 34.2 MJ; average drivetrain + thermal efficiency ~ 0.275 -> 9.405 MJ mechanical/L
    let mech_joules_per_liter = 9_405_000.0_f64;

    let fastest_idx = req
        .routes
        .iter()
        .min_by(|a, b| a.duration_s.total_cmp(&b.duration_s))
        .map(|r| r.index)
        .unwrap_or(req.routes[0].index);

    struct RawEval {
        index: usize,
        distance_km: f64,
        duration_min: f64,
        liters: f64,
        l_per_100: f64,
        gain_m: f64,
        loss_m: f64,
        sharp_turns: usize,
    }

    let mut raw_evals = Vec::with_capacity(req.routes.len());

    for r in &req.routes {
        let dist_m = r.distance_m.max(10.0);
        let dur_s = r.duration_s.max(5.0);
        let avg_speed_mps = (dist_m / dur_s).clamp(3.0, 42.0);

        let mut gain_m = 0.0_f64;
        let mut loss_m = 0.0_f64;
        let mut sharp_turns = 0usize;

        if r.points.len() >= 2 {
            let mut prev_bearing: Option<f64> = None;
            for w in r.points.windows(2) {
                let p0 = &w[0];
                let p1 = &w[1];
                let seg_m = haversine_meters(p0.lat, p0.lon, p1.lat, p1.lon);
                let dh = p1.elev_m - p0.elev_m;
                if dh > 0.4 {
                    gain_m += dh;
                } else if dh < -0.4 {
                    loss_m += -dh;
                }

                if seg_m > 8.0 {
                    let bearing = segment_bearing(p0.lat, p0.lon, p1.lat, p1.lon);
                    if let Some(pb) = prev_bearing {
                        let diff = angle_diff_deg(pb, bearing);
                        if diff > 42.0 {
                            sharp_turns += 1;
                        }
                    }
                    prev_bearing = Some(bearing);
                }
            }
        }

        // Mechanical work terms (Joules)
        let rolling_j = c_r * mass * g * dist_m;
        let aero_j = 0.5 * rho_air * cd_a * avg_speed_mps.powi(2) * dist_m;
        // Climbing requires full potential energy; gentle downhill recovers ~35% via coasting/fuel-cut
        let climb_j = mass * g * gain_m - 0.35 * mass * g * loss_m.min(gain_m * 0.9);
        let climb_j = climb_j.max(-rolling_j * 0.35);

        // Estimate stop/slowdown events from OSRM steps + sharp polyline bends
        let stops_est = (r.steps_count as f64 * 0.65) + (sharp_turns as f64 * 0.35);
        let accel_speed_mps = (avg_speed_mps * 0.72).clamp(5.0, 18.0);
        let stop_go_j = stops_est * 0.5 * mass * accel_speed_mps.powi(2) * 0.55;

        let total_mech_j = (rolling_j + aero_j + climb_j + stop_go_j).max(dist_m * 180.0);
        // Idle + accessories fuel burn (~0.65 L/hour = 0.0001805 L/s)
        let idle_liters = dur_s * 0.000145;
        let liters = ((total_mech_j / mech_joules_per_liter) + idle_liters).max(0.01);

        let distance_km = dist_m / 1000.0;
        let l_per_100 = (liters / distance_km.max(0.1)) * 100.0;

        raw_evals.push(RawEval {
            index: r.index,
            distance_km,
            duration_min: dur_s / 60.0,
            liters,
            l_per_100,
            gain_m,
            loss_m,
            sharp_turns,
        });
    }

    let best_fuel_eval = raw_evals
        .iter()
        .min_by(|a, b| a.liters.total_cmp(&b.liters))
        .unwrap();
    let best_fuel_idx = best_fuel_eval.index;
    let fastest_liters = raw_evals
        .iter()
        .find(|e| e.index == fastest_idx)
        .map(|e| e.liters)
        .unwrap_or(best_fuel_eval.liters);
    let min_liters = best_fuel_eval.liters;

    let evaluated: Vec<EvaluatedRoute> = raw_evals
        .into_iter()
        .map(|e| {
            let saved_pct = if fastest_liters > 0.001 {
                (((fastest_liters - e.liters) / fastest_liters) * 100.0) as f32
            } else {
                0.0
            };
            let is_best = e.index == best_fuel_idx;
            let is_fast = e.index == fastest_idx;

            // Relative + absolute eco score (0..100)
            let efficiency_ratio = (min_liters / e.liters).clamp(0.4, 1.0);
            let consumption_penalty = ((e.l_per_100 - 4.8).max(0.0) * 4.2).min(30.0);
            let eco_score = (efficiency_ratio * 100.0 - consumption_penalty * 0.35)
                .clamp(35.0, 99.0)
                .round() as u8;

            let co2_grams = (e.liters * 2392.0).round() as u32;

            let (summary_en, summary_pl) = if is_best && is_fast {
                (
                    format!(
                        "Best overall: fastest & lowest fuel ({:.1} L/100km)",
                        e.l_per_100
                    ),
                    format!(
                        "Najlepsza trasa: najszybsza i najoszczędniejsza ({:.1} L/100km)",
                        e.l_per_100
                    ),
                )
            } else if is_best {
                (
                    format!(
                        "Best Fuel-Saving Track: saves {:.1}% fuel vs fastest",
                        saved_pct.max(0.1)
                    ),
                    format!(
                        "Najlepsza trasa paliwowa: oszczędza {:.1}% paliwa",
                        saved_pct.max(0.1)
                    ),
                )
            } else if is_fast {
                (
                    format!("Fastest route (+{:.1}% more fuel)", (-saved_pct).max(0.0)),
                    format!(
                        "Najszybsza trasa (+{:.1}% więcej paliwa)",
                        (-saved_pct).max(0.0)
                    ),
                )
            } else {
                (
                    format!("Alternative track ({:.1} L/100km)", e.l_per_100),
                    format!("Trasa alternatywna ({:.1} L/100km)", e.l_per_100),
                )
            };

            EvaluatedRoute {
                index: e.index,
                distance_km: (e.distance_km * 100.0).round() / 100.0,
                duration_min: (e.duration_min * 10.0).round() / 10.0,
                estimated_liters: (e.liters * 100.0).round() / 100.0,
                l_per_100km: (e.l_per_100 * 10.0).round() / 10.0,
                co2_grams,
                elevation_gain_m: e.gain_m.round(),
                elevation_loss_m: e.loss_m.round(),
                sharp_turns: e.sharp_turns,
                eco_score,
                is_best_fuel_saving: is_best,
                is_fastest: is_fast,
                fuel_saved_vs_fastest_pct: (saved_pct * 10.0).round() / 10.0,
                summary_en,
                summary_pl,
            }
        })
        .collect();

    EcoRouteResponse {
        best_fuel_index: best_fuel_idx,
        fastest_index: fastest_idx,
        routes: evaluated,
    }
}

fn segment_bearing(lat1: f64, lon1: f64, lat2: f64, lon2: f64) -> f64 {
    let r_lat1 = lat1.to_radians();
    let r_lat2 = lat2.to_radians();
    let d_lon = (lon2 - lon1).to_radians();
    let y = d_lon.sin() * r_lat2.cos();
    let x = r_lat1.cos() * r_lat2.sin() - r_lat1.sin() * r_lat2.cos() * d_lon.cos();
    (y.atan2(x).to_degrees() + 360.0) % 360.0
}

fn angle_diff_deg(a: f64, b: f64) -> f64 {
    let d = (a - b).abs() % 360.0;
    if d > 180.0 {
        360.0 - d
    } else {
        d
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn picks_flatter_fewer_stops_route_for_fuel_saving() {
        // Route 0: Fast highway with big hill climb and higher speed (126 km/h)
        // Route 1: Slightly slower, flatter arterial road (72 km/h) with fewer turns
        let req = EcoRouteRequest {
            vehicle_mass_kg: 1400.0,
            routes: vec![
                CandidateRoute {
                    index: 0,
                    distance_m: 25_000.0,
                    duration_s: 715.0, // ~126 km/h
                    steps_count: 14,
                    points: vec![
                        RoutePoint {
                            lat: 52.0,
                            lon: 21.0,
                            elev_m: 100.0,
                        },
                        RoutePoint {
                            lat: 52.1,
                            lon: 21.0,
                            elev_m: 420.0,
                        },
                        RoutePoint {
                            lat: 52.2,
                            lon: 21.0,
                            elev_m: 380.0,
                        },
                    ],
                },
                CandidateRoute {
                    index: 1,
                    distance_m: 24_000.0,
                    duration_s: 1200.0, // ~72 km/h
                    steps_count: 5,
                    points: vec![
                        RoutePoint {
                            lat: 52.0,
                            lon: 21.0,
                            elev_m: 100.0,
                        },
                        RoutePoint {
                            lat: 52.1,
                            lon: 21.05,
                            elev_m: 115.0,
                        },
                        RoutePoint {
                            lat: 52.2,
                            lon: 21.0,
                            elev_m: 108.0,
                        },
                    ],
                },
            ],
        };

        let resp = analyze_eco_routes(&req);
        assert_eq!(resp.fastest_index, 0);
        assert_eq!(resp.best_fuel_index, 1);
        let best = resp.routes.iter().find(|r| r.index == 1).unwrap();
        assert!(best.is_best_fuel_saving);
        assert!(best.fuel_saved_vs_fastest_pct > 10.0);
    }
}
