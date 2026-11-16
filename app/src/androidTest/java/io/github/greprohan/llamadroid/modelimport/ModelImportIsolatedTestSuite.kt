package io.github.greprohan.llamadroid.modelimport

import org.junit.runner.RunWith
import org.junit.runners.Suite

/** Bounded expected-pass roster only. Never include native, crash setup/verify or hang fixtures. */
@RunWith(Suite::class)
@Suite.SuiteClasses(AndroidImportIoTest::class, ModelImportScreenTest::class,
    AndroidPickerRouteTest::class, AndroidModelStoreTest::class)
class ModelImportIsolatedTestSuite
