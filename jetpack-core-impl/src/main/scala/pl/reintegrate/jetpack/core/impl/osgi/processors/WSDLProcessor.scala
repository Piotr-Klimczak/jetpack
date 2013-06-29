package pl.reintegrate.jetpack.core.impl.osgi.processors

import org.osgi.framework.Bundle
import org.osgi.framework.BundleEvent
import pl.reintegrate.jetpack.core.osgi.AnnotatedBundleProcessor
import pl.reintegrate.jetpack.core.osgi.AbstractBundleProcessor

@AnnotatedBundleProcessor
class WSDLProcessor extends AbstractBundleProcessor {

    override def scan(bundle: Bundle) = {}
    override def stopScan(bundle: Bundle) = {}
    override def bundleChanged(bundleEvent: BundleEvent) = {}
}