package pl.reintegrate.jetpack.core.osgi

import org.osgi.framework.Bundle
import org.osgi.framework.BundleEvent
import org.osgi.framework.BundleContext
import pl.reintegrate.jetpack.core.context.JetpackContext

trait BundleProcessor {

    def scan(bundle: Bundle)
    def stopScan(bundle: Bundle)
    def bundleChanged(bundleEvent: BundleEvent)

    def setContext(context: JetpackContext)
}