package pl.reintegrate.jetpack.core.impl.context

import akka.actor.ActorSystem
import scala.util.Try
import pl.reintegrate.jetpack.core.context.JetpackContext
import pl.reintegrate.jetpack.core.osgi.ActivationRegistry
import org.osgi.framework.BundleContext

class JetpackContextImpl extends JetpackContext {
    var actorSystem:ActorSystem = null
    var activationRegistry: ActivationRegistry = null
    var bundleContext: BundleContext = null

    override def setActorSystem(actorSystem: ActorSystem) = this.actorSystem = actorSystem
    override def getActorSystemDispatcher = actorSystem.dispatcher
    override def setActivationRegistry(activationRegistry: ActivationRegistry) = this.activationRegistry = activationRegistry
    override def getActivationRegistry = activationRegistry
    override def setBundleContext(bundleContext: BundleContext) = this.bundleContext = bundleContext
    override def getBundleContext = bundleContext

    override def shutdown = {
        Try(actorSystem.shutdown)
    }
}