package pl.reintegrate.jetpack.core.tooling

import pl.reintegrate.jetpack.core.context.JetpackContext
import pl.reintegrate.jetpack.core.osgi.ByServiceReference
import scala.collection.JavaConversions._
import scala.util.Success
import scala.util.Failure
import scala.util.Try
import org.osgi.framework.Bundle
import org.slf4j.Logger
import org.slf4j.LoggerFactory

trait OsgiInstantiation {
    val LOG = LoggerFactory.getLogger(getClass().getCanonicalName())
    lazy implicit val actorContext = getContext.getActorSystemDispatcher

    lazy val bundleProcessorServiceAwaitable = AwaitableOperation {
        byReference: ByServiceReference =>
            def getServiceReference = getContext.getBundleContext.getServiceReferences(byReference.interfaceName(), byReference.filter()).toList.get(0)
            getContext.getBundleContext.getService(getServiceReference)
    }

    def getContext: JetpackContext
    
    def loadClassFromBundle[T](bundle: Bundle, className: String) = bundle.loadClass(className).asInstanceOf[Class[T]]

    def lookupOSGiOrCreateInstanceOf[T](clazz: Class[T], getServiceByReferenceAnnotation: Class[_] => ByServiceReference): T = {
        def hasByServiceReferenceAnnotation(clazz: Class[T]) = Try(getServiceByReferenceAnnotation(clazz)) match {
            case Success(byReference) =>
                LOG.info("Class " + clazz.getCanonicalName() + " will be instantieted using OSGi reference")
                Some(byReference)
            case Failure(e) =>
                LOG.info("Class " + clazz.getCanonicalName() + " will be instantieted using reflection")
                None
        }
        def instantiate(clazz: Class[T]): T = Try(clazz.newInstance) match {
            case Success(instance) => instance
            case Failure(e) => throw new InstatiationException("Could not instantiate class: " + clazz.getCanonicalName(), e)
        }

        hasByServiceReferenceAnnotation(clazz) match {
            case Some(byReference) => bundleProcessorServiceAwaitable.startWith(byReference).asInstanceOf[T]
            case None => instantiate(clazz)
        }
    }
}

class InstatiationException(msg: String, e: Throwable) extends RuntimeException(msg, e) {
}