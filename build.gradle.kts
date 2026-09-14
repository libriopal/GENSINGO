// GENSINGO — root build script.
//
// Greenfield per PRD_CC.md §13.1 / §12: this is NOT the com.discomplemented.ginseng
// tracking prototype that previously occupied this repository. That tree targeted a
// different product (continuous GPS tracking + backend sync + landowner permissions,
// the RadarNav lineage the PRD §12 explicitly excludes from porting) and could never
// have built: it had no res/ directory, no assets/, and no Gradle wrapper.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
