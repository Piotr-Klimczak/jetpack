package pl.reintegrate.jetpack.core.impl.osgi

import org.osgi.framework.BundleContext
import pl.reintegrate.jetpack.core.osgi.BundleProcessor
import org.osgi.framework.Bundle
import org.osgi.framework.BundleListener
import org.osgi.framework.BundleEvent
import org.osgi.framework.ServiceListener
import org.osgi.framework.ServiceEvent
import org.slf4j.LoggerFactory
import pl.reintegrate.jetpack.core.impl.osgi.processors.BundleProcessorsDiscovery
import scala.collection.mutable.ListBuffer
import java.util.Dictionary
import pl.reintegrate.jetpack.core.osgi.ActivationRegistry
import org.osgi.framework.BundleActivator
import scala.util.Failure
import scala.util.Try
import scala.util.Success
import org.osgi.framework.ServiceReference
import pl.reintegrate.jetpack.core.context.JetpackContext
import pl.reintegrate.jetpack.core.tooling.AwaitableOperation
import scala.concurrent.Future
import akka.actor.ActorSystem
import akka.osgi.ActorSystemActivator

class ActivationRegistryImpl extends ActorSystemActivator with ActivationRegistry with BundleActivator with BundleListener with ServiceListener {
    val LOG = LoggerFactory.getLogger(classOf[ActivationRegistryImpl])
    val bundleProcessors = new BundleByIdContainerOf[BundleProcessor]()
    var actorSystem: ActorSystem = null
    var context: JetpackContext = null

    //configure with ActorSystemActivation
    override def configure(context: BundleContext, system: ActorSystem) {
        actorSystem = system
        registerService(context, system)
    }

    override def start(bundleContext: BundleContext): Unit = {
        LOG.info("Starting Jetpack Activator...")

        super.start(bundleContext)
        implicit val actorSystemContext = actorSystem.dispatcher

        def getFutureJetpackContext(context: BundleContext) = {
            def getServiceReference = Option(AwaitableOperation(context.getServiceReference[JetpackContext]).startWith(classOf[JetpackContext]))
            getServiceReference match {
                case Some(reference) => AwaitableOperation(context.getService[JetpackContext]).startWith(reference)
                case None => throw new IllegalStateException("Could not find Jetpack context in OSGi registry: " + classOf[JetpackContext].getName())
            }
        }

        def finalizeActivationRegistry(jetpackContext: JetpackContext, bundleContext: BundleContext) = {
            context = jetpackContext
            context.setActivationRegistry(this)
            context.setActorSystem(actorSystem)
            context.setBundleContext(bundleContext)

            bundleContext.addBundleListener(this)
            bundleContext.addServiceListener(this)

            addBundleProcessor(0, new BundleProcessorsDiscovery())
            processInstalledBundles(bundleContext, scanBundle)

            bundleContext.registerService(classOf[ActivationRegistry].getCanonicalName(), this, null)
        }


        Future {
            getFutureJetpackContext(bundleContext)
        } onComplete {
            case Success(jetpackContext) => finalizeActivationRegistry(jetpackContext, bundleContext)
            case Failure(e) =>
                LOG.error("Exception occured while getting Jetpack context from OSGi registry", e)
                throw e
        }

        LOG.info("Jetpack Activator initialized, waiting for future tasks...")
    }

    override def stop(context: BundleContext): Unit = {
        LOG.info("Stopping " + this.getClass.getCanonicalName)

        context.removeServiceListener(this)
        context.removeBundleListener(this)

        processInstalledBundles(context, unregisterScannerForBundle)

        super.stop(context)
    }

    override def bundleChanged(event: BundleEvent): Unit = {
        LOG.info("Bundle " + event.getBundle.getBundleId + " has new state: " + event.getType)
        bundleProcessors.foreach(bp => processingExceptionaHandler(bp.bundleChanged(event)))
    }

    override def serviceChanged(event: ServiceEvent) = {
        LOG.info("Service changed for Bundle " + event.getServiceReference.getBundle.getBundleId
            + " has new state: " + event.getType + ", prperties: " + event.getServiceReference.getPropertyKeys)
        //TODO Implementation
    }

    override def addBundleProcessor(id: Long, bp: BundleProcessor) = {
        LOG.info("Adding bundle processor: " + bp.getClass().getCanonicalName() + " to Activation Registry")
        bp.setContext(context);
        bundleProcessors.put(id, bp)
    }
    override def getBundleProcessors(): ListBuffer[BundleProcessor] = bundleProcessors.toList

    def scanBundle(b: Bundle) = bundleProcessors.foreach(bp => processingExceptionaHandler(bp.scan(b)))
    def unregisterScannerForBundle(b: Bundle) = bundleProcessors.foreach(bp => processingExceptionaHandler(bp.stopScan(b)))
    def processInstalledBundles(context: BundleContext, action: (Bundle) => Unit) = context.getBundles.foreach(action(_))

    def processingExceptionaHandler(block: => Unit) = Try(block) match {
        case Failure(e) => LOG.error("Exception during bundle processing", e)
        case Success(_) =>
    }
}