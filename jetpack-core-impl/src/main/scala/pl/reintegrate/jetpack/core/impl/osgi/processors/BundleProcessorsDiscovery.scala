package pl.reintegrate.jetpack.core.impl.osgi.processors

import pl.reintegrate.jetpack.core.osgi.AbstractBundleProcessor
import org.osgi.framework.Bundle
import org.osgi.framework.BundleEvent
import org.slf4j.LoggerFactory
import java.net.URL
import pl.reintegrate.jetpack.core.osgi.ActivationRegistry
import scala.util.Success
import scala.util.Failure
import scala.util.Try
import java.io.InputStream
import scala.io.Source
import scala.collection.JavaConversions._
import org.springframework.asm.ClassReader
import org.springframework.asm.ClassVisitor
import org.springframework.core.`type`.ClassMetadata
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.LinkedHashSet
import org.springframework.core.`type`.classreading.SimpleMetadataReaderFactory
import scala.util.Failure
import pl.reintegrate.jetpack.core.osgi.exception.BundleProcessingException
import org.osgi.framework.wiring.BundleWiring
import org.springframework.core.`type`.classreading.MetadataReaderFactory
import pl.reintegrate.jetpack.core.osgi.AnnotatedBundleProcessor
import org.springframework.core.`type`.classreading.MetadataReader
import pl.reintegrate.jetpack.core.osgi.ByServiceReference
import scala.util.Success
import pl.reintegrate.jetpack.core.osgi.BundleProcessor
import scala.concurrent.Future
import pl.reintegrate.jetpack.core.tooling.AwaitableOperation
import scala.util.Failure
import pl.reintegrate.jetpack.core.tooling.OsgiInstantiation

class BundleProcessorsDiscovery extends AbstractBundleProcessor with OsgiInstantiation {
    import BundleProcessorsDiscovery.LOG

    override def scan(bundle: Bundle) = {
        LOG.info("Bundle " + bundle.getBundleId + " scanning...")

        def getBundlesClassloader = bundle.adapt(classOf[BundleWiring]).getClassLoader
        val getMetadataReader = new SimpleMetadataReaderFactory(getBundlesClassloader)

        listResources(bundle, "*.class").foreach(registerIfBundleProcessor(bundle, _, getMetadataReader))
    }

    override def stopScan(bundle: Bundle) = {
        //TODO
    }
    override def bundleChanged(bundleEvent: BundleEvent) = {
        //TODO
    }

    private def registerIfBundleProcessor(bundle: Bundle, url: URL, metadataReader: MetadataReaderFactory): Unit = {
        if (LOG.isDebugEnabled)
            LOG.debug("Processing class: " + url)

        val className = getClassNameFromUrl(url)

        def isBundleProcessor(className: String) = {
            def isBundleProcessorImplementation(reader: MetadataReader) = !reader.getClassMetadata.getInterfaceNames.find(_.equals(classOf[BundleProcessor].getCanonicalName)).isEmpty
            def hasBundleProcessorAnnotation(reader: MetadataReader) = !reader.getAnnotationMetadata.getAnnotationTypes.find(_.equals(classOf[AnnotatedBundleProcessor].getCanonicalName)).isEmpty
            def isAbstract(reader: MetadataReader) = reader.getAnnotationMetadata.isAbstract

            Try(metadataReader.getMetadataReader(className)) match {
                case Success(reader) => (isBundleProcessorImplementation(reader) || hasBundleProcessorAnnotation(reader)) && !isAbstract(reader)
                case Failure(e) => LOG.error("Cannot read class under url: " + url, e); false
            }
        }

        def isAlreadyRegistered(className: String) = {
            context.getActivationRegistry.getBundleProcessors.find(bp => className.equals(bp.getClass.getCanonicalName)) match {
                case Some(_) =>
                    LOG.info("Bundle processor already registered: " + url)
                    true
                case None =>
                    false
            }
        }

        if (isBundleProcessor(className) && !isAlreadyRegistered(className)) {
            LOG.info("Found new bundle processor: " + url)
            Future {
                def getByServiceReferenceAnnotation(clazz: Class[_]) = clazz.getAnnotation(classOf[AnnotatedBundleProcessor]).ref()(0)

                lookupOSGiOrCreateInstanceOf(
                    loadClassFromBundle[BundleProcessor](bundle, className),
                    getByServiceReferenceAnnotation(_))
            } onComplete {
                case Success(instance) => context.getActivationRegistry.addBundleProcessor(bundle.getBundleId, instance)
                case Failure(e) if (e.isInstanceOf[ClassNotFoundException]) => throw new BundleProcessingException("Could not load bundle's class: " + url, e)
                case Failure(e) => throw new BundleProcessingException("Could not get bundle processor instance: " + url, e)
            }

        }
    }
}

object BundleProcessorsDiscovery {
    val LOG = LoggerFactory.getLogger(classOf[BundleProcessorsDiscovery])
}
