package org.apache.flinkx.api

import org.apache.flink.annotation.{Internal, Public, PublicEvolving}
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.serialization.SerializerConfig
import org.apache.flink.api.common.eventtime.{TimestampAssigner, WatermarkGenerator, WatermarkStrategy}
import org.apache.flink.api.common.functions.{FilterFunction, FlatMapFunction, MapFunction, Partitioner}
import org.apache.flink.api.common.io.OutputFormat
import org.apache.flink.api.common.operators.{ResourceSpec, SlotSharingGroup}
import org.apache.flink.api.common.serialization.SerializationSchema
import org.apache.flink.api.common.state.MapStateDescriptor
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.connector.sink2.Sink
import org.apache.flink.api.java.functions.KeySelector
import org.apache.flink.api.java.typeutils.ResultTypeQueryable
import org.apache.flink.streaming.api.datastream.{
  BroadcastStream,
  DataStreamSink,
  SingleOutputStreamOperator,
  AllWindowedStream => JavaAllWindowedStream,
  DataStream => JavaStream,
  KeyedStream => JavaKeyedStream
}
import org.apache.flink.streaming.api.functions.timestamps.AscendingTimestampExtractor
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.streaming.api.operators.OneInputStreamOperator
import org.apache.flink.streaming.api.windowing.assigners._
import org.apache.flink.streaming.api.windowing.windows.{GlobalWindow, TimeWindow, Window}
import org.apache.flink.util.Collector
import org.apache.flink.api.java.tuple.{Tuple => JavaTuple}
import scala.jdk.CollectionConverters._
import ScalaStreamOps._

@Public
class DataStream[T](stream: JavaStream[T]) {

  /** Returns the [[StreamExecutionEnvironment]] associated with the current [[DataStream]].
    *
    * @return
    *   associated execution environment
    * @deprecated
    *   Use [[executionEnvironment]] instead
    */
  @deprecated
  @PublicEvolving
  def getExecutionEnvironment: StreamExecutionEnvironment =
    new StreamExecutionEnvironment(stream.getExecutionEnvironment)

  /** Returns the TypeInformation for the elements of this DataStream.
    *
    * @deprecated
    *   Use [[dataType]] instead.
    */
  @deprecated
  @PublicEvolving
  def getType: TypeInformation[T] = stream.getType

  /** Returns the ID of the DataStream.
    */
  @Internal
  private[flinkx] def getId = stream.getId

  // --------------------------------------------------------------------------
  //  Scalaesk accessors
  // --------------------------------------------------------------------------

  /** Gets the underlying java DataStream object.
    */
  def javaStream: JavaStream[T] = stream

  /** Returns the TypeInformation for the elements of this DataStream.
    */
  def dataType: TypeInformation[T] = stream.getType

  /** Returns the execution config.
    */
  def executionConfig: ExecutionConfig = stream.getExecutionConfig

  def serializerConfig: SerializerConfig =
    stream.getExecutionEnvironment().getConfig().getSerializerConfig()

  /** Returns the [[StreamExecutionEnvironment]] associated with this data stream
    */
  def executionEnvironment: StreamExecutionEnvironment =
    new StreamExecutionEnvironment(stream.getExecutionEnvironment)

  /** Returns the parallelism of this operation.
    */
  def parallelism: Int = stream.getParallelism

  /** Sets the parallelism of this operation. This must be at least 1.
    */
  def setParallelism(parallelism: Int): DataStream[T] = {
    stream match {
      case ds: SingleOutputStreamOperator[T] => ds.setParallelism(parallelism)
      case _                                 =>
        throw new UnsupportedOperationException("Operator " + stream + " cannot set the parallelism.")
    }
    this
  }

  def setMaxParallelism(maxParallelism: Int): DataStream[T] = {
    stream match {
      case ds: SingleOutputStreamOperator[T] => ds.setMaxParallelism(maxParallelism)
      case _                                 =>
        throw new UnsupportedOperationException(
          "Operator " + stream + " cannot set the maximum" +
            "paralllelism"
        )
    }

    this
  }

