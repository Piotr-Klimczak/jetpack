package pl.reintegrate.jetpack.core.impl.osgi

import scala.collection.immutable.Nil
import org.osgi.framework.Bundle
import scala.collection.mutable.Map
import scala.collection.mutable.ListBuffer

class BundleByIdContainerOf[T] {
    val map = Map[Long, ListBuffer[T]]()

    def foreach[U](f: T => U) = toList.foreach(f)

    def put(bundle: Bundle, value: T) = getForBundle(bundle) += value
    def put(bundleId: Long, value: T) = getForBundle(bundleId) += value

    def getForBundle(bundle: Bundle): ListBuffer[T] = getForBundle(bundle.getBundleId())
    def getForBundle(bundleId: Long): ListBuffer[T] = map.get(bundleId) match {
        case Some(v) => v
        case None => {
            map += bundleId -> ListBuffer[T]()
            getForBundle(bundleId)
        }
    }

    def toList() = map.foldLeft(ListBuffer[T]()) { case (s, (k, v)) => s ++ v }
    
}