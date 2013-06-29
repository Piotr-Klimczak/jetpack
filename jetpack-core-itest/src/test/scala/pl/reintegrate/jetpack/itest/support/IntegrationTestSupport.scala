/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package pl.reintegrate.jetpack.itest.support

import javax.inject.Inject
import org.osgi.framework.{ ServiceRegistration, BundleContext }
import java.io.File
import scala.Some
import org.junit.{ Before, BeforeClass, After }
import org.ops4j.pax.logging.spi.{ PaxLoggingEvent, PaxAppender }
import collection.mutable.ArrayBuffer
import java.util.Hashtable
import org.junit.Assert.fail
import org.apache.karaf.features.{ Feature, FeaturesService }
import scala.collection.JavaConversions.setAsJavaSet
import java.util
import scala.language.postfixOps
import org.ops4j.pax.exam.junit.ProbeBuilder
import org.ops4j.pax.exam.TestProbeBuilder
import org.osgi.framework.Constants
import scala.util.Try

/**
 * Basimport pl.reintegrate.jetpack.itest.support.IntegrationTestConfigurations
 * import pl.reintegrate.jetpack.itest.support.Await
 * e class for building Apache ServiceMix integration tests
 */
abstract class IntegrationTestSupport extends Await with IntegrationTestConfigurations {

    @Inject
    var bundleContext: BundleContext = null;

    @Inject
    var featuresService: FeaturesService = null

    /*
     * List of services to be unregistered after the test
     */
    val registrations = ArrayBuffer.empty[ServiceRegistration[_]]

    /*
     * Catch all exceptions as sometimes Await is executing a block using values not yet fully initialized
     */
    @After
    def clearLogging = try { logging.clear } catch { case e: Exception => }

    /*
     * A set of convenience vals for referring to directories within the test container
     */
    lazy val servicemixHomeFolder = new File(System.getProperty("servicemix.home"))
    lazy val dataFolder = new File(servicemixHomeFolder, "data")
    lazy val logFolder = new File(dataFolder, "log")
    lazy val logFile: File = new File(logFolder, "servicemix.log")

    /**
     * Install a feature and run a block of code.  Afterwards, uninstall the feature again.
     */
    def testWithFeature(names: String*)(block: => Unit) =
        try {
            val features: Set[Feature] = (names map { name => featuresService.getFeature(name) } toSet)
            //TODO: Get this working without the extra options - enabling bundle refresh here will mess up the test container
            featuresService.installFeatures(features, util.EnumSet.of(FeaturesService.Option.NoAutoRefreshBundles))
            block
        } finally {
            names foreach { featuresService.uninstallFeature }
        }

    /**
     * Expect a certain condition to occur within the allotted waiting time.
     */
    def expect[T](block: => Option[T]): Unit = expect(null.asInstanceOf[String]) { block }
    
    /**
     * Expect a certain condition to occur within the allotted waiting time.
     */
    def expect[T](msg: String = null)(block: => Option[T]): Unit = await(block) match {
        case None => fail("Gave up waiting for test condition" + (if (msg != null) ": " + msg))
        case _ => //graciously ignore
    }

    /**
     * Registers and return a logging appender
     */
    lazy val logging = {
        val appender = new PaxLoggingAppender

        val props = new Hashtable[String, String]()
        props.put("org.ops4j.pax.logging.appender.name", "ITestLogAppender")

        Option(bundleContext.registerService(classOf[PaxAppender], appender, props)) match {
            case Some(registration) => (registrations += registration)
            case None => throw new RuntimeException("Error setting up logging appender for testing")
        }

        appender
    }

    /**
     * Simple PaxAppender implementation that buffers logging events for the integration
     */
    class PaxLoggingAppender extends PaxAppender {

        val buffer = ArrayBuffer.empty[PaxLoggingEvent]

        def doAppend(event: PaxLoggingEvent) = buffer += event

        def clear = buffer.clear

        def containsMessage(predicate: String => Boolean): Option[String] = containsEvent(event => predicate(event.getMessage)) map (_.getMessage)

        def containsEvent(predicate: PaxLoggingEvent => Boolean): Option[PaxLoggingEvent] = buffer find (predicate)

    }

}