  /** Returns the minimum resources of this operation.
    */
  @PublicEvolving
  def minResources: ResourceSpec = stream.getMinResources

  /** Returns the preferred resources of this operation.
    */
  @PublicEvolving
  def preferredResources: ResourceSpec = stream.getPreferredResources

  /** Gets the name of the current data stream. This name is used by the visualization and logging during runtime.
    *
    * @return
    *   Name of the stream.
    */
  def name: String = stream match {
    case stream: SingleOutputStreamOperator[T] => stream.getName
    case _ => throw new UnsupportedOperationException("Only supported for operators.")
  }

  // --------------------------------------------------------------------------

  /** Sets the name of the current data stream. This name is used by the visualization and logging during runtime.
    *
    * @return
    *   The named operator
    */
  def name(name: String): DataStream[T] = stream match {
    case stream: SingleOutputStreamOperator[T] => asScalaStream(stream.name(name))
    case _                                     =>
      throw new UnsupportedOperationException("Only supported for operators.")
      this
  }

  /** Sets an ID for this operator.
    *
    * The specified ID is used to assign the same operator ID across job submissions (for example when starting a job
    * from a savepoint).
    *
    * <strong>Important</strong>: this ID needs to be unique per transformation and job. Otherwise, job submission will
    * fail.
    *
    * @param uid
    *   The unique user-specified ID of this transformation.
    * @return
    *   The operator with the specified ID.
    */
  @PublicEvolving
  def uid(uid: String): DataStream[T] = javaStream match {
    case stream: SingleOutputStreamOperator[T] => asScalaStream(stream.uid(uid))
    case _                                     =>
      throw new UnsupportedOperationException("Only supported for operators.")
      this
  }

  @PublicEvolving
  def getSideOutput[X: TypeInformation](tag: OutputTag[X]): DataStream[X] = javaStream match {
    case stream: SingleOutputStreamOperator[_] =>
      asScalaStream(stream.getSideOutput(tag: OutputTag[X]))
  }

  /** Sets an user provided hash for this operator. This will be used AS IS the create the JobVertexID. <p/> <p>The user
    * provided hash is an alternative to the generated hashes, that is considered when identifying an operator through
    * the default hash mechanics fails (e.g. because of changes between Flink versions). <p/>
    * <p><strong>Important</strong>: this should be used as a workaround or for trouble shooting. The provided hash
    * needs to be unique per transformation and job. Otherwise, job submission will fail. Furthermore, you cannot assign
    * user-specified hash to intermediate nodes in an operator chain and trying so will let your job fail.
    *
    * @param hash
    *   the user provided hash for this operator.
    * @return
    *   The operator with the user provided hash.
    */
  @PublicEvolving
  def setUidHash(hash: String): DataStream[T] = javaStream match {
    case stream: SingleOutputStreamOperator[T] =>
      asScalaStream(stream.setUidHash(hash))
    case _ =>
      throw new UnsupportedOperationException("Only supported for operators.")
      this
  }

  /** Turns off chaining for this operator so thread co-location will not be used as an optimization. </p> Chaining can
    * be turned off for the whole job by [[StreamExecutionEnvironment.disableOperatorChaining()]] however it is not
    * advised for performance considerations.
    */
  @PublicEvolving
  def disableChaining(): DataStream[T] = {
    stream match {
      case ds: SingleOutputStreamOperator[T] => ds.disableChaining()
      case _                                 =>
        throw new UnsupportedOperationException("Only supported for operators.")
    }
    this
  }

  /** Starts a new task chain beginning at this operator. This operator will not be chained (thread co-located for
    * increased performance) to any previous tasks even if possible.
    */
  @PublicEvolving
  def startNewChain(): DataStream[T] = {
    stream match {
      case ds: SingleOutputStreamOperator[T] => ds.startNewChain()
      case _                                 =>
        throw new UnsupportedOperationException("Only supported for operators.")
    }
    this
  }

