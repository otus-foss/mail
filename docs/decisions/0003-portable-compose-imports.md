# Recognize platform API roots accurately

The architecture checker previously matched `platform.` anywhere after a word boundary, including
inside the portable `androidx.compose.ui.platform` package. This prevented legitimate common UI
code from reading density, layout direction, and keyboard control through Compose.

Match forbidden API roots only when they are not preceded by an identifier character or dot.
The architecture policy is unchanged: `platform.Foundation`, `java`, `javax`, and `android` roots
remain forbidden in common production code. Domain still cannot depend on Compose. Regression
tests cover portable imports, actual platform imports, and fully qualified platform calls.
