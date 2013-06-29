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

class BundleProcessorsDiscovery extends AbstractBundleProcessor {
    import BundleProcessorsDiscovery.LOG
    lazy implicit val actorContext = context.getActorSystemDispatcher

    lazy val serviceAwaitable = AwaitableOperation {
        byReference: ByServiceReference =>
            def getServiceReference = bundleContext.getServiceReferences(byReference.interfaceName(), byReference.filter()).toList.get(0)
            bundleContext.getService(getServiceReference).asInstanceOf[BundleProcessor]
    }

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
            def hasBundleProcessorSuperClass(reader: MetadataReader) = !reader.getClassMetadata.getInterfaceNames.find(_.equals(classOf[BundleProcessor].getCanonicalName)).isEmpty
            def hasBundleProcessorAnnotation(reader: MetadataReader) = !reader.getAnnotationMetadata.getAnnotationTypes.find(_.equals(classOf[AnnotatedBundleProcessor].getCanonicalName)).isEmpty

            Try(metadataReader.getMetadataReader(className)) match {
                case Success(reader) => (hasBundleProcessorSuperClass(reader) || hasBundleProcessorAnnotation(reader))
                case Failure(e) => LOG.error("Cannot read class under url: " + url, e); false
            }
        }

        def isAlreadyRegistered(className: String) =
            context.getActivationRegistry.getBundleProcessors.find(bp => className.equals(bp.getClass.getCanonicalName)) match {
                case Some(_) =>
                    LOG.info("Bundle processor already registered: " + url)
                    true
                case None =>
                    false
            }

        def getBundleProcessorInstance(clazz: Class[_]): BundleProcessor = {
            def hasByServiceReferenceAnnotation(clazz: Class[_]) = Try(clazz.getAnnotation(classOf[AnnotatedBundleProcessor]).ref()(0)) match {
                case Success(byReference) => Some(byReference)
                case Failure(e) =>
                    LOG.info("Bundle processor of class: " + clazz.getCanonicalName + " will be instantieted using reflection")
                    None
            }
            def instantiateBundleProcessor(clazz: Class[_]) = Try(clazz.newInstance.asInstanceOf[BundleProcessor]) match {
                case Success(instance) => instance
                case Failure(e) => throw new BundleProcessingException("Could not instantiate bundle's class: " + url, e)
            }

            hasByServiceReferenceAnnotation(clazz) match {
                case Some(byReference) => serviceAwaitable.startWith(byReference)
                case None => instantiateBundleProcessor(clazz)
            }
        }

        if (isBundleProcessor(className) && !isAlreadyRegistered(className)) {
            LOG.info("Found new bundle processor: " + url)

            Try(bundle.loadClass(className).asInstanceOf[Class[Any]]) match {
                case Success(clazz) => {
                    Future {
                        getBundleProcessorInstance(clazz)
                    } onComplete {
                        case Success(instance) => context.getActivationRegistry.addBundleProcessor(bundle.getBundleId, instance)
                        case Failure(e) => throw new BundleProcessingException("Could not get bundle processor instance: " + url, e)
                    }
                }
                case Failure(e) => throw new BundleProcessingException("Could not load bundle's class: " + url, e)
            }

        }
    }
}

object BundleProcessorsDiscovery {
    val LOG = LoggerFactory.getLogger(classOf[BundleProcessorsDiscovery])
}