  /** Sets the slot sharing group of this operation. Parallel instances of operations that are in the same slot sharing
    * group will be co-located in the same TaskManager slot, if possible.
    *
    * Operations inherit the slot sharing group of input operations if all input operations are in the same slot sharing
    * group and no slot sharing group was explicitly specified.
    *
    * Initially an operation is in the default slot sharing group. An operation can be put into the default group
    * explicitly by setting the slot sharing group to `"default"`.
    *
    * @param slotSharingGroup
    *   The slot sharing group name.
    */
  @PublicEvolving
  def slotSharingGroup(slotSharingGroup: String): DataStream[T] = {
    stream match {
      case ds: SingleOutputStreamOperator[T] => ds.slotSharingGroup(slotSharingGroup)
      case _                                 =>
        throw new UnsupportedOperationException("Only supported for operators.")
    }
    this
  }

  /** Sets the slot sharing group of this operation. Parallel instances of operations that are in the same slot sharing
    * group will be co-located in the same TaskManager slot, if possible.
    *
    * Operations inherit the slot sharing group of input operations if all input operations are in the same slot sharing
    * group and no slot sharing group was explicitly specified.
    *
    * Initially an operation is in the default slot sharing group. An operation can be put into the default group
    * explicitly by setting the slot sharing group to `"default"`.
    *
    * @param slotSharingGroup
    *   Which contains name and its resource spec.
    */
  @PublicEvolving
  def slotSharingGroup(slotSharingGroup: SlotSharingGroup): DataStream[T] = {
    stream match {
      case ds: SingleOutputStreamOperator[T] => ds.slotSharingGroup(slotSharingGroup)
      case _                                 =>
        throw new UnsupportedOperationException("Only supported for operators.")
    }
    this
  }

  /** Sets the maximum time frequency (ms) for the flushing of the output buffer. By default the output buffers flush
    * only when they are full.
    *
    * @param timeoutMillis
    *   The maximum time between two output flushes.
    * @return
    *   The operator with buffer timeout set.
    */
  def setBufferTimeout(timeoutMillis: Long): DataStream[T] = {
    stream match {
      case ds: SingleOutputStreamOperator[T] => ds.setBufferTimeout(timeoutMillis)
      case _                                 =>
        throw new UnsupportedOperationException("Only supported for operators.")
    }
    this
  }

  // --------------------------------------------------------------------------
  //  Stream Transformations
  // --------------------------------------------------------------------------

  /** Creates a new DataStream by merging DataStream outputs of the same type with each other. The DataStreams merged
    * using this operator will be transformed simultaneously.
    */
  def union(dataStreams: DataStream[T]*): DataStream[T] =
    asScalaStream(stream.union(dataStreams.map(_.javaStream): _*))

  /** Creates a new ConnectedStreams by connecting DataStream outputs of different type with each other. The DataStreams
    * connected using this operators can be used with CoFunctions.
    */
  def connect[T2](dataStream: DataStream[T2]): ConnectedStreams[T, T2] =
    asScalaStream(stream.connect(dataStream.javaStream))

  /** Creates a new [[BroadcastConnectedStream]] by connecting the current [[DataStream]] or [[KeyedStream]] with a
    * [[BroadcastStream]].
    *
    * The latter can be created using the [[broadcast(MapStateDescriptor[])]] method.
    *
    * The resulting stream can be further processed using the ``broadcastConnectedStream.process(myFunction)`` method,
    * where ``myFunction`` can be either a [[org.apache.flink.streaming.api.functions.co.KeyedBroadcastProcessFunction]]
    * or a [[org.apache.flink.streaming.api.functions.co.BroadcastProcessFunction]] depending on the current stream
    * being a [[KeyedStream]] or not.
    *
    * @param broadcastStream
    *   The broadcast stream with the broadcast state to be connected with this stream.
    * @return
    *   The [[BroadcastConnectedStream]].
    */
  @PublicEvolving
  def connect[R](broadcastStream: BroadcastStream[R]): BroadcastConnectedStream[T, R] =
    asScalaStream(stream.connect(broadcastStream))

