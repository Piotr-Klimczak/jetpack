package pl.reintegrate.jetpack.core.osgi

import org.osgi.framework.Bundle
import org.osgi.framework.BundleEvent
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.ListBuffer
import org.osgi.framework.wiring.BundleWiring
import scala.collection.JavaConverters._
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import pl.reintegrate.jetpack.core.osgi.exception.BundleProcessingException
import java.net.URL
import org.osgi.framework.BundleContext
import pl.reintegrate.jetpack.core.context.JetpackContext
import pl.reintegrate.jetpack.core.context.JetpackContext

abstract class AbstractBundleProcessor extends BundleProcessor {
    var context: JetpackContext = null
    var bundleContext: BundleContext = null
    lazy val activationRegistry: ActivationRegistry = context.getActivationRegistry

    override def setContext(context: JetpackContext) = this.context = context

    def listResources(bundle: Bundle, filter: String = "*.*") = {
        val bw = bundle.adapt(classOf[BundleWiring])
        bw.findEntries("/", filter, BundleWiring.FINDENTRIES_RECURSE).asScala
    }

    protected def getClassFrom(bundle: Bundle, url: URL, clazz: Class[_]) =
        Try(bundle.loadClass(getClassNameFromUrl(url)).asInstanceOf[Class[Any]]) match {
            case Success(clazzFound) => if (clazzFound.isInstance(clazz)) Some(clazzFound) else None
            case Failure(e) => throw new BundleProcessingException("Cannot load class: " + url, e)
        }

    protected def getClassNameFromUrl(url: URL) = url.getPath().replace("/", ".").substring(1, url.getPath().length() - 6)
}