package pl.reintegrate.jetpack.core.osgi

import scala.collection.mutable.ListBuffer

import org.osgi.framework.Bundle

trait ActivationRegistry {
    def addBundleProcessor(id: Long, bundleProcessor: BundleProcessor): Unit
    def getBundleProcessors(): ListBuffer[BundleProcessor]
}