  /** Groups the elements of a DataStream by the given K key to be used with grouped operators like grouped reduce or
    * grouped aggregations.
    */
  def keyBy[K: TypeInformation](fun: T => K): KeyedStream[T, K] = {

    val cleanFun                    = clean(fun)
    val keyType: TypeInformation[K] = implicitly[TypeInformation[K]]

    val keyExtractor = new KeySelector[T, K] with ResultTypeQueryable[K] {
      def getKey(in: T): K                             = cleanFun(in)
      override def getProducedType: TypeInformation[K] = keyType
    }
    asScalaStream(new JavaKeyedStream(stream, keyExtractor, keyType))
  }

  /** Groups the elements of a DataStream by the given K key to be used with grouped operators like grouped reduce or
    * grouped aggregations.
    */
  def keyBy[K: TypeInformation](fun: KeySelector[T, K]): KeyedStream[T, K] = {

    val cleanFun                    = clean(fun)
    val keyType: TypeInformation[K] = implicitly[TypeInformation[K]]

    asScalaStream(new JavaKeyedStream(stream, cleanFun, keyType))
  }

  /** Partitions a DataStream on the key returned by the selector, using a custom partitioner. This method takes the key
    * selector to get the key to partition on, and a partitioner that accepts the key type.
    *
    * Note: This method works only on single field keys, i.e. the selector cannot return tuples of fields.
    */
  def partitionCustom[K: TypeInformation](partitioner: Partitioner[K], fun: T => K): DataStream[T] = {

    val keyType  = implicitly[TypeInformation[K]]
    val cleanFun = clean(fun)

    val keyExtractor = new KeySelector[T, K] with ResultTypeQueryable[K] {
      def getKey(in: T): K                             = cleanFun(in)
      override def getProducedType: TypeInformation[K] = keyType
    }

    asScalaStream(stream.partitionCustom(partitioner, keyExtractor))
  }

  /** Sets the partitioning of the DataStream so that the output tuples are broad casted to every parallel instance of
    * the next component.
    */
  def broadcast: DataStream[T] = asScalaStream(stream.broadcast())

  /** Sets the partitioning of the [[DataStream]] so that the output elements are broadcasted to every parallel instance
    * of the next operation. In addition, it implicitly creates as many
    * [[org.apache.flink.api.common.state.BroadcastState broadcast states]] as the specified descriptors which can be
    * used to store the element of the stream.
    *
    * @param broadcastStateDescriptors
    *   the descriptors of the broadcast states to create.
    * @return
    *   A [[BroadcastStream]] which can be used in the [[DataStream.connect(BroadcastStream)]] to create a
    *   [[BroadcastConnectedStream]] for further processing of the elements.
    */
  @PublicEvolving
  def broadcast(broadcastStateDescriptors: MapStateDescriptor[_, _]*): BroadcastStream[T] = {
    if (broadcastStateDescriptors == null) {
      throw new NullPointerException("State Descriptors must not be null.")
    }
    javaStream.broadcast(broadcastStateDescriptors: _*)
  }

  /** Sets the partitioning of the DataStream so that the output values all go to the first instance of the next
    * processing operator. Use this setting with care since it might cause a serious performance bottleneck in the
    * application.
    */
  @PublicEvolving
  def global: DataStream[T] = asScalaStream(stream.global())

  /** Sets the partitioning of the DataStream so that the output tuples are shuffled to the next component.
    */
  @PublicEvolving
  def shuffle: DataStream[T] = asScalaStream(stream.shuffle())

  /** Sets the partitioning of the DataStream so that the output tuples are forwarded to the local subtask of the next
    * component (whenever possible).
    */
  def forward: DataStream[T] = asScalaStream(stream.forward())

  /** Sets the partitioning of the DataStream so that the output tuples are distributed evenly to the next component.
    */
  def rebalance: DataStream[T] = asScalaStream(stream.rebalance())

