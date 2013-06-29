package pl.reintegrate.jetpack.core.context

import akka.actor.ActorSystem
import scala.util.Try
import pl.reintegrate.jetpack.core.osgi.ActivationRegistry
import scala.concurrent.ExecutionContext
import org.osgi.framework.BundleContext

trait JetpackContext {
    def setActivationRegistry(value: ActivationRegistry)
    def getActivationRegistry: ActivationRegistry
    def setActorSystem(actorSystem: ActorSystem)
    def getActorSystemDispatcher: ExecutionContext
    def setBundleContext(bundleContext: BundleContext)
    def getBundleContext(): BundleContext
    def shutdown
}