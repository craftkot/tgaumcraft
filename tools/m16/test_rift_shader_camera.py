#!/usr/bin/env python3
import unittest
from pathlib import Path

from verify_rift_shader_camera import parse_shader_frame, verify_shader_camera


def marker(yaw="1.5707964", pitch="-0.6806784", sampler="Sampler0", texture="minecraft:textures/entity/end_portal.png"):
    return (
        "[Render thread/INFO] "
        f"TC_SMOKE_RIFT_SHADER_ACTIVE=YES time=2015 yaw={yaw} pitch={pitch} "
        f"sampler={sampler} texture={texture}"
    )


class RiftShaderCameraTest(unittest.TestCase):
    def test_exact_side_on_camera_passes(self):
        result = verify_shader_camera(marker(), yaw_degrees=90, pitch_degrees=39)
        self.assertIn("RIFT_SHADER_CAMERA=PASS", result)

    def test_legacy_edge_on_false_positive_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "yaw mismatch"):
            verify_shader_camera(
                marker(yaw="0.0", pitch="-1.5707964"),
                yaw_degrees=90,
                pitch_degrees=39,
            )

    def test_yaw_wraparound_is_accepted(self):
        self.assertIn(
            "RIFT_SHADER_CAMERA=PASS",
            verify_shader_camera(marker(yaw="-4.712389"), yaw_degrees=90, pitch_degrees=39),
        )

    def test_wrong_pitch_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "pitch mismatch"):
            verify_shader_camera(marker(pitch="0.0"), yaw_degrees=90, pitch_degrees=39)

    def test_missing_marker_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "found 0"):
            parse_shader_frame("no shader marker")

    def test_duplicate_marker_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "found 2"):
            parse_shader_frame(marker() + "\n" + marker())

    def test_malformed_marker_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "malformed"):
            parse_shader_frame("TC_SMOKE_RIFT_SHADER_ACTIVE=YES time=2015 yaw=bad")

    def test_non_finite_angles_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "finite"):
            parse_shader_frame(marker(yaw="nan"))

    def test_wrong_sampler_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "sampler"):
            verify_shader_camera(marker(sampler="Sampler1"), yaw_degrees=90, pitch_degrees=39)

    def test_wrong_texture_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "texture"):
            verify_shader_camera(marker(texture="minecraft:textures/entity/enderdragon/dragon.png"), yaw_degrees=90, pitch_degrees=39)

    def test_client_smoke_driver_wires_camera_verifier(self):
        driver = (Path(__file__).parents[1] / "client_rift_ambient_sound_smoke.py").read_text(encoding="utf-8")
        self.assertIn("tools/m16/test_rift_shader_camera.py", driver)
        self.assertIn('tools/m16/verify_rift_shader_camera.py "$render_probe_log"', driver)
        self.assertIn('--yaw-degrees "$rift_visual_yaw"', driver)
        self.assertIn('--pitch-degrees "$rift_visual_pitch"', driver)


if __name__ == "__main__":
    unittest.main(verbosity=2)