  /** Sets the partitioning of the [[DataStream]] so that the output tuples are distributed evenly to a subset of
    * instances of the downstream operation.
    *
    * The subset of downstream operations to which the upstream operation sends elements depends on the degree of
    * parallelism of both the upstream and downstream operation. For example, if the upstream operation has parallelism
    * 2 and the downstream operation has parallelism 4, then one upstream operation would distribute elements to two
    * downstream operations while the other upstream operation would distribute to the other two downstream operations.
    * If, on the other hand, the downstream operation has parallelism 2 while the upstream operation has parallelism 4
    * then two upstream operations will distribute to one downstream operation while the other two upstream operations
    * will distribute to the other downstream operations.
    *
    * In cases where the different parallelisms are not multiples of each other one or several downstream operations
    * will have a differing number of inputs from upstream operations.
    */
  @PublicEvolving
  def rescale: DataStream[T] = asScalaStream(stream.rescale())

  /** Creates a new DataStream by applying the given function to every element of this DataStream.
    */
  def map[R: TypeInformation](fun: T => R): DataStream[R] = {
    if (fun == null) {
      throw new NullPointerException("Map function must not be null.")
    }
    val cleanFun = clean(fun)
    val mapper   = new MapFunction[T, R] {
      def map(in: T): R = cleanFun(in)
    }

    map(mapper)
  }

  /** Creates a new DataStream by applying the given function to every element of this DataStream.
    */
  def map[R: TypeInformation](mapper: MapFunction[T, R]): DataStream[R] = {
    if (mapper == null) {
      throw new NullPointerException("Map function must not be null.")
    }

    val outType: TypeInformation[R] = implicitly[TypeInformation[R]]
    asScalaStream(stream.map(mapper, outType).asInstanceOf[JavaStream[R]])
  }

  /** Creates a new DataStream by applying the given function to every element and flattening the results.
    */
  def flatMap[R: TypeInformation](flatMapper: FlatMapFunction[T, R]): DataStream[R] = {
    if (flatMapper == null) {
      throw new NullPointerException("FlatMap function must not be null.")
    }

    val outType: TypeInformation[R] = implicitly[TypeInformation[R]]
    asScalaStream(stream.flatMap(flatMapper, outType).asInstanceOf[JavaStream[R]])
  }

  /** Creates a new DataStream by applying the given function to every element and flattening the results.
    */
  def flatMap[R: TypeInformation](fun: (T, Collector[R]) => Unit): DataStream[R] = {
    if (fun == null) {
      throw new NullPointerException("FlatMap function must not be null.")
    }
    val cleanFun   = clean(fun)
    val flatMapper = new FlatMapFunction[T, R] {
      def flatMap(in: T, out: Collector[R]): Unit = { cleanFun(in, out) }
    }
    flatMap(flatMapper)
  }

  /** Creates a new DataStream by applying the given function to every element and flattening the results.
    */
  def flatMap[R: TypeInformation](fun: T => IterableOnce[R]): DataStream[R] = {
    if (fun == null) {
      throw new NullPointerException("FlatMap function must not be null.")
    }
    val cleanFun   = clean(fun)
    val flatMapper = new FlatMapFunction[T, R] {
      def flatMap(in: T, out: Collector[R]): Unit = { cleanFun(in).iterator.foreach(out.collect) }
    }
    flatMap(flatMapper)
  }

  /** Applies the given [[ProcessFunction]] on the input stream, thereby creating a transformed output stream.
    *
    * The function will be called for every element in the stream and can produce zero or more output.
    *
    * @param processFunction
    *   The [[ProcessFunction]] that is called for each element in the stream.
    */
  @PublicEvolving
  def process[R: TypeInformation](processFunction: ProcessFunction[T, R]): DataStream[R] = {

    if (processFunction == null) {
      throw new NullPointerException("ProcessFunction must not be null.")
    }

    asScalaStream(javaStream.process(processFunction, implicitly[TypeInformation[R]]))
  }

  /** Creates a new DataStream that contains only the elements satisfying the given filter predicate.
    */
  def filter(filter: FilterFunction[T]): DataStream[T] = {
    if (filter == null) {
      throw new NullPointerException("Filter function must not be null.")
    }
    asScalaStream(stream.filter(filter))
  }

  /** Creates a new DataStream that contains only the elements satisfying the given filter predicate.
    */
  def filter(fun: T => Boolean): DataStream[T] = {
    if (fun == null) {
      throw new NullPointerException("Filter function must not be null.")
    }
    val cleanFun  = clean(fun)
    val filterFun = new FilterFunction[T] {
      def filter(in: T): Boolean = cleanFun(in)
    }
    filter(filterFun)
  }

