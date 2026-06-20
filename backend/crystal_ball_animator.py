"""
Crystal Ball Animator - Generates realistic 3D animation data for crystal ball visualization.
Includes: particle effects, light refraction, sphere rendering, prediction waves.
"""

import math
from datetime import datetime
from typing import Dict, List, Any


class CrystalBallAnimator:
    """Generate realistic crystal ball animation data."""

    def __init__(self):
        self.frame_rate = 60
        self.animation_duration = 3.0  # seconds

    def generate_animation_frame(self, confidence: float, prediction_index: int) -> Dict[str, Any]:
        """
        Generate a single animation frame for the crystal ball.

        Args:
            confidence: Confidence level (0-100)
            prediction_index: Which prediction this is (1-5)
        """
        frames = []
        total_frames = int(self.frame_rate * self.animation_duration)

        for frame_num in range(total_frames):
            progress = frame_num / total_frames  # 0.0 to 1.0

            frame_data = {
                "frame": frame_num,
                "timestamp": frame_num / self.frame_rate,
                "progress": progress,

                # Sphere rendering
                "sphere": self._calculate_sphere(progress, confidence),

                # Particles
                "particles": self._generate_particles(progress, confidence, prediction_index),

                # Light & Glow
                "light": self._calculate_light(progress, confidence),

                # Prediction waves/ripples
                "waves": self._generate_waves(progress, confidence, prediction_index),

                # Refraction & distortion
                "refraction": self._calculate_refraction(progress),

                # Opacity & fade
                "opacity": self._calculate_opacity(progress, confidence)
            }

            frames.append(frame_data)

        return {
            "status": "success",
            "animation": {
                "duration": self.animation_duration,
                "fps": self.frame_rate,
                "total_frames": total_frames,
                "frames": frames,
                "loop": True
            },
            "metadata": {
                "confidence": confidence,
                "prediction_index": prediction_index,
                "generated_at": datetime.now().isoformat()
            }
        }

    def _calculate_sphere(self, progress: float, confidence: float) -> Dict[str, Any]:
        """Calculate sphere properties (rotation, scale, distortion)."""
        # Gentle rotating animation
        rotation_y = progress * 360 * 2  # 2 full rotations
        rotation_x = math.sin(progress * math.pi * 2) * 15  # Slight wobble

        # Pulsing scale based on confidence
        scale = 1.0 + (confidence / 100.0) * 0.1 + math.sin(progress * math.pi * 2) * 0.05

        # Distortion (wave-like effect)
        distortion = math.sin(progress * math.pi * 4) * (confidence / 100.0) * 0.1

        return {
            "rotation": {
                "x": rotation_x,
                "y": rotation_y,
                "z": 0
            },
            "scale": scale,
            "distortion": distortion,
            "surface_detail": {
                "roughness": 0.1 + math.sin(progress * math.pi) * 0.05,
                "metallic": 0.3,
                "reflection": 0.8 + (confidence / 100.0) * 0.2
            }
        }

    def _generate_particles(self, progress: float, confidence: float, index: int) -> List[Dict]:
        """Generate particle system data for mystical effect."""
        particles = []
        num_particles = int(30 + (confidence / 100.0) * 50)  # More particles = higher confidence

        for i in range(num_particles):
            # Particle angle and speed vary
            angle = (i / num_particles) * math.pi * 2 + progress * math.pi * 4
            speed = 0.3 + (confidence / 100.0) * 0.7
            distance = 1.5 + math.sin(progress * math.pi * 2 + i) * 0.5

            # Create spiral/orbital motion
            x = distance * math.cos(angle)
            y = distance * math.sin(angle) + math.sin(progress * math.pi * 2) * 0.3
            z = math.cos(progress * math.pi * 2 + i * 0.5) * 0.5

            # Particle lifecycle
            life_cycle = (progress + (i / num_particles)) % 1.0
            opacity = math.sin(life_cycle * math.pi) if life_cycle < 1.0 else 0

            particles.append({
                "id": i,
                "position": {"x": x, "y": y, "z": z},
                "velocity": {"x": math.cos(angle) * speed, "y": math.sin(angle) * speed, "z": 0},
                "size": 2 + (confidence / 100.0) * 3,
                "opacity": opacity * (confidence / 100.0),
                "color": self._get_particle_color(index, life_cycle),
                "glow": opacity * (confidence / 100.0) * 0.8
            })

        return particles

    def _generate_waves(self, progress: float, confidence: float, index: int) -> List[Dict]:
        """Generate prediction wave/ripple effects."""
        waves = []

        # Create multiple concentric waves
        num_waves = int(2 + (confidence / 100.0) * 3)

        for wave_idx in range(num_waves):
            wave_offset = (wave_idx / num_waves) * 0.3
            wave_progress = (progress + wave_offset) % 1.0

            # Expanding circle
            radius = wave_progress * 2.0
            opacity = (1.0 - wave_progress) * (confidence / 100.0)
            intensity = math.sin(wave_progress * math.pi) * (confidence / 100.0)

            waves.append({
                "id": wave_idx,
                "radius": radius,
                "opacity": opacity,
                "intensity": intensity,
                "color": self._get_wave_color(index),
                "thickness": 0.05 + intensity * 0.1,
                "position": {"x": 0, "y": 0, "z": 0}
            })

        return waves

    def _calculate_light(self, progress: float, confidence: float) -> Dict[str, Any]:
        """Calculate dynamic lighting."""
        # Main light that orbits
        light_angle = progress * math.pi * 2

        return {
            "main_light": {
                "position": {
                    "x": math.cos(light_angle) * 3,
                    "y": 2 + math.sin(progress * math.pi) * 1,
                    "z": math.sin(light_angle) * 3
                },
                "color": self._get_light_color(confidence),
                "intensity": 0.8 + math.sin(progress * math.pi * 2) * 0.3,
                "distance": 10
            },
            "ambient_light": {
                "color": "#4a0080",  # Purple/mystical
                "intensity": 0.4 + (confidence / 100.0) * 0.3
            },
            "glow_light": {
                "color": self._get_glow_color(confidence),
                "intensity": (confidence / 100.0) * 0.8,
                "bloom": (confidence / 100.0) * 0.6
            }
        }

    def _calculate_refraction(self, progress: float) -> Dict[str, Any]:
        """Calculate glass refraction and distortion."""
        # Subtle refraction that changes over time
        refraction_strength = 0.1 + math.sin(progress * math.pi) * 0.05

        return {
            "ior": 1.5,  # Index of refraction (like glass)
            "chromatic_aberration": refraction_strength * 0.02,
            "distortion": {
                "x": math.sin(progress * math.pi * 2) * refraction_strength,
                "y": math.cos(progress * math.pi * 2) * refraction_strength,
                "scale": 1.0 + math.sin(progress * math.pi * 4) * 0.05
            },
            "fresnel": {
                "strength": 0.5,
                "power": 2.0
            }
        }

    def _calculate_opacity(self, progress: float, confidence: float) -> float:
        """Calculate sphere opacity."""
        # Pulsing effect
        pulse = 0.5 + math.sin(progress * math.pi * 2) * 0.2
        base_opacity = 0.7 + (confidence / 100.0) * 0.3

        return pulse * base_opacity

    def _get_particle_color(self, index: int, progress: float) -> str:
        """Get particle color based on prediction index."""
        colors = [
            "#FF00FF",  # Magenta
            "#00FFFF",  # Cyan
            "#00FF00",  # Green
            "#FFFF00",  # Yellow
            "#FF0080"   # Pink
        ]

        color = colors[index % len(colors)]

        # Add shimmer effect
        shimmer = math.sin(progress * math.pi * 4) * 0.3
        return color

    def _get_wave_color(self, index: int) -> str:
        """Get wave color."""
        colors = [
            "#FF00FF",  # Magenta
            "#00FFFF",  # Cyan
            "#00FF00",  # Green
            "#FFFF00",  # Yellow
        ]
        return colors[index % len(colors)]

    def _get_light_color(self, confidence: float) -> str:
        """Get light color based on confidence."""
        if confidence > 80:
            return "#FFFFFF"  # Bright white
        elif confidence > 60:
            return "#FFFF00"  # Yellow
        elif confidence > 40:
            return "#FF00FF"  # Magenta
        else:
            return "#00FFFF"  # Cyan

    def _get_glow_color(self, confidence: float) -> str:
        """Get glow color."""
        if confidence > 80:
            return "#FF00FF"  # Bright magenta
        elif confidence > 60:
            return "#00FFFF"  # Cyan
        else:
            return "#00FF00"  # Green


# Global instance
animator = CrystalBallAnimator()
