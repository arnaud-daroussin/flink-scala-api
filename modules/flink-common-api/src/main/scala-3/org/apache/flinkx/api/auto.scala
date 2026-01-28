package org.apache.flinkx.api

import org.apache.flink.api.common.typeinfo.TypeInformation

/** Provides automatic derivation of TypeInformation for Scala types.
  *
  * Import this object to enable automatic type-information derivation for ADT (case classes, sealed traits) and have
  * access to implicitly available type-information for Scala types, collections, Java types, etc.
  *
  * ==Usage==
  *
  * ===Automatic Derivation===
  *
  * Simply import `org.apache.flinkx.api.auto._` to enable automatic TypeInformation resolution:
  *
  * {{{
  * import org.apache.flinkx.api.auto._
  *
  * case class User(id: String, age: Int)
  *
  * // TypeInformation is automatically derived
  * val env = StreamExecutionEnvironment.getExecutionEnvironment
  * env.fromElements(User("alice", 30), User("bob", 25))
  * }}}
  *
  * @see
  *   [[semiauto]] for explicit/manual derivation
  * @see
  *   [[LowPrioImplicits.deriveTypeInformation]] for the automatic derivation method
  */
// Implicits priority order (linearization): auto > HighPrioImplicits > LowPrioImplicits
object auto extends LowPrioImplicits with HighPrioImplicits