  /** Creates a new DataStream that contains only the elements not satisfying the given filter predicate.
    */
  def filterNot(fun: T => Boolean): DataStream[T] = {
    if (fun == null) {
      throw new NullPointerException("FilteNot function must not be null.")
    }
    val cleanFun  = clean(fun)
    val filterFun = new FilterFunction[T] {
      def filter(in: T): Boolean = !cleanFun(in)
    }
    filter(filterFun)
  }

  /** Windows this [[DataStream]] into sliding count windows.
    *
    * Note: This operation can be inherently non-parallel since all elements have to pass through the same operator
    * instance. (Only for special cases, such as aligned time windows is it possible to perform this operation in
    * parallel).
    *
    * @param size
    *   The size of the windows in number of elements.
    * @param slide
    *   The slide interval in number of elements.
    */
  def countWindowAll(size: Long, slide: Long): AllWindowedStream[T, GlobalWindow] = {
    new AllWindowedStream(stream.countWindowAll(size, slide))
  }

  /** Windows this [[DataStream]] into tumbling count windows.
    *
    * Note: This operation can be inherently non-parallel since all elements have to pass through the same operator
    * instance. (Only for special cases, such as aligned time windows is it possible to perform this operation in
    * parallel).
    *
    * @param size
    *   The size of the windows in number of elements.
    */
  def countWindowAll(size: Long): AllWindowedStream[T, GlobalWindow] = {
    new AllWindowedStream(stream.countWindowAll(size))
  }

  /** Windows this data stream to a [[AllWindowedStream]], which evaluates windows over a key grouped stream. Elements
    * are put into windows by a [[WindowAssigner]]. The grouping of elements is done both by key and by window.
    *
    * A [[org.apache.flink.streaming.api.windowing.triggers.Trigger]] can be defined to specify when windows are
    * evaluated. However, `WindowAssigner` have a default `Trigger` that is used if a `Trigger` is not specified.
    *
    * Note: This operation can be inherently non-parallel since all elements have to pass through the same operator
    * instance. (Only for special cases, such as aligned time windows is it possible to perform this operation in
    * parallel).
    *
    * @param assigner
    *   The `WindowAssigner` that assigns elements to windows.
    * @return
    *   The trigger windows data stream.
    */
  @PublicEvolving
  def windowAll[W <: Window](assigner: WindowAssigner[_ >: T, W]): AllWindowedStream[T, W] = {
    new AllWindowedStream[T, W](new JavaAllWindowedStream[T, W](stream, assigner))
  }

  /** Assigns timestamps to the elements in the data stream and generates watermarks to signal event time progress. The
    * given [[WatermarkStrategy is used to create a [[TimestampAssigner]] and
    * [[org.apache.flink.api.common.eventtime.WatermarkGenerator]].
    *
    * For each event in the data stream, the [[TimestampAssigner#extractTimestamp(Object, long)]] method is called to
    * assign an event timestamp.
    *
    * For each event in the data stream, the [[WatermarkGenerator#onEvent(Object, long, WatermarkOutput)]] will be
    * called.
    *
    * Periodically (defined by the [[ExecutionConfig#getAutoWatermarkInterval()]]), the
    * [[WatermarkGenerator#onPeriodicEmit(WatermarkOutput)]] method will be called.
    *
    * Common watermark generation patterns can be found as static methods in the
    * [[org.apache.flink.api.common.eventtime.WatermarkStrategy]] class.
    */
  def assignTimestampsAndWatermarks(watermarkStrategy: WatermarkStrategy[T]): DataStream[T] = {
    val cleanedStrategy = clean(watermarkStrategy)

    asScalaStream(stream.assignTimestampsAndWatermarks(cleanedStrategy))
  }

