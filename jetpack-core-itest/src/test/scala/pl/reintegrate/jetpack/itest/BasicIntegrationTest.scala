package pl.reintegrate.jetpack.itest

import org.ops4j.pax.exam.junit.ExamReactorStrategy
import org.ops4j.pax.exam.junit.Configuration
import org.junit.runner.RunWith
import org.ops4j.pax.exam.junit.JUnit4TestRunner
import org.ops4j.pax.exam.spi.reactors.EagerSingleStagedReactorFactory
import org.junit.Test
import pl.reintegrate.jetpack.itest.support.CamelTestSupport
import pl.reintegrate.jetpack.itest.support.IntegrationTestSupport
import org.osgi.framework.Bundle
import pl.reintegrate.jetpack.core.osgi.ActivationRegistry
import pl.reintegrate.jetpack.core.tooling.AwaitableOperation

@RunWith(classOf[JUnit4TestRunner])
@ExamReactorStrategy(Array(classOf[EagerSingleStagedReactorFactory]))
abstract class BasicIntegrationTest extends IntegrationTestSupport with CamelTestSupport {

    @Configuration
    def config = servicemixTestConfiguration() ++ scalaTestConfiguration

}

class JetpackInstallTest extends BasicIntegrationTest {
    import JetpackInstallTest._

    @Test
    def coreInstallTest = testWithFeature("camel-scala", "jetpack-core") {
        def getActivationRegistry(clazz: Class[ActivationRegistry]) = bundleContext.getService(bundleContext.getServiceReference(clazz))
        def getAwaitedActivationRegistry = AwaitableOperation(getActivationRegistry).startWith(classOf[ActivationRegistry])

        expect("jetpack-core-impl bundle is present and active in OSGi context") {
            bundleContext.getBundles.find(b => b.getSymbolicName.contains("jetpack-core-impl") && b.getState == Bundle.ACTIVE)
        }

        expect(BUNDLE_PROCESSOR_DISCOVERY_CLASS_NAME + " is present in OSGi services context") {
            getAwaitedActivationRegistry.getBundleProcessors().find(_.getClass.getCanonicalName.contains(BUNDLE_PROCESSOR_DISCOVERY_CLASS_NAME))
        }

        expect(BUNDLE_PROCESSOR_WSDL_CLASS_NAME + " is present in OSGi services context") {
            getAwaitedActivationRegistry.getBundleProcessors().find(_.getClass.getCanonicalName.contains(BUNDLE_PROCESSOR_WSDL_CLASS_NAME))
        }
    }

    object JetpackInstallTest {
        val BUNDLE_PROCESSOR_DISCOVERY_CLASS_NAME = "BundleProcessorsDiscovery"
        val BUNDLE_PROCESSOR_WSDL_CLASS_NAME = "WSDLBundleProcessor"
    }
}