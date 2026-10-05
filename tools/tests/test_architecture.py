"""Regression tests showing representative legal/illegal dependency directions."""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from check_architecture import library_violations, violations


class ArchitectureTests(unittest.TestCase):
    def check(self, package, body, filename="Example.kt"):
        path = (
            Path("shared/src/commonMain/kotlin/com/otus/mail")
            / package.replace(".", "/")
            / filename
        )
        return violations(path, f"package com.otus.mail.{package}\n{body}", "com.otus.mail")

    def test_elements_cannot_import_components(self):
        self.assertTrue(
            self.check("designsystem.elements", "import com.otus.mail.designsystem.components.Card")
        )

    def test_fully_qualified_reference_is_checked(self):
        self.assertTrue(
            self.check(
                "designsystem.elements", "fun x() = com.otus.mail.designsystem.components.Card()"
            )
        )

    def test_component_cannot_import_screen_using_alias(self):
        self.assertTrue(
            self.check(
                "feature.inbox.presentation.components",
                "import com.otus.mail.feature.inbox.presentation.screens.InboxScreen as View",
            )
        )

    def test_domain_cannot_import_data(self):
        self.assertTrue(
            self.check("feature.inbox.domain", "import com.otus.mail.feature.inbox.data.Messages")
        )

    def test_domain_cannot_access_network(self):
        self.assertTrue(self.check("feature.inbox.domain", "import io.ktor.client.HttpClient"))

    def test_presentation_cannot_import_data(self):
        self.assertTrue(
            self.check(
                "feature.inbox.presentation", "import com.otus.mail.feature.inbox.data.Messages"
            )
        )

    def test_feature_cannot_import_sibling(self):
        self.assertTrue(
            self.check(
                "feature.inbox.domain", "import com.otus.mail.feature.contacts.domain.Person"
            )
        )

    def test_component_cannot_hold_viewmodel(self):
        self.assertTrue(
            self.check("feature.inbox.presentation.components", "fun row(vm: InboxViewModel) {}")
        )

    def test_common_cannot_import_android(self):
        self.assertTrue(self.check("core.platform", "import android.content.Context"))

    def test_compose_platform_package_is_portable(self):
        self.assertFalse(
            self.check("designsystem.elements", "import androidx.compose.ui.platform.LocalDensity")
        )

    def test_real_platform_roots_stay_forbidden(self):
        for reference in (
            "import platform.Foundation.NSDate",
            "import java.time.Instant",
            "fun now() = platform.Foundation.NSDate()",
            "fun now() = java.time.Instant.now()",
        ):
            with self.subTest(reference=reference):
                self.assertTrue(self.check("designsystem.elements", reference))

    def test_domain_still_rejects_compose_platform(self):
        self.assertTrue(
            self.check("feature.inbox.domain", "import androidx.compose.ui.platform.LocalDensity")
        )

    def test_allowed_screen_and_domain_dependency(self):
        self.assertFalse(
            self.check(
                "feature.inbox.presentation.screens",
                "import com.otus.mail.feature.inbox.domain.Message\n"
                "@Composable fun InboxScreen() {}",
                "InboxScreen.kt",
            )
        )

    def test_comments_and_strings_do_not_create_dependencies(self):
        self.assertFalse(
            self.check(
                "core.jmap",
                "// import com.otus.mail.feature.inbox.data.Messages\n"
                'val example = "com.otus.mail.feature.inbox.data.Messages"',
            )
        )

    def test_package_must_match_path(self):
        path = Path("shared/src/commonMain/kotlin/com/otus/mail/core/Example.kt")
        self.assertTrue(violations(path, "package com.otus.mail.app", "com.otus.mail"))

    def test_color_literal_must_live_in_theme(self):
        self.assertTrue(self.check("designsystem.elements", "val color = Color(0xFF202021)"))
        self.assertFalse(self.check("designsystem.theme", "val color = Color(0xFF202021)"))

    def test_suppression_is_rejected(self):
        self.assertTrue(self.check("core.jmap", '@Suppress("ALL")\nfun x() {}'))


if __name__ == "__main__":
    unittest.main()


class LibraryTests(unittest.TestCase):
    def check(self, package, body, source_set="commonMain"):
        path = (
            Path(f"jmap/src/{source_set}/kotlin/com/otus/jmap") / package.replace(".", "/") / "X.kt"
        )
        name = "com.otus.jmap" + (f".{package}" if package else "")
        return library_violations(path, f"package {name}\n{body}")

    def test_library_code_is_allowed_networking(self):
        self.assertEqual(self.check("", "import io.ktor.client.HttpClient"), [])

    def test_library_cannot_use_compose(self):
        self.assertTrue(self.check("", "import androidx.compose.runtime.Composable"))

    def test_library_cannot_reach_app_code(self):
        self.assertTrue(self.check("login", "import com.otus.mail.app.App"))
        self.assertTrue(self.check("", "fun x() = com.otus.calendar.app.App()"))

    def test_library_common_code_cannot_use_platform_apis(self):
        self.assertTrue(self.check("", "import android.content.Context"))
        self.assertEqual(self.check("login", "import android.content.Context", "androidMain"), [])

    def test_library_package_must_match_its_namespace(self):
        path = Path("jmap/src/commonMain/kotlin/com/otus/jmap/X.kt")
        self.assertTrue(library_violations(path, "package com.otus.mail.core\n"))