  /** Assigns timestamps to the elements in the data stream and periodically creates watermarks to signal event time
    * progress.
    *
    * This method is a shortcut for data streams where the element timestamp are known to be monotonously ascending
    * within each parallel stream. In that case, the system can generate watermarks automatically and perfectly by
    * tracking the ascending timestamps.
    *
    * For cases where the timestamps are not monotonously increasing, use the more general methods
    * [[assignTimestampsAndWatermarks(AssignerWithPeriodicWatermarks)]] and
    * [[assignTimestampsAndWatermarks(AssignerWithPunctuatedWatermarks)]].
    *
    * @deprecated
    *   Please use {@link #assignTimestampsAndWatermarks(WatermarkStrategy)} instead.
    */
  @Deprecated
  def assignAscendingTimestamps(extractor: T => Long): DataStream[T] = {
    val cleanExtractor    = clean(extractor)
    val extractorFunction = new AscendingTimestampExtractor[T] {
      def extractAscendingTimestamp(element: T): Long = {
        cleanExtractor(element)
      }
    }
    asScalaStream(stream.assignTimestampsAndWatermarks(extractorFunction))
  }

  /** Creates a co-group operation. See [[CoGroupedStreams]] for an example of how the keys and window can be specified.
    */
  def coGroup[T2](otherStream: DataStream[T2]): CoGroupedStreams[T, T2] = {
    new CoGroupedStreams(this, otherStream)
  }

  /** Creates a join operation. See [[JoinedStreams]] for an example of how the keys and window can be specified.
    */
  def join[T2](otherStream: DataStream[T2]): JoinedStreams[T, T2] = {
    new JoinedStreams(this, otherStream)
  }

  /** Writes a DataStream to the standard output stream (stdout). For each element of the DataStream the result of
    * .toString is written.
    */
  @PublicEvolving
  def print(): DataStreamSink[T] = stream.print()

  /** Writes a DataStream to the standard error stream (stderr).
    *
    * For each element of the DataStream the result of [[AnyRef.toString()]] is written.
    *
    * @return
    *   The closed DataStream.
    */
  @PublicEvolving
  def printToErr(): DataStreamSink[T] = stream.printToErr()

  /** Writes a DataStream to the standard output stream (stdout). For each element of the DataStream the result of
    * [[AnyRef.toString()]] is written.
    *
    * @param sinkIdentifier
    *   The string to prefix the output with.
    * @return
    *   The closed DataStream.
    */
  @PublicEvolving
  def print(sinkIdentifier: String): DataStreamSink[T] = stream.print(sinkIdentifier)

  /** Writes a DataStream to the standard error stream (stderr).
    *
    * For each element of the DataStream the result of [[AnyRef.toString()]] is written.
    *
    * @param sinkIdentifier
    *   The string to prefix the output with.
    * @return
    *   The closed DataStream.
    */
  @PublicEvolving
  def printToErr(sinkIdentifier: String): DataStreamSink[T] = stream.printToErr(sinkIdentifier)

  /** Writes a DataStream using the given [[OutputFormat]].
    *
    * @deprecated
    *   Please use the {@link org.apache.flink.streaming.api.functions.sink.filesystem.StreamingFileSink} explicitly
    *   using the {@link #addSink(SinkFunction)} method.
    */
  @Deprecated
  def writeUsingOutputFormat(format: OutputFormat[T]): DataStreamSink[T] = {
    stream.writeUsingOutputFormat(format)
  }

  /** Writes the DataStream to a socket as a byte array. The format of the output is specified by a
    * [[SerializationSchema]].
    */
  @PublicEvolving
  def writeToSocket(hostname: String, port: Integer, schema: SerializationSchema[T]): DataStreamSink[T] = {
    stream.writeToSocket(hostname, port, schema)
  }

  // /** Adds the given sink to this DataStream. Only streams with sinks added will be executed once the
  //   * StreamExecutionEnvironment.execute(...) method is called.
  //   */
  // def addSink(sinkFunction: SinkFunction[T]): DataStreamSink[T] =
  //   stream.addSink(sinkFunction)

