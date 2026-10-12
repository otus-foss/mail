"""Regression tests showing representative legal/illegal dependency directions."""

import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from check_architecture import drawable_violations, suppression_violations, violations


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

    def test_color_literals_belong_in_meta(self):
        for package in ("designsystem.elements", "designsystem.theme", "feature.inbox.theme"):
            with self.subTest(package=package):
                self.assertTrue(self.check(package, "val color = Color(0xFF202021)"))

    def test_aliased_and_qualified_color_literals_are_rejected(self):
        for body in (
            "import androidx.compose.ui.graphics.Color as Paint\nval c = Paint(0xFF202021)",
            "val c = androidx.compose.ui.graphics.Color(0xFF202021)",
            "val c = Color(red = 0.1f, green = 0.2f, blue = 0.3f)",
            "val c = Color.hsv(10f, 0.5f, 0.5f)",
        ):
            with self.subTest(body=body):
                self.assertTrue(self.check("feature.inbox.presentation.components", body))
        self.assertFalse(
            self.check("feature.inbox.presentation.components", "val c = OtusColors.text1")
        )

    def test_central_ui_rejects_app_tokens_and_elements(self):
        for layer in ("elements", "components", "theme"):
            path = Path(
                f"shared/src/commonMain/kotlin/com/otus/mail/designsystem/{layer}/Example.kt"
            )
            errors = violations(
                path,
                f"package com.otus.mail.designsystem.{layer}",
                "com.otus.mail",
                central_ui=True,
            )
            self.assertIn("visual elements, components, and tokens belong in otus-meta", errors)

    def test_central_ui_allows_feature_adapters_using_meta(self):
        path = Path(
            "shared/src/commonMain/kotlin/com/otus/mail/feature/inbox/presentation/components/Example.kt"
        )
        self.assertFalse(
            violations(
                path,
                "package com.otus.mail.feature.inbox.presentation.components\n"
                "import com.otus.designsystem.components.ContentRow",
                "com.otus.mail",
                central_ui=True,
            )
        )

    def test_suppression_is_rejected(self):
        for annotation in (
            '@Suppress("ALL")',
            '@SuppressLint("NewApi")',
            '@android.annotation.SuppressLint("NewApi")',
            '@kotlin.Suppress("ALL")',
            '@file:kotlin.Suppress("ALL")',
            '@get:Suppress("ALL")',
        ):
            with self.subTest(annotation=annotation):
                self.assertTrue(self.check("core.jmap", f"{annotation}\nfun x() {{}}"))

    def test_xml_tools_ignore_is_rejected(self):
        ignored = '<application tools:ignore="GoogleAppIndexingWarning" />'
        self.assertTrue(suppression_violations(ignored))
        self.assertFalse(suppression_violations('<application android:label="@string/app" />'))

    def test_template_code_in_strings_is_checked(self):
        for body in (
            'val x = "${com.otus.mail.feature.inbox.data.Messages()}"',
            'val x = """a ${"b" + com.otus.mail.feature.inbox.data.Messages()} c"""',
        ):
            with self.subTest(body=body):
                self.assertTrue(self.check("feature.inbox.domain", body))
        text = 'val x = "${size} com.otus.mail.feature.inbox.data.X"'
        self.assertFalse(self.check("feature.inbox.domain", text))

    def test_domain_cannot_use_compose_resources(self):
        resources = "import org.jetbrains.compose.resources.stringResource"
        self.assertTrue(self.check("feature.inbox.domain", resources))

    def test_drawables_are_rejected_in_every_source_set(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            for name in (
                "shared/src/commonMain/composeResources/drawable/a.xml",
                "shared/src/androidMain/composeResources/drawable-night/b.png",
                "androidApp/src/main/res/drawable-v24/c.xml",
                "androidApp/src/main/res/values/colors.xml",
            ):
                (root / name).parent.mkdir(parents=True, exist_ok=True)
                (root / name).write_text("<x />")
            errors = drawable_violations(root)
        self.assertEqual(3, len(errors), errors)
        self.assertFalse(any("values" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