  // /** Adds the given sink to this DataStream. Only streams with sinks added will be executed once the
  //   * StreamExecutionEnvironment.execute(...) method is called.
  //   */
  // def addSink(fun: T => Unit): DataStreamSink[T] = {
  //   if (fun == null) {
  //     throw new NullPointerException("Sink function must not be null.")
  //   }
  //   val cleanFun = clean(fun)
  //   val sinkFunction = new SinkFunction[T] {
  //     override def invoke(in: T) = cleanFun(in)
  //   }
  //   this.addSink(sinkFunction)
  // }

  /** Adds the given sink to this DataStream. Only streams with sinks added will be executed once the
    * StreamExecutionEnvironment.execute(...) method is called.
    */
  def sinkTo(sink: org.apache.flink.api.connector.sink2.Sink[T]): DataStreamSink[T] =
    stream.sinkTo(sink)

  /** Triggers the distributed execution of the streaming dataflow and returns an iterator over the elements of the
    * given DataStream.
    *
    * <p>The DataStream application is executed in the regular distributed manner on the target environment, and the
    * events from the stream are polled back to this application process and thread through Flink's REST API.
    *
    * <p><b>IMPORTANT</b> The returned iterator must be closed to free all cluster resources.
    */
  def executeAndCollect(): CloseableIterator[T] =
    CloseableIterator.fromJava(stream.executeAndCollect())

  /** Triggers the distributed execution of the streaming dataflow and returns an iterator over the elements of the
    * given DataStream.
    *
    * <p>The DataStream application is executed in the regular distributed manner on the target environment, and the
    * events from the stream are polled back to this application process and thread through Flink's REST API.
    *
    * <p><b>IMPORTANT</b> The returned iterator must be closed to free all cluster resources.
    */
  def executeAndCollect(jobExecutionName: String): CloseableIterator[T] =
    CloseableIterator.fromJava(stream.executeAndCollect(jobExecutionName))

  /** Triggers the distributed execution of the streaming dataflow and returns an iterator over the elements of the
    * given DataStream.
    *
    * <p>The DataStream application is executed in the regular distributed manner on the target environment, and the
    * events from the stream are polled back to this application process and thread through Flink's REST API.
    */
  def executeAndCollect(limit: Int): List[T] =
    stream.executeAndCollect(limit).asScala.toList

  /** Triggers the distributed execution of the streaming dataflow and returns an iterator over the elements of the
    * given DataStream.
    *
    * <p>The DataStream application is executed in the regular distributed manner on the target environment, and the
    * events from the stream are polled back to this application process and thread through Flink's REST API.
    */
  def executeAndCollect(jobExecutionName: String, limit: Int): List[T] =
    stream.executeAndCollect(jobExecutionName, limit).asScala.toList

  /** Returns a "closure-cleaned" version of the given function. Cleans only if closure cleaning is not disabled in the
    * [[org.apache.flink.api.common.ExecutionConfig]].
    */
  private[flinkx] def clean[F <: AnyRef](f: F): F = {
    new StreamExecutionEnvironment(stream.getExecutionEnvironment).scalaClean(f)
  }

  /** Transforms the [[DataStream]] by using a custom [[OneInputStreamOperator]].
    *
    * @param operatorName
    *   name of the operator, for logging purposes
    * @param operator
    *   the object containing the transformation logic
    * @tparam R
    *   the type of elements emitted by the operator
    */
  @PublicEvolving
  def transform[R: TypeInformation](operatorName: String, operator: OneInputStreamOperator[T, R]): DataStream[R] = {
    asScalaStream(stream.transform(operatorName, implicitly[TypeInformation[R]], operator))
  }

  /** Sets the description of this data stream.
    *
    * <p>Description is used in json plan and web ui, but not in logging and metrics where only name is available.
    * Description is expected to provide detailed information about this operation, while name is expected to be more
    * simple, providing summary information only, so that we can have more user-friendly logging messages and metric
    * tags without losing useful messages for debugging.
    *
    * @return
    *   The operator with new description
    */
  @PublicEvolving
  def setDescription(description: String): DataStream[T] = stream match {
    case stream: SingleOutputStreamOperator[T] => asScalaStream(stream.setDescription(description))
    case _                                     =>
      throw new UnsupportedOperationException("Only supported for operators.")
      this
  }
}